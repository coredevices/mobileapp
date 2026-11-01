package io.rebble.libpebblecommon.plugin

import io.rebble.libpebblecommon.js.JsEngineInterface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * A host API whose calls resolve JS promises: JS calls `<name>.request(id, json)` (see
 * [promiseBridgeJs]) and the result is evaluated back in as `<replyFunction>(id, payload)`.
 */
internal abstract class PromiseJsBridge(
    scope: CoroutineScope,
    private val eval: (String) -> Unit,
    override val name: String,
    private val replyFunction: String,
    private val failureMessage: String,
) : JsEngineInterface {
    override val methods = listOf("request")

    private val json = Json { ignoreUnknownKeys = true }
    private val bridgeJob = SupervisorJob(scope.coroutineContext[Job])
    private val bridgeScope = CoroutineScope(scope.coroutineContext + bridgeJob)

    protected abstract suspend fun handle(request: JsonObject): JsonElement

    override fun dispatch(method: String, args: List<Any?>): Any? {
        if (method != "request") return null
        val id = (args.getOrNull(0) as? Number)?.toInt() ?: return null
        val requestJson = args.getOrNull(1) as? String ?: return null
        bridgeScope.launch {
            val response = try {
                buildJsonObject { put("result", handle(parse(requestJson))) }
            } catch (error: Exception) {
                buildJsonObject {
                    put("error", buildJsonObject {
                        put("code", errorCode(error))
                        put("message", error.message ?: failureMessage)
                    })
                }
            }
            eval("globalThis.$replyFunction($id,$response)")
        }
        return null
    }

    private fun parse(raw: String): JsonObject = try {
        json.parseToJsonElement(raw).jsonObject
    } catch (error: SerializationException) {
        throw IllegalArgumentException("malformed request", error)
    }

    protected fun JsonObject.string(name: String) =
        get(name)?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }

    private fun errorCode(error: Exception) = when (error) {
        is PluginNativeException -> error.code
        is IllegalArgumentException, is SerializationException -> PluginErrors.INVALID_ARGS
        else -> PluginErrors.UNKNOWN
    }

    fun close() {
        bridgeScope.cancel()
    }
}

/** JS for inside a `function (global)` wrapper: defines `call(operation, args)`, returning a promise. */
internal fun promiseBridgeJs(bridgeName: String, replyFunction: String, failureMessage: String) = """
  var nextId = 1;
  var pending = Object.create(null);
  global.$replyFunction = function (id, payload) {
    var callbacks = pending[id];
    if (!callbacks) return;
    delete pending[id];
    if (payload && payload.error) {
      var error = new Error(payload.error.message || '$failureMessage');
      error.code = payload.error.code;
      callbacks.reject(error);
    } else {
      callbacks.resolve(payload ? payload.result : undefined);
    }
  };
  function call(operation, args) {
    return new Promise(function (resolve, reject) {
      var id = nextId++;
      pending[id] = { resolve: resolve, reject: reject };
      $bridgeName.request(id, JSON.stringify(Object.assign({ operation: operation }, args)));
    });
  }
"""
