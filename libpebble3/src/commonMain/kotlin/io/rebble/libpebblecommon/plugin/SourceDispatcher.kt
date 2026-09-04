package io.rebble.libpebblecommon.plugin

import co.touchlab.kermit.Logger
import io.rebble.libpebblecommon.js.JsRunner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Per-PKJS-session dispatcher for source subscriptions. Created alongside each
 * [io.rebble.libpebblecommon.js.PrivatePKJSInterface]; owns the lifecycle of every active
 * subscription on the consuming session and tears them down when the session ends.
 *
 * Subscription IDs are minted by the JS side (counter in startup.js); the dispatcher just
 * stores them. This matches the existing transactionId / callbackId conventions on other
 * Pebble APIs.
 */
class SourceDispatcher(
    private val registry: PluginRegistry,
    private val jsRunner: JsRunner,
    private val scope: CoroutineScope,
) {
    private val logger = Logger.withTag("SourceDispatcher")
    private val subscriptions = mutableMapOf<Int, Job>()

    fun subscribe(subscriptionId: Int, requestJson: String) {
        if (subscriptions.containsKey(subscriptionId)) {
            logger.w { "subscribe: id $subscriptionId already active — ignoring" }
            return
        }
        val request = try {
            Json.decodeFromString<SubscribeRequest>(requestJson)
        } catch (e: SerializationException) {
            logger.w(e) { "subscribe: malformed request" }
            scope.launch {
                emitError(subscriptionId, PluginErrors.INVALID_REQUEST, e.message ?: "bad json")
            }
            return
        }

        val plugin = registry.findSourcePlugin(request.category, request.item, request.plugin)
        if (plugin == null) {
            scope.launch {
                emitError(
                    subscriptionId,
                    PluginErrors.PLUGIN_UNAVAILABLE,
                    "No plugin for ${request.category}/${request.item}",
                )
            }
            return
        }

        val job = plugin
            .observe(request.category, request.item, request.properties, request.iconPixelSize)
            .onEach { envelope ->
                val payload = Json.encodeToString(SourceEnvelope.serializer(), envelope)
                // Quote the JSON so it becomes a JS string literal, then JSON.parse it
                // back inside the JS shim. Same pattern as the existing app-message ACK path.
                val quoted = Json.encodeToString(String.serializer(), payload)
                jsRunner.eval("globalThis._signalSourceUpdate($subscriptionId, $quoted)")
            }
            .catch { t ->
                logger.w(t) { "subscription $subscriptionId failed" }
                emitError(
                    subscriptionId,
                    PluginErrors.UNKNOWN,
                    t.message ?: t::class.simpleName ?: "error",
                )
            }
            .launchIn(scope)
        subscriptions[subscriptionId] = job
    }

    fun unsubscribe(subscriptionId: Int) {
        subscriptions.remove(subscriptionId)?.cancel()
    }

    private suspend fun emitError(subscriptionId: Int, code: String, message: String) {
        val payload = Json.encodeToString(
            SourceErrorPayload.serializer(),
            SourceErrorPayload(code, message),
        )
        val quoted = Json.encodeToString(String.serializer(), payload)
        jsRunner.eval("globalThis._signalSourceError($subscriptionId, $quoted)")
    }

    @Serializable
    private data class SubscribeRequest(
        val category: String,
        val item: String,
        val properties: List<String>? = null,
        val plugin: String? = null,
        val maxStalenessMs: Long? = null,
        val iconPixelSize: IconPixelSize? = null,
    )

    @Serializable
    private data class SourceErrorPayload(
        val code: String,
        val message: String,
    )
}
