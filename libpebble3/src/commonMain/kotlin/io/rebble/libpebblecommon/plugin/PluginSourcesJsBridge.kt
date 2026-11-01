package io.rebble.libpebblecommon.plugin

import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement

/** `Pebble.readSource(request)`: another plugin's source, as a promise of its envelope. */
internal class PluginSourcesJsBridge(
    scope: CoroutineScope,
    eval: (String) -> Unit,
    private val read: suspend (SourceRequest) -> SourceUpdate,
) : PromiseJsBridge(scope, eval, NAME, REPLY_FUNCTION, FAILURE_MESSAGE) {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun handle(request: JsonObject): JsonElement =
        when (val update = read(json.decodeFromJsonElement(SourceRequest.serializer(), request))) {
            is SourceUpdate.Data -> Json.encodeToJsonElement(update.envelope)
            is SourceUpdate.Error -> throw PluginNativeException(update.code, update.message)
        }

    companion object {
        private const val NAME = "_PluginSourcesBridge"
        private const val REPLY_FUNCTION = "__pluginSourcesReply"
        private const val FAILURE_MESSAGE = "reading the source failed"

        val INSTALL_JS = """
(function (global) {
${promiseBridgeJs(NAME, REPLY_FUNCTION, FAILURE_MESSAGE)}
  Pebble.readSource = function (request) { return call('read', request || {}); };
  Pebble.subscribeSource = function (request) { _JsBridge.subscribeSource(JSON.stringify(request || {})); };
})(globalThis);
"""
    }
}
