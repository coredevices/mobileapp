package coredevices.pebble.config.bridge

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Internal JSON serializer used by the bridge. Lenient to tolerate config pages that
 * send numbers/strings loosely.
 */
val bridgeJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = false
}

@Serializable
data class FetchRequest(
    val url: String,
    val method: String = "GET",
    val headers: Map<String, String> = emptyMap(),
    val body: String? = null,
    val timeout: Long = 30_000,
)

@Serializable
data class FetchResponse(
    val ok: Boolean,
    val status: Int,
    val statusText: String,
    val headers: Map<String, String>,
    val body: String,
)
