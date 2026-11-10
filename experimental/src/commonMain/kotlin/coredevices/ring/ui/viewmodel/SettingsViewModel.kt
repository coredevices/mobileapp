package coredevices.ring.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import coredevices.libindex.device.IndexDeviceManager
import coredevices.libindex.device.InterviewedIndexDevice
import coredevices.libindex.device.KnownIndexDevice
import coredevices.libindex.device.RSSIMeasurement
import coredevices.ring.RingDelegate
import coredevices.ring.agent.integrations.providers.IntegrationProvider
import coredevices.ring.agent.integrations.providers.IntegrationSupport
import coredevices.ring.agent.integrations.providers.LegacyDestinations
import coredevices.ring.data.NoteShortcutType
import coredevices.ring.database.Preferences
import coredevices.util.CommonBuildKonfig
import coredevices.util.emailOrNull
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import io.rebble.libpebblecommon.connection.LibPebble
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

sealed class DiagnosticsState {
    object Idle : DiagnosticsState()
    object Running : DiagnosticsState()
    data class Error(val message: String) : DiagnosticsState()
    data class Completed(val measurement: RSSIMeasurement) : DiagnosticsState()
}

private const val DIAGNOSTICS_ATTEMPTS = 3
private val DIAGNOSTICS_RETRY_DELAY = 1.seconds
private val DIAGNOSTICS_CONNECTION_TIMEOUT = 10.seconds

class SettingsViewModel(
    private val preferences: Preferences,
    private val destinations: DestinationCatalog,
    private val indexDeviceManager: IndexDeviceManager,
    private val ringDelegate: RingDelegate,
    legacy: LegacyDestinations,
): ViewModel() {
    val version = CommonBuildKonfig.GIT_HASH
    val username = Firebase.auth.authStateChanged
        .map { it?.emailOrNull }
        .stateIn(viewModelScope, SharingStarted.Lazily, Firebase.auth.currentUser?.email)

    val debugDetailsEnabled = preferences.debugDetailsEnabled
    private val _showNoteShortcutDialog = MutableStateFlow(false)
    val showNoteShortcutDialog = _showNoteShortcutDialog.asStateFlow()
    val noteShortcut = preferences.noteShortcut
    val autoDismissActionNotifications = preferences.autoDismissActionNotifications
    private val currentRing = indexDeviceManager.rings.map {
        it.firstOrNull { ring -> ring is KnownIndexDevice }
    }
    val ringPaired = preferences.ringPaired
    val currentRingFirmware = currentRing
        .mapNotNull { (it as? InterviewedIndexDevice)?.firmwareVersion }
        .stateIn(
            viewModelScope,
            started = SharingStarted.Lazily,
            initialValue = null
        )

    val currentRingName = currentRing
        .map { it?.name }
        .stateIn(
            viewModelScope,
            started = SharingStarted.Lazily,
            initialValue = null
        )

    val availableNoteProviders = connectedProviders(IntegrationSupport.Note)
    val availableReminderProviders = connectedProviders(IntegrationSupport.Reminder)

    val legacyDestinations = legacy.pending
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _diagnosticsState = MutableStateFlow<DiagnosticsState>(DiagnosticsState.Idle)
    val diagnosticsState = _diagnosticsState.asStateFlow()

    fun togglePendingIntentScanEnabled() {
        viewModelScope.launch(Dispatchers.IO) {
            preferences.setUsePendingIntentScan(!preferences.usePendingIntentScan.value)
        }
    }

    val pendingIntentScanEnabled = preferences.usePendingIntentScan

    fun toggleScanByPairedAddress() {
        viewModelScope.launch(Dispatchers.IO) {
            preferences.setScanByPairedAddress(!preferences.scanByPairedAddress.value)
        }
    }

    val scanByPairedAddress = preferences.scanByPairedAddress

    fun beginDiagnostics(): Job {
        _diagnosticsState.value = DiagnosticsState.Running
        return viewModelScope.launch {
            val ring = indexDeviceManager.rings.value.filterIsInstance<KnownIndexDevice>().firstOrNull()
                ?: run {
                    Logger.withTag("RingDiagnostics").w { "No paired ring found for diagnostics" }
                    _diagnosticsState.value = DiagnosticsState.Idle
                    return@launch
                }

            var lastError: Exception? = null
            repeat(DIAGNOSTICS_ATTEMPTS) { attempt ->
                try {
                    val result = ring.measureRSSI(DIAGNOSTICS_CONNECTION_TIMEOUT)
                    Logger.withTag("RingDiagnostics").i { "RSSI diagnostic result: $result" }
                    _diagnosticsState.value = DiagnosticsState.Completed(result)
                    return@launch
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    lastError = e
                    Logger.withTag("RingDiagnostics").w(e) {
                        "RSSI measurement failed (attempt ${attempt + 1}/$DIAGNOSTICS_ATTEMPTS)"
                    }
                    if (attempt < DIAGNOSTICS_ATTEMPTS - 1) delay(DIAGNOSTICS_RETRY_DELAY)
                }
            }
            _diagnosticsState.value = DiagnosticsState.Error(
                lastError?.message ?: "Couldn't reach your Index 01"
            )
        }.apply {
            invokeOnCompletion {
                if (it is CancellationException) {
                    _diagnosticsState.value = DiagnosticsState.Idle
                }
            }
        }
    }

    fun resetDiagnostics() {
        _diagnosticsState.value = DiagnosticsState.Idle
    }

    private fun refreshAvailableProviders() {
        destinations.refresh()
    }

    private fun connectedProviders(support: IntegrationSupport): StateFlow<List<IntegrationProvider>> = destinations
        .items(support)
        .map { items -> items.filterIsInstance<DestinationItem.Installed>().filter { it.connected }.map { it.provider } }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun toggleDebugDetailsEnabled() {
        viewModelScope.launch {
            val newValue = !debugDetailsEnabled.value
            preferences.setDebugDetailsEnabled(newValue)
        }
    }

    fun toggleAutoDismissActionNotifications() {
        preferences.setAutoDismissActionNotifications(!autoDismissActionNotifications.value)
    }

    fun showNoteShortcutDialog() {
        refreshAvailableProviders()
        _showNoteShortcutDialog.value = true
    }

    fun closeNoteShortcutDialog() {
        _showNoteShortcutDialog.value = false
    }

    fun setNoteShortcut(shortcut: NoteShortcutType) {
        preferences.setNoteShortcut(shortcut)
    }

    fun restartPreemptiveTransfer() {
        ringDelegate.restartPreemptiveTransfer()
    }
}
