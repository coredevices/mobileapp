package io.rebble.libpebblecommon.plugin

import co.touchlab.kermit.Logger
import io.rebble.libpebblecommon.js.JsRunner
import io.rebble.libpebblecommon.permissions.AppPermissions
import io.rebble.libpebblecommon.permissions.invokeActionKey
import io.rebble.libpebblecommon.permissions.refusalMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.uuid.Uuid

/**
 * Per-PKJS-session dispatcher for `Pebble.invokeAction`. One-shot rather than flow-based:
 * each call resolves exactly once and nothing is cached. Sits alongside [SourceDispatcher],
 * which owns the long-lived subscription side.
 */
class ActionDispatcher(
    private val registry: PluginRegistry,
    private val jsRunner: JsRunner,
    private val scope: CoroutineScope,
    private val permissions: AppPermissions,
) {
    private val logger = Logger.withTag("ActionDispatcher")

    fun invoke(callId: Int, requestJson: String) {
        val request = try {
            Json.decodeFromString<InvokeRequest>(requestJson)
        } catch (e: SerializationException) {
            logger.w(e) { "invokeAction: malformed request" }
            scope.launch {
                respond(
                    callId,
                    ActionResult.error(PluginErrors.INVALID_REQUEST, e.message ?: "bad json"),
                )
            }
            return
        }
        scope.launch {
            val result = run(request)
            // Args can carry user data, so only their names.
            logger.i {
                "${jsRunner.appInfo.shortName} (${jsRunner.appInfo.uuid}) invoked ${request.plugin}/${request.action}" +
                    " with ${request.args?.keys.orEmpty()}: ${if (result.ok) "ok" else "${result.code} ${result.message}"}"
            }
            respond(callId, result)
        }
    }

    private suspend fun run(request: InvokeRequest): ActionResult {
        val plugin = registry.findPlugin(request.plugin)
            ?: return ActionResult.error(
                PluginErrors.PLUGIN_UNAVAILABLE,
                "No plugin ${request.plugin}",
            )
        val declaration = plugin.action(request.action)
            ?: return ActionResult.error(
                PluginErrors.PLUGIN_UNAVAILABLE,
                "${plugin.name} has no action ${request.action}",
            )
        val subject = permissions.subjectOf(Uuid.parse(jsRunner.appInfo.uuid))
            ?: return ActionResult.error(PluginErrors.PERMISSION_DENIED, "${jsRunner.appInfo.shortName} isn't installed")
        val required = requiredOfCaller(declaration).toMutableSet()
        // The args can carry the caller's data, which the plugin could send off the phone.
        if (plugin.pluginUuid != subject.appUuid && permissions.canExfiltrate(plugin.pluginUuid)) {
            required += invokeActionKey(plugin.pluginUuid)
        }
        val refused = permissions.requestAll(subject, required, wait = true)
        if (refused.isNotEmpty()) {
            return ActionResult.error(
                PluginErrors.PERMISSION_DENIED,
                refusalMessage(jsRunner.appInfo.shortName, refused),
            )
        }
        val args = request.args ?: JsonObject(emptyMap())
        val missing = declaration.requiredParams.filterNot { args.containsKey(it) }
        if (missing.isNotEmpty()) {
            return ActionResult.error(
                PluginErrors.INVALID_ARGS,
                "missing required: ${missing.joinToString()}",
            )
        }
        val timeout = request.timeoutMs?.coerceIn(1, MAX_TIMEOUT_MS) ?: DEFAULT_TIMEOUT_MS
        return withTimeoutOrNull(timeout) {
            try {
                plugin.invoke(request.action, args)
            } catch (e: Exception) {
                logger.w(e) { "action ${request.action} threw" }
                ActionResult.error(PluginErrors.UNKNOWN, e.message ?: "error")
            }
        } ?: ActionResult.error(PluginErrors.TIMEOUT, "no response in ${timeout}ms")
    }

    private suspend fun respond(callId: Int, result: ActionResult) {
        val payload = Json.encodeToString(ActionResult.serializer(), result)
        val quoted = Json.encodeToString(String.serializer(), payload)
        jsRunner.eval("globalThis._signalActionResult($callId, $quoted)")
    }

    @Serializable
    private data class InvokeRequest(
        val plugin: String,
        val action: String,
        val args: JsonObject? = null,
        val timeoutMs: Long? = null,
    )

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 15_000L
        const val MAX_TIMEOUT_MS = 60_000L
    }
}
