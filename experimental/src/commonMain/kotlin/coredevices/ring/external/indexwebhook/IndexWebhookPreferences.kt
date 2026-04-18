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
 * Stores webhook configuration: URL, auth token, and payload mode.
 */
class IndexWebhookPreferences(private val settings: Settings) {

    companion object {
        private const val URL_KEY = "index_webhook_url"
        private const val TOKEN_KEY = "index_webhook_auth_token"
        private const val PAYLOAD_MODE_KEY = "index_webhook_payload_mode"
    }

    private val _webhookUrl = MutableStateFlow(settings.getStringOrNull(URL_KEY))
    val webhookUrl = _webhookUrl.asStateFlow()

    private val _authToken = MutableStateFlow(settings.getStringOrNull(TOKEN_KEY))
    val authToken = _authToken.asStateFlow()

    private val _payloadMode = MutableStateFlow(
        IndexWebhookPayloadMode.fromId(settings.getInt(PAYLOAD_MODE_KEY, IndexWebhookPayloadMode.RecordingOnly.id))
    )
    val payloadMode = _payloadMode.asStateFlow()

    /** True when both URL and auth token are configured (non-null, non-blank). */
    val isLinked: Boolean
        get() = !_webhookUrl.value.isNullOrBlank() && !_authToken.value.isNullOrBlank()

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

    fun clearAll() {
        settings.remove(URL_KEY)
        settings.remove(TOKEN_KEY)
        settings.remove(PAYLOAD_MODE_KEY)
        _webhookUrl.value = null
        _authToken.value = null
        _payloadMode.value = IndexWebhookPayloadMode.RecordingOnly
    }
}
