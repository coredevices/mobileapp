package coredevices.pebble.config.bridge

import io.ktor.http.decodeURLPart
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/**
 * Parses the URL hash and returns the decoded settings as a plain string map.
 *
 * Config pages currently encode settings in the URL hash (e.g.
 * `https://.../config.html#%7B%22token%22%3A%22...%22%7D`). The bridge exposes
 * these through `window.pebbleBridge.config` so the page no longer has to read
 * the URL itself.
 */
fun parseBridgeConfigFromUrlHash(url: String): Map<String, String> {
    val fragment = url.substringAfter('#', missingDelimiterValue = "")
    if (fragment.isBlank()) return emptyMap()

    return try {
        val decoded = fragment.decodeURLPart()
        val json = bridgeJson.parseToJsonElement(decoded)
        when (json) {
            is JsonObject -> json.entries.associate { (key, element) ->
                key to when (element) {
                    is JsonPrimitive -> if (element.isString) element.content else element.toString()
                    else -> element.toString()
                }
            }
            else -> emptyMap()
        }
    } catch (_: Exception) {
        emptyMap()
    }
}
