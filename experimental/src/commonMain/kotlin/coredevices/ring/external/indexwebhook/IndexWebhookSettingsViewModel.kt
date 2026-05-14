package coredevices.ring.external.indexwebhook

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class IndexWebhookSettingsViewModel(
    private val webhookPreferences: IndexWebhookPreferences
) : ViewModel() {

    private val _webhookUrl = MutableStateFlow<String?>(null)
    val webhookUrl = _webhookUrl.asStateFlow()

    private val _authToken = MutableStateFlow<String?>(null)
    val authToken = _authToken.asStateFlow()

    private val _dialogOpen = MutableStateFlow(false)
    val dialogOpen = _dialogOpen.asStateFlow()

    private val _urlInput = MutableStateFlow("")
    val urlInput = _urlInput.asStateFlow()

    private val _tokenInput = MutableStateFlow("")
    val tokenInput = _tokenInput.asStateFlow()

    private val _payloadModeInput = MutableStateFlow(IndexWebhookPayloadMode.RecordingOnly)
    val payloadModeInput = _payloadModeInput.asStateFlow()

    val isLinked: Boolean
        get() = !_webhookUrl.value.isNullOrBlank() && !_authToken.value.isNullOrBlank()

    init {
        viewModelScope.launch {
            webhookPreferences.webhookUrl.collectLatest { url ->
                _webhookUrl.value = url?.ifBlank { null }
            }
        }
        viewModelScope.launch {
            webhookPreferences.authToken.collectLatest { token ->
                _authToken.value = token?.ifBlank { null }
            }
        }
    }

    fun openDialog() {
        _urlInput.value = _webhookUrl.value ?: ""
        _tokenInput.value = _authToken.value ?: ""
        _payloadModeInput.value = webhookPreferences.payloadMode.value
        _dialogOpen.value = true
    }

    fun closeDialog() {
        _dialogOpen.value = false
    }

    fun updateUrlInput(url: String) {
        _urlInput.value = url
    }

    fun updateTokenInput(token: String) {
        _tokenInput.value = token
    }

    fun updatePayloadMode(mode: IndexWebhookPayloadMode) {
        _payloadModeInput.value = mode
    }

    fun save() {
        viewModelScope.launch {
            val url = _urlInput.value.ifBlank { null }?.trim()
            val token = _tokenInput.value.ifBlank { null }?.trim()
            webhookPreferences.setWebhookUrl(url)
            webhookPreferences.setAuthToken(token)
            webhookPreferences.setPayloadMode(_payloadModeInput.value)
            closeDialog()
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            webhookPreferences.clearAll()
            closeDialog()
        }
    }
}
