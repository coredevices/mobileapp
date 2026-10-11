package io.rebble.libpebblecommon.plugin

import kotlinx.serialization.json.JsonObject

/**
 * The host half of hosted OAuth for plugins: a plugin calls `Pebble.oauth.authorize(slug)`, the
 * host runs the App Store broker flow, and it gets back an access token plus an opaque
 * `broker_refresh` it hands to [refresh].
 */
interface PluginOAuthApi {
    suspend fun authorize(pluginUuid: String, connectorSlug: String): JsonObject
    suspend fun refresh(pluginUuid: String, connectorSlug: String, brokerRefresh: String): JsonObject
}
