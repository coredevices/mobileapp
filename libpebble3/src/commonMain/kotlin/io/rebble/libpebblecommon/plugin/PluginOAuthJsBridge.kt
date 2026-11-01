package io.rebble.libpebblecommon.plugin

import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Injects `Pebble.oauth.{authorize, refresh}` into a plugin session and routes those calls to the
 * host's [PluginOAuthApi]. Present only when the plugin declares an `oauth` connector and the host
 * has an OAuth API wired.
 *
 * The connector slug is verified against the manifest here, so a plugin can only reach a connector
 * it declared.
 */
internal class PluginOAuthJsBridge(
    scope: CoroutineScope,
    eval: (String) -> Unit,
    private val pluginUuid: String,
    private val declaredSlugs: Set<String>,
    private val api: PluginOAuthApi,
) : PromiseJsBridge(scope, eval, NAME, REPLY_FUNCTION, FAILURE_MESSAGE) {

    override suspend fun handle(request: JsonObject): JsonElement {
        val operation = request.string("operation")
            ?: throw IllegalArgumentException("operation is required")
        val slug = request.string("connectorSlug")
            ?: throw IllegalArgumentException("connectorSlug is required")
        if (slug !in declaredSlugs) {
            throw PluginNativeException(
                PluginErrors.PERMISSION_DENIED,
                "this plugin did not declare an OAuth connector '$slug'",
            )
        }
        return when (operation) {
            "authorize" -> api.authorize(pluginUuid, slug)
            "refresh" -> {
                val brokerRefresh = request.string("brokerRefresh")
                    ?: throw IllegalArgumentException("brokerRefresh is required")
                api.refresh(pluginUuid, slug, brokerRefresh)
            }
            else -> throw IllegalArgumentException("unknown OAuth operation '$operation'")
        }
    }

    companion object {
        private const val NAME = "_PluginOAuthBridge"
        private const val REPLY_FUNCTION = "__pluginOAuthReply"
        private const val FAILURE_MESSAGE = "OAuth operation failed"

        val INSTALL_JS = """
(function (global) {
${promiseBridgeJs(NAME, REPLY_FUNCTION, FAILURE_MESSAGE)}
  Pebble.oauth = Object.freeze({
    authorize: function (connectorSlug) { return call('authorize', { connectorSlug: connectorSlug }); },
    refresh: function (connectorSlug, brokerRefresh) {
      return call('refresh', { connectorSlug: connectorSlug, brokerRefresh: brokerRefresh });
    }
  });
})(globalThis);
"""
    }
}
