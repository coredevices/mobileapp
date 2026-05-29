package coredevices.ring.external.indexwebhook

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

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
 * Stores webhook configuration: URL, auth token, payload mode, and trigger.
 */
class IndexWebhookPreferences(private val settings: Settings) {

    companion object {
        private const val URL_KEY = "index_webhook_url"
        private const val TOKEN_KEY = "index_webhook_auth_token"
        private const val PAYLOAD_MODE_KEY = "index_webhook_payload_mode"
        private const val TRIGGER_KEY = "index_webhook_trigger"
    }

    private val _webhookUrl = MutableStateFlow(settings.getStringOrNull(URL_KEY))
    val webhookUrl = _webhookUrl.asStateFlow()

    private val _authToken = MutableStateFlow(settings.getStringOrNull(TOKEN_KEY))
    val authToken = _authToken.asStateFlow()

    private val _payloadMode = MutableStateFlow(
        IndexWebhookPayloadMode.fromId(settings.getInt(PAYLOAD_MODE_KEY, IndexWebhookPayloadMode.RecordingOnly.id))
    )
    val payloadMode = _payloadMode.asStateFlow()

    private val _trigger = MutableStateFlow(
        IndexWebhookTrigger.fromId(settings.getInt(TRIGGER_KEY, IndexWebhookTrigger.DoubleClickHold.id))
    )
    val trigger = _trigger.asStateFlow()

    fun setWebhookUrl(url: String?) {
        if (url != null) {
            settings.putString(URL_KEY, url)
        } else {
            settings.remove(URL_KEY)
        }
        _webhookUrl.value = url
    }

    fun setAuthToken(token: String?) {
        if (token != null) {
            settings.putString(TOKEN_KEY, token)
        } else {
            settings.remove(TOKEN_KEY)
        }
        _authToken.value = token
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
        settings.remove(TOKEN_KEY)
        settings.remove(PAYLOAD_MODE_KEY)
        settings.remove(TRIGGER_KEY)
        _webhookUrl.value = null
        _authToken.value = null
        _payloadMode.value = IndexWebhookPayloadMode.RecordingOnly
        _trigger.value = IndexWebhookTrigger.DoubleClickHold
    }
}
