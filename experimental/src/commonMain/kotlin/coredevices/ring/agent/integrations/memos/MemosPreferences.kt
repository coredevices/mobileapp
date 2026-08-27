package coredevices.ring.agent.integrations.memos

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Persists the Memos server to post notes to. The access token is a secret and lives in
 * `IntegrationTokenStorage` instead.
 */
class MemosPreferences(private val settings: Settings) {

    companion object {
        private const val BASE_URL_KEY = "memos_base_url"
    }

    private val _baseUrl = MutableStateFlow(settings.getStringOrNull(BASE_URL_KEY))
    val baseUrl = _baseUrl.asStateFlow()

    fun setBaseUrl(url: String) {
        val normalized = url.trim().trimEnd('/').ifBlank { null }
        if (normalized == null) settings.remove(BASE_URL_KEY) else settings.putString(BASE_URL_KEY, normalized)
        _baseUrl.value = normalized
    }

    fun clear() {
        settings.remove(BASE_URL_KEY)
        _baseUrl.value = null
    }
}
