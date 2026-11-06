package io.rebble.libpebblecommon.plugin

import io.rebble.libpebblecommon.permissions.PermissionDecision
import io.rebble.libpebblecommon.permissions.queryState
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** `Pebble.permissions.query(key)`: the plugin's own permission, as `granted`, `denied` or `prompt`. */
internal class PluginPermissionsJsBridge(
    scope: CoroutineScope,
    eval: (String) -> Unit,
    private val query: suspend (String) -> PermissionDecision,
) : PromiseJsBridge(scope, eval, NAME, REPLY_FUNCTION, FAILURE_MESSAGE) {

    override suspend fun handle(request: JsonObject): JsonElement {
        val key = request.string("key") ?: throw IllegalArgumentException("a permission is required")
        return JsonPrimitive(query(key).queryState())
    }

    companion object {
        private const val NAME = "_PluginPermissionsBridge"
        private const val REPLY_FUNCTION = "__pluginPermissionsReply"
        private const val FAILURE_MESSAGE = "permission query failed"

        val INSTALL_JS = """
(function (global) {
${promiseBridgeJs(NAME, REPLY_FUNCTION, FAILURE_MESSAGE)}
  Pebble.permissions = Object.freeze({
    query: function (permission) { return call('query', { key: String(permission) }); }
  });
})(globalThis);
"""
    }
}
