package coredevices.ring.external.indexwebhook

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coredevices.ring.service.button.RingGesture
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A single editable webhook header row. */
data class WebhookHeaderInput(val name: String, val value: String)

sealed interface WebhookTestState {
    data object Idle : WebhookTestState
    data object Sending : WebhookTestState
    data class Done(val ok: Boolean, val label: String) : WebhookTestState
}

@OptIn(ExperimentalCoroutinesApi::class)
class IndexWebhookSettingsViewModel(
    private val webhookPreferences: IndexWebhookPreferences,
    private val webhookApi: IndexWebhookApi,
    private val runRepository: IndexWebhookRunRepository,
) : ViewModel() {

    private val _gesture = MutableStateFlow<RingGesture?>(null)
    val gesture = _gesture.asStateFlow()

    val dialogOpen = _gesture
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** The gesture the settings row summarises, so opening it edits the config it describes. */
    val configuredGesture = webhookPreferences.configs
        .map { configs -> configs.entries.firstOrNull { it.value.isActive }?.key }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** URL of any gesture with a saved webhook, for the settings row summary. */
    val webhookUrl = webhookPreferences.configs
        .map { configs -> configs.values.firstOrNull { it.isActive }?.url }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _urlInput = MutableStateFlow("")
    val urlInput = _urlInput.asStateFlow()

    private val _headerInputs = MutableStateFlow<List<WebhookHeaderInput>>(emptyList())
    val headerInputs = _headerInputs.asStateFlow()

    private val _payloadModeInput = MutableStateFlow(IndexWebhookPayloadMode.RecordingOnly)
    val payloadModeInput = _payloadModeInput.asStateFlow()

    private val _testState = MutableStateFlow<WebhookTestState>(WebhookTestState.Idle)
    val testState = _testState.asStateFlow()

    val runs = _gesture
        .flatMapLatest { gesture ->
            gesture?.let { runRepository.runs(it) } ?: flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList<IndexWebhookRun>())

    /** The other recording gesture, when it has a saved webhook worth copying. */
    val copyableGesture = combine(_gesture, webhookPreferences.configs) { gesture, configs ->
        otherGesture(gesture)?.takeIf { configs[it]?.isActive == true }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Whether the open gesture has a stored webhook to delete, disabled ones included. */
    val canRemove = combine(_gesture, webhookPreferences.configs) { gesture, configs ->
        gesture != null && configs[gesture]?.url?.isNotBlank() == true
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun openDialog(gesture: RingGesture = RingGesture.Hold) {
        val config = webhookPreferences.configFor(gesture)
        loadDraft(config)
        _testState.value = WebhookTestState.Idle
        _gesture.value = gesture
    }

    fun closeDialog() {
        _gesture.value = null
    }

    fun removeCurrentGesture() {
        val gesture = _gesture.value ?: return
        webhookPreferences.clear(gesture)
        closeDialog()
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

    fun copyFromOtherGesture() {
        val other = copyableGesture.value ?: return
        loadDraft(webhookPreferences.configFor(other))
    }

    fun sendTestEvent() {
        val gesture = _gesture.value ?: return
        val url = _urlInput.value.trim().ifBlank { null } ?: return
        _testState.value = WebhookTestState.Sending
        viewModelScope.launch {
            val result = webhookApi.sendTestEvent(gesture, url, draftHeaders())
            _testState.value = WebhookTestState.Done(
                ok = result.ok,
                label = "${result.status} · ${result.durationMs} ms",
            )
        }
    }

    fun save() {
        val gesture = _gesture.value ?: return
        val url = _urlInput.value.trim().ifBlank { null }
        if (url == null) {
            webhookPreferences.clear(gesture)
        } else {
            webhookPreferences.setConfig(
                gesture,
                IndexWebhookConfig(
                    url = url,
                    payloadMode = _payloadModeInput.value,
                    headers = draftHeaders(),
                    saved = true,
                ),
            )
        }
        closeDialog()
    }

    private fun loadDraft(config: IndexWebhookConfig) {
        _urlInput.value = config.url ?: ""
        _headerInputs.value = config.headers
            .map { WebhookHeaderInput(it.key, it.value) }
            .ifEmpty { listOf(WebhookHeaderInput("", "")) }
        _payloadModeInput.value = config.payloadMode
    }

    /** Drops rows with a blank name; later rows win on duplicate names. */
    private fun draftHeaders(): Map<String, String> = _headerInputs.value
        .map { it.name.trim() to it.value.trim() }
        .filter { it.first.isNotEmpty() }
        .toMap()

    private fun otherGesture(gesture: RingGesture?): RingGesture? =
        gesture?.let { IndexWebhookPreferences.gestures.firstOrNull { other -> other != it } }
}
