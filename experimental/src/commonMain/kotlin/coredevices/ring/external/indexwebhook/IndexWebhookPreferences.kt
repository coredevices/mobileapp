package coredevices.ring.external.indexwebhook

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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
 * Recording button gesture a webhook config is bound to. Each gesture has its own
 * independent webhook configuration.
 */
enum class IndexWebhookGesture(val keySuffix: String) {
    SingleClickHold("single_click_hold"),
    DoubleClickHold("double_click_hold"),
}

/**
 * One gesture's webhook configuration.
 */
data class IndexWebhookConfig(
    val url: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val payloadMode: IndexWebhookPayloadMode = IndexWebhookPayloadMode.RecordingOnly,
) {
    val isConfigured: Boolean get() = !url.isNullOrBlank()
}

/**
 * Stores one webhook configuration (URL, user-settable request headers, payload mode)
 * per recording button gesture.
 */
class IndexWebhookPreferences(private val settings: Settings) {

    companion object {
        // Flat keys from before configs were per-gesture; migrated in init.
        private const val LEGACY_URL_KEY = "index_webhook_url"
        private const val LEGACY_TOKEN_KEY = "index_webhook_auth_token"
        private const val LEGACY_HEADERS_KEY = "index_webhook_headers"
        private const val LEGACY_PAYLOAD_MODE_KEY = "index_webhook_payload_mode"
        private const val LEGACY_TRIGGER_KEY = "index_webhook_trigger"

        // Ids of the removed IndexWebhookTrigger enum, still read during migration.
        private const val LEGACY_TRIGGER_SINGLE_CLICK = 0
        private const val LEGACY_TRIGGER_BOTH = 2

        // Header name the auth token used to be hardcoded to before headers were user-settable.
        private const val LEGACY_TOKEN_HEADER = "X-Widget-Token"

        private val json = Json
        private val headersSerializer = MapSerializer(String.serializer(), String.serializer())

        private fun urlKey(gesture: IndexWebhookGesture) = "index_webhook_url_${gesture.keySuffix}"
        private fun headersKey(gesture: IndexWebhookGesture) = "index_webhook_headers_${gesture.keySuffix}"
        private fun payloadModeKey(gesture: IndexWebhookGesture) =
            "index_webhook_payload_mode_${gesture.keySuffix}"
    }

    // Migrations must run before the per-gesture configs are loaded below, and the legacy
    // token must land in the flat headers before those are split out per-gesture.
    init {
        migrateLegacyToken()
        migrateFlatConfig()
    }

    private val configs = IndexWebhookGesture.entries.associateWith { gesture ->
        MutableStateFlow(loadConfig(gesture))
    }
    private val readOnlyConfigs = configs.mapValues { it.value.asStateFlow() }

    fun config(gesture: IndexWebhookGesture): StateFlow<IndexWebhookConfig> =
        readOnlyConfigs.getValue(gesture)

    private fun loadConfig(gesture: IndexWebhookGesture) = IndexWebhookConfig(
        url = settings.getStringOrNull(urlKey(gesture)),
        headers = loadHeaders(headersKey(gesture)),
        payloadMode = IndexWebhookPayloadMode.fromId(
            settings.getInt(payloadModeKey(gesture), IndexWebhookPayloadMode.RecordingOnly.id)
        ),
    )

    private fun loadHeaders(key: String): Map<String, String> {
        val raw = settings.getStringOrNull(key) ?: return emptyMap()
        return try {
            json.decodeFromString(headersSerializer, raw)
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun migrateLegacyToken() {
        val legacyToken = settings.getStringOrNull(LEGACY_TOKEN_KEY) ?: return
        if (settings.getStringOrNull(LEGACY_HEADERS_KEY) == null && legacyToken.isNotBlank()) {
            settings.putString(
                LEGACY_HEADERS_KEY,
                json.encodeToString(headersSerializer, mapOf(LEGACY_TOKEN_HEADER to legacyToken))
            )
        }
        settings.remove(LEGACY_TOKEN_KEY)
    }

    private fun migrateFlatConfig() {
        val hasFlatConfig = settings.hasKey(LEGACY_URL_KEY) || settings.hasKey(LEGACY_HEADERS_KEY) ||
            settings.hasKey(LEGACY_PAYLOAD_MODE_KEY) || settings.hasKey(LEGACY_TRIGGER_KEY)
        if (!hasFlatConfig) return

        val url = settings.getStringOrNull(LEGACY_URL_KEY)
        val headers = settings.getStringOrNull(LEGACY_HEADERS_KEY)
        val payloadMode = settings.getIntOrNull(LEGACY_PAYLOAD_MODE_KEY)
        val gestures = when (settings.getIntOrNull(LEGACY_TRIGGER_KEY)) {
            LEGACY_TRIGGER_SINGLE_CLICK -> listOf(IndexWebhookGesture.SingleClickHold)
            LEGACY_TRIGGER_BOTH -> IndexWebhookGesture.entries
            else -> listOf(IndexWebhookGesture.DoubleClickHold) // the old default trigger
        }
        gestures.forEach { gesture ->
            url?.let { settings.putString(urlKey(gesture), it) }
            headers?.let { settings.putString(headersKey(gesture), it) }
            payloadMode?.let { settings.putInt(payloadModeKey(gesture), it) }
        }

        settings.remove(LEGACY_URL_KEY)
        settings.remove(LEGACY_HEADERS_KEY)
        settings.remove(LEGACY_PAYLOAD_MODE_KEY)
        settings.remove(LEGACY_TRIGGER_KEY)
    }

    fun setWebhookUrl(gesture: IndexWebhookGesture, url: String?) {
        if (url != null) {
            settings.putString(urlKey(gesture), url)
        } else {
            settings.remove(urlKey(gesture))
        }
        configs.getValue(gesture).update { it.copy(url = url) }
    }

    fun setHeaders(gesture: IndexWebhookGesture, headers: Map<String, String>) {
        if (headers.isEmpty()) {
            settings.remove(headersKey(gesture))
        } else {
            settings.putString(headersKey(gesture), json.encodeToString(headersSerializer, headers))
        }
        configs.getValue(gesture).update { it.copy(headers = headers) }
    }

    fun setPayloadMode(gesture: IndexWebhookGesture, mode: IndexWebhookPayloadMode) {
        settings.putInt(payloadModeKey(gesture), mode.id)
        configs.getValue(gesture).update { it.copy(payloadMode = mode) }
    }

    fun clear(gesture: IndexWebhookGesture) {
        settings.remove(urlKey(gesture))
        settings.remove(headersKey(gesture))
        settings.remove(payloadModeKey(gesture))
        configs.getValue(gesture).value = IndexWebhookConfig()
    }
}
