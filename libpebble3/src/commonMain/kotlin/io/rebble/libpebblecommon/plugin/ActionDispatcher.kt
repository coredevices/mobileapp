package io.rebble.libpebblecommon.plugin

import co.touchlab.kermit.Logger
import io.rebble.libpebblecommon.js.JsRunner
import io.rebble.libpebblecommon.permissions.AppPermissions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.time.Duration.Companion.milliseconds
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
    private val client: PluginClient,
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
            respond(callId, run(request))
        }
    }

    private suspend fun run(request: InvokeRequest): ActionResult {
        val plugin = registry.findPlugin(request.plugin)
            ?: return ActionResult.error(
                PluginErrors.PLUGIN_UNAVAILABLE,
                "No plugin ${request.plugin}",
            )
        val subject = permissions.subjectOf(Uuid.parse(jsRunner.appInfo.uuid))
            ?: return ActionResult.error(PluginErrors.PERMISSION_DENIED, "${jsRunner.appInfo.shortName} isn't installed")
        return client.invoke(
            PluginCaller.App(subject, jsRunner.appInfo.shortName),
            plugin.metadata,
            request.action,
            request.args ?: JsonObject(emptyMap()),
            request.timeoutMs?.milliseconds?.coerceIn(1.milliseconds, JsPlugin.REQUEST_TIMEOUT),
        )
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
}
