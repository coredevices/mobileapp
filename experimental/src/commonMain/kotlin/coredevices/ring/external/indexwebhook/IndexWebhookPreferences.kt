package coredevices.ring.external.indexwebhook

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Payload mode controls what data is sent to the webhook endpoint.
 */
enum class IndexWebhookPayloadMode(val id: Int) {
    RecordingOnly(0),
    TranscriptionOnly(1),
    Both(2);

    companion object {
        fun fromId(id: Int): IndexWebhookPayloadMode =
            entries.firstOrNull { it.id == id } ?: RecordingOnly
    }
}

/**
 * Trigger controls which button gestures cause a webhook send.
 */
enum class IndexWebhookTrigger(val id: Int) {
    SingleClick(0),
    DoubleClickHold(1),
    Both(2);

    companion object {
        // Default to DoubleClickHold so users migrating from the old
        // SecondaryMode.IndexWebhook setup keep the same behavior.
        fun fromId(id: Int): IndexWebhookTrigger =
            entries.firstOrNull { it.id == id } ?: DoubleClickHold
    }
}

/**
 * Stores webhook configuration: URL, user-settable request headers, payload mode, and trigger.
 */
class IndexWebhookPreferences(private val settings: Settings) {

    companion object {
        private const val URL_KEY = "index_webhook_url"
        private const val TOKEN_KEY = "index_webhook_auth_token" // legacy, migrated into HEADERS_KEY
        private const val HEADERS_KEY = "index_webhook_headers"
        private const val PAYLOAD_MODE_KEY = "index_webhook_payload_mode"
        private const val TRIGGER_KEY = "index_webhook_trigger"

        // Header name the auth token used to be hardcoded to before headers were user-settable.
        private const val LEGACY_TOKEN_HEADER = "X-Widget-Token"

        private val json = Json
        private val headersSerializer = MapSerializer(String.serializer(), String.serializer())
    }

    // Migrate the old single auth token into the headers map before loading them below.
    init {
        migrateLegacyToken()
    }

    private val _webhookUrl = MutableStateFlow(settings.getStringOrNull(URL_KEY))
    val webhookUrl = _webhookUrl.asStateFlow()

    private val _headers = MutableStateFlow(loadHeaders())
    val headers = _headers.asStateFlow()

    private val _payloadMode = MutableStateFlow(
        IndexWebhookPayloadMode.fromId(settings.getInt(PAYLOAD_MODE_KEY, IndexWebhookPayloadMode.RecordingOnly.id))
    )
    val payloadMode = _payloadMode.asStateFlow()

    private val _trigger = MutableStateFlow(
        IndexWebhookTrigger.fromId(settings.getInt(TRIGGER_KEY, IndexWebhookTrigger.DoubleClickHold.id))
    )
    val trigger = _trigger.asStateFlow()

    private fun loadHeaders(): Map<String, String> {
        val raw = settings.getStringOrNull(HEADERS_KEY) ?: return emptyMap()
        return try {
            json.decodeFromString(headersSerializer, raw)
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun migrateLegacyToken() {
        val legacyToken = settings.getStringOrNull(TOKEN_KEY) ?: return
        if (settings.getStringOrNull(HEADERS_KEY) == null && legacyToken.isNotBlank()) {
            settings.putString(HEADERS_KEY, json.encodeToString(headersSerializer, mapOf(LEGACY_TOKEN_HEADER to legacyToken)))
        }
        settings.remove(TOKEN_KEY)
    }

    fun setWebhookUrl(url: String?) {
        if (url != null) {
            settings.putString(URL_KEY, url)
        } else {
            settings.remove(URL_KEY)
        }
        _webhookUrl.value = url
    }

    fun setHeaders(headers: Map<String, String>) {
        if (headers.isEmpty()) {
            settings.remove(HEADERS_KEY)
        } else {
            settings.putString(HEADERS_KEY, json.encodeToString(headersSerializer, headers))
        }
        _headers.value = headers
    }

    fun setPayloadMode(mode: IndexWebhookPayloadMode) {
        settings.putInt(PAYLOAD_MODE_KEY, mode.id)
        _payloadMode.value = mode
    }

    fun setTrigger(trigger: IndexWebhookTrigger) {
        settings.putInt(TRIGGER_KEY, trigger.id)
        _trigger.value = trigger
    }

    fun clearAll() {
        settings.remove(URL_KEY)
        settings.remove(HEADERS_KEY)
        settings.remove(PAYLOAD_MODE_KEY)
        settings.remove(TRIGGER_KEY)
        _webhookUrl.value = null
        _headers.value = emptyMap()
        _payloadMode.value = IndexWebhookPayloadMode.RecordingOnly
        _trigger.value = IndexWebhookTrigger.DoubleClickHold
    }
}
