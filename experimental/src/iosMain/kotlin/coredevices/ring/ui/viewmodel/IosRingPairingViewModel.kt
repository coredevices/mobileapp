package coredevices.ring.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.juul.kable.Advertisement
import com.juul.kable.Bluetooth
import com.juul.kable.Scanner
import coredevices.analytics.CoreAnalytics
import coredevices.haversine.CollectionIndexStorage
import coredevices.haversine.KMPHaversineSatelliteManager
import coredevices.ring.database.Preferences
import coredevices.ring.service.RingEvent
import coredevices.ring.service.RingPairing
import coredevices.ring.service.RingPairingException
import coredevices.ring.service.RingSync
import coredevices.ring.util.ringServiceUuid128
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import platform.Foundation.NSUUID
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

class IosRingPairingViewModel(
    private val onShowSnackbar: suspend (String) -> Unit,
    private val satelliteManager: KMPHaversineSatelliteManager
): ViewModel(), KoinComponent {
    companion object {
        private const val RSSI_THRESHOLD = -70
    }
    private val logger = Logger.withTag("IosRingPairingViewModel")
    sealed class PairingState {
        object WaitingForBluetooth : PairingState()
        object Scanning : PairingState()
        data class WaitingForPairing(val id: String, val extraDetail: String? = null) : PairingState()
        object Paired : PairingState()
    }
    private val _pairingState = MutableStateFlow<PairingState>(PairingState.Scanning)
    val pairingState = _pairingState.asStateFlow()
    private val _pairingDialogueAdv = MutableStateFlow<Advertisement?>(null)
    val pairingDialogueAdv = _pairingDialogueAdv.asStateFlow()
    private var scannerJob: Job? = null
    private var pairingDialogueResult: CompletableDeferred<Boolean>? = null
    private val indexStorage: CollectionIndexStorage by inject()
    private val preferences: Preferences by inject()
    private val coreAnalytics: CoreAnalytics by inject()
    private val ringSync: RingSync by inject()
    private val ringPairing: RingPairing by inject()
    private val longRunningScope = CoroutineScope(Dispatchers.Default)
    private val scanner = Scanner {
        filters {
            match {
                services = listOf(
                    Uuid.parse(ringServiceUuid128)
                )
            }
        }
    }

    val updateStatus = ringSync.ringEvents.filterIsInstance<RingEvent.FirmwareUpdate>().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5.seconds),
        initialValue = null
    )

    init {
        viewModelScope.launch {
            startScanning()
        }
    }

    private fun logPairedEvent() {
        coreAnalytics.logEvent("ring.pair_success")
    }
    private fun logPairFailedEvent(reason: String) {
        coreAnalytics.logEvent("ring.pair_failed", mapOf("reason" to reason))
    }
    private suspend fun openPairingDialogue(potentialRing: Advertisement): Boolean {
        val completable = CompletableDeferred<Boolean>()
        pairingDialogueResult = completable
        _pairingDialogueAdv.value = potentialRing
        return completable.await()
    }

    fun closePairingDialogue(result: Boolean) {
        pairingDialogueResult?.complete(result)
        pairingDialogueResult = null
        _pairingDialogueAdv.value = null
    }

    private fun targetRing(id: String) {
        val uid = Firebase.auth.currentUser?.uid
        if (uid == null) {
            logger.e { "No user ID found, cannot program satellite" }
            viewModelScope.launch {
                onShowSnackbar("Error: Log in to pair with ring")
            }
            return
        }
        val pressAgainInfoJob = viewModelScope.launch {
            delay(6.seconds)
            _pairingState.value = PairingState.WaitingForPairing(id, "Try pressing the ring again to pair")
        }
        longRunningScope.launch(Dispatchers.Main) { // Outlive the ViewModel scope in case user navigates away, we can get in weird state
            try {
                ringPairing.pairRing(id)
            } catch (e: RingPairingException.NoUserId) {
                logger.e(e) { "No user ID found, cannot program satellite" }
                onShowSnackbar("Error: Log in to pair with ring")
                return@launch
            } catch (e: RingPairingException.ProgrammingFailed) {
                logger.e(e) { "Failed to program satellite $id with user ID" }
                onShowSnackbar("Failed to pair with ring, please try again")
                logPairFailedEvent("programming_failed")
                return@launch
            } catch (e: Exception) {
                logger.e(e) { "Unexpected error during pairing" }
                onShowSnackbar("Failed to pair with ring, please report a bug")
                return@launch
            } finally {
                pressAgainInfoJob.cancel()
            }
            logPairedEvent()
            _pairingState.value = PairingState.Paired
            onShowSnackbar("Successfully paired with ring")
        }
    }

    private fun pairWithRing(potentialRing: Advertisement) {
        Logger.d { "Pairing with ring: ${potentialRing.name}" }
        val id = NSUUID(potentialRing.identifier.toString()).UUIDString
        targetRing(id)
        _pairingState.value = PairingState.WaitingForPairing(id)
    }

    fun startScanning() {
        scannerJob?.cancel()
        scannerJob = viewModelScope.launch {
            Bluetooth.availability.onEach {
                if (it !is Bluetooth.Availability.Available) {
                    _pairingState.value = PairingState.WaitingForBluetooth
                }
            }.first { it is Bluetooth.Availability.Available }
            val potentialRing = scanner.advertisements.onStart {
                _pairingState.value = PairingState.Scanning
            }.onEach {
                Logger.v { "Ring ${it.name}: ${it.rssi} dbm" }
            }.first { it.rssi >= RSSI_THRESHOLD }
            Logger.d { "Potential ring found: ${potentialRing.name} with RSSI ${potentialRing.rssi}" }
            val shouldPair = openPairingDialogue(potentialRing)
            Logger.d { "ShouldPair $shouldPair" }
            pairWithRing(potentialRing)
        }
    }
}