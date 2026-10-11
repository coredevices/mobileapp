package io.rebble.libpebblecommon.plugin

import io.rebble.libpebblecommon.storage.SecureStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** `Pebble.secureStorage.{getItem, setItem, removeItem}`, over the plugin's own [SecureStorage]. */
internal class PluginSecureStorageJsBridge(
    scope: CoroutineScope,
    eval: (String) -> Unit,
    private val storage: SecureStorage,
) : PromiseJsBridge(scope, eval, NAME, REPLY_FUNCTION, FAILURE_MESSAGE) {

    override suspend fun handle(request: JsonObject): JsonElement {
        val operation = request.string("operation")
            ?: throw IllegalArgumentException("operation is required")
        val key = request.string("key") ?: throw IllegalArgumentException("key is required")
        return when (operation) {
            "getItem" -> JsonPrimitive(storage.get(key))
            "setItem" -> {
                val value = (request["value"] as? JsonPrimitive)?.takeIf { it.isString }?.content
                    ?: throw IllegalArgumentException("value is required")
                storage.set(key, value)
                JsonNull
            }
            "removeItem" -> {
                storage.remove(key)
                JsonNull
            }
            else -> throw IllegalArgumentException("unknown secure storage operation '$operation'")
        }
    }

    companion object {
        private const val NAME = "_PluginSecureStorageBridge"
        private const val REPLY_FUNCTION = "__pluginSecureStorageReply"
        private const val FAILURE_MESSAGE = "secure storage operation failed"

        val INSTALL_JS = """
(function (global) {
${promiseBridgeJs(NAME, REPLY_FUNCTION, FAILURE_MESSAGE)}
  Pebble.secureStorage = Object.freeze({
    getItem: function (key) { return call('getItem', { key: String(key) }); },
    setItem: function (key, value) { return call('setItem', { key: String(key), value: String(value) }); },
    removeItem: function (key) { return call('removeItem', { key: String(key) }); }
  });
})(globalThis);
"""
    }
}
