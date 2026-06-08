package coredevices.ring.external.indexwebhook

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** A single editable webhook header row. */
data class WebhookHeaderInput(val name: String, val value: String)

class IndexWebhookSettingsViewModel(
    private val webhookPreferences: IndexWebhookPreferences
) : ViewModel() {

    private val _webhookUrl = MutableStateFlow<String?>(null)
    val webhookUrl = _webhookUrl.asStateFlow()

    private val _dialogOpen = MutableStateFlow(false)
    val dialogOpen = _dialogOpen.asStateFlow()

    private val _urlInput = MutableStateFlow("")
    val urlInput = _urlInput.asStateFlow()

    private val _headerInputs = MutableStateFlow<List<WebhookHeaderInput>>(emptyList())
    val headerInputs = _headerInputs.asStateFlow()

    private val _payloadModeInput = MutableStateFlow(IndexWebhookPayloadMode.RecordingOnly)
    val payloadModeInput = _payloadModeInput.asStateFlow()

    private val _triggerInput = MutableStateFlow(IndexWebhookTrigger.DoubleClickHold)
    val triggerInput = _triggerInput.asStateFlow()

    val isLinked: Boolean
        get() = !_webhookUrl.value.isNullOrBlank()

    init {
        viewModelScope.launch {
            webhookPreferences.webhookUrl.collectLatest { url ->
                _webhookUrl.value = url?.ifBlank { null }
            }
        }
    }

    fun openDialog() {
        _urlInput.value = _webhookUrl.value ?: ""
        // Show existing headers, or a single empty row to start from.
        _headerInputs.value = webhookPreferences.headers.value
            .map { WebhookHeaderInput(it.key, it.value) }
            .ifEmpty { listOf(WebhookHeaderInput("", "")) }
        _payloadModeInput.value = webhookPreferences.payloadMode.value
        _triggerInput.value = webhookPreferences.trigger.value
        _dialogOpen.value = true
    }

    fun closeDialog() {
        _dialogOpen.value = false
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

    fun updateTrigger(trigger: IndexWebhookTrigger) {
        _triggerInput.value = trigger
    }

    fun save() {
        viewModelScope.launch {
            val url = _urlInput.value.ifBlank { null }?.trim()
            // Drop rows with a blank name; later rows win on duplicate names.
            val headers = _headerInputs.value
                .map { it.name.trim() to it.value.trim() }
                .filter { it.first.isNotEmpty() }
                .toMap()
            webhookPreferences.setWebhookUrl(url)
            webhookPreferences.setHeaders(headers)
            webhookPreferences.setPayloadMode(_payloadModeInput.value)
            webhookPreferences.setTrigger(_triggerInput.value)
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
