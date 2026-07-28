package coredevices.ring.external.indexwebhook

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** A single editable webhook header row. */
data class WebhookHeaderInput(val name: String, val value: String)

class IndexWebhookSettingsViewModel(
    private val webhookPreferences: IndexWebhookPreferences
) : ViewModel() {

    // The gesture whose config the dialog is editing; null while the dialog is closed.
    private val _editingGesture = MutableStateFlow<IndexWebhookGesture?>(null)
    val editingGesture = _editingGesture.asStateFlow()

    private val _urlInput = MutableStateFlow("")
    val urlInput = _urlInput.asStateFlow()

    private val _headerInputs = MutableStateFlow<List<WebhookHeaderInput>>(emptyList())
    val headerInputs = _headerInputs.asStateFlow()

    private val _payloadModeInput = MutableStateFlow(IndexWebhookPayloadMode.RecordingOnly)
    val payloadModeInput = _payloadModeInput.asStateFlow()

    fun config(gesture: IndexWebhookGesture): StateFlow<IndexWebhookConfig> =
        webhookPreferences.config(gesture)

    val isLinked: Boolean
        get() = _editingGesture.value
            ?.let { webhookPreferences.config(it).value.isConfigured } == true

    fun openDialog(gesture: IndexWebhookGesture) {
        val config = webhookPreferences.config(gesture).value
        _urlInput.value = config.url ?: ""
        // Show existing headers, or a single empty row to start from.
        _headerInputs.value = config.headers
            .map { WebhookHeaderInput(it.key, it.value) }
            .ifEmpty { listOf(WebhookHeaderInput("", "")) }
        _payloadModeInput.value = config.payloadMode
        _editingGesture.value = gesture
    }

    fun closeDialog() {
        _editingGesture.value = null
    }

    fun updateUrlInput(url: String) {
        _urlInput.value = url
    }

    fun addHeader() {
        _headerInputs.value = _headerInputs.value + WebhookHeaderInput("", "")
    }

    fun updateHeaderName(index: Int, name: String) {
        _headerInputs.value = _headerInputs.value.toMutableList().also {
            it[index] = it[index].copy(name = name)
        }
    }

    fun updateHeaderValue(index: Int, value: String) {
        _headerInputs.value = _headerInputs.value.toMutableList().also {
            it[index] = it[index].copy(value = value)
        }
    }

    fun removeHeader(index: Int) {
        _headerInputs.value = _headerInputs.value.filterIndexed { i, _ -> i != index }
    }

    fun updatePayloadMode(mode: IndexWebhookPayloadMode) {
        _payloadModeInput.value = mode
    }

    fun save() {
        val gesture = _editingGesture.value ?: return
        viewModelScope.launch {
            val url = _urlInput.value.ifBlank { null }?.trim()
            // Drop rows with a blank name; later rows win on duplicate names.
            val headers = _headerInputs.value
                .map { it.name.trim() to it.value.trim() }
                .filter { it.first.isNotEmpty() }
                .toMap()
            webhookPreferences.setWebhookUrl(gesture, url)
            webhookPreferences.setHeaders(gesture, headers)
            webhookPreferences.setPayloadMode(gesture, _payloadModeInput.value)
            closeDialog()
        }
    }

    fun clear() {
        val gesture = _editingGesture.value ?: return
        viewModelScope.launch {
            webhookPreferences.clear(gesture)
            closeDialog()
        }
    }
}
