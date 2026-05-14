package coredevices.libindex.device

import co.touchlab.kermit.Logger
import coredevices.haversine.KMPHaversineSatellite
import coredevices.haversine.KMPHaversineSatelliteManager
import coredevices.libindex.IndexDevices
import coredevices.libindex.Rings
import coredevices.libindex.database.BasePreferences
import coredevices.libindex.database.PrefsCollectionIndexStorage
import coredevices.libindex.database.repository.RingTransferRepository
import coredevices.libindex.di.LibIndexCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

class IndexDeviceManager(
    private val satelliteManager: KMPHaversineSatelliteManager,
    private val scope: LibIndexCoroutineScope,
    private val deviceFactory: IndexDeviceFactory,
    private val prefs: BasePreferences,
    // Not present on some platforms (iOS)
    private val associations: IndexPlatformBluetoothAssociations?,
    private val indexStorage: PrefsCollectionIndexStorage,
    private val transferRepo: RingTransferRepository,
): Rings {
    private val _rings = MutableStateFlow(emptyList<IndexDevice>())
    override val rings: IndexDevices = _rings

    companion object {
        private val logger = Logger.withTag("IndexDeviceRepository")
    }

    fun update(indexDevice: IndexDevice) {
        _rings.update { prev ->
            val existingIdx = prev.indexOfFirst { indexDevice.identifier.asString.equals(it.identifier.asString, ignoreCase = true) }
            if (existingIdx != -1) {
                prev
                    .toMutableList()
                    .apply { set(existingIdx, indexDevice) }
            } else {
                prev + indexDevice
            }
        }
    }

    private fun updateRing(satellite: KMPHaversineSatellite, isUpdating: Boolean? = null) {
        _rings.update { prev ->
            val existingIdx = prev.indexOfFirst { satellite.id.equals(it.identifier.asString, ignoreCase = true) }
            val existing = if (existingIdx != -1) prev[existingIdx] as? KnownIndexDevice else null
            if (existingIdx != -1 && existing != null) {
                if (prefs.ringPairedName.value != satellite.name) {
                    prefs.setRingPairedName(satellite.name)
                }
                prev
                    .toMutableList()
                    .apply {
                        set(
                            existingIdx,
                            deviceFactory.create(
                                identifier = existing.identifier,
                                name = existing.name,
                                isPaired = true,
                                satellite = satellite,
                                satelliteState = satellite.state.value!!,
                                isUpdating = isUpdating
                                    ?: (existing is InterviewedIndexDevice && (existing as InterviewedIndexDevice).updating)
                            )
                        )
                    }
            } else {
                prev
            }
        }
    }

    fun init() {
        prefs.ringPaired.value?.let { pairedId ->
            val association = associations?.associations?.value?.firstOrNull { it.identifier == IndexIdentifier(pairedId) }
            if (associations != null && association == null) {
                logger.d { "Paired ring $pairedId not found in bt associations, clearing paired state" }
                prefs.setRingPaired(null)
                prefs.setRingPairedName(null)
            } else if (association != null && association.deviceName != prefs.ringPairedName.value) {
                prefs.setRingPairedName(association.deviceName)
            }
        } ?: scope.launch {
            associations?.associationsReady?.await()
            logger.d { "No paired ring stored, looking for one in bt associations" }
            val candidate = associations?.associations?.value
                ?.firstOrNull { it.deviceName.contains("Pebble Index", ignoreCase = true) }
            candidate?.let {
                logger.d { "Found candidate ${it.deviceName} (${it.identifier.asString}) in bt associations, setting as paired ring" }
                prefs.setRingPaired(it.identifier.asString)
                prefs.setRingPairedName(it.deviceName)
                scope.launch(Dispatchers.IO) {
                    indexStorage.setLastSuccessfulCollectionIndex(null)
                    transferRepo.markTransfersAsPreviousIndexIteration()
                }
            }
        }
        associations?.bondStateChanges?.onEach { evt ->
            if (evt.state == IndexBondState.NotBonded && evt.identifier.asString == prefs.ringPaired.value) {
                logger.d { "Received bond state change for paired ring ${evt.identifier.asString}, state=${evt.state}, removing paired state" }
                prefs.setRingPaired(null)
                prefs.setRingPairedName(null)
            } else if (evt.state == IndexBondState.Bonded && evt.identifier.asString == prefs.ringPaired.value) {
                logger.d { "Received bond state change for paired ring ${evt.identifier.asString}, state=${evt.state}, ring likely SOS'd, considering it a new iteration" }
                indexStorage.setLastSuccessfulCollectionIndex(null)
                transferRepo.markTransfersAsPreviousIndexIteration()
                evt.name?.let { prefs.setRingPairedName(it) }
            } else if (evt.state == IndexBondState.Bonded && prefs.ringPaired.value == null && evt.name?.contains("Pebble Index", ignoreCase = true) == true) {
                logger.d { "Received bond state change for unpaired ring ${evt.identifier.asString}, state=${evt.state}, setting as paired ring" }
                indexStorage.setLastSuccessfulCollectionIndex(null)
                transferRepo.markTransfersAsPreviousIndexIteration()
                prefs.setRingPaired(evt.identifier.asString)
                prefs.setRingPairedName(evt.name)
            }
        }?.flowOn(Dispatchers.IO)?.launchIn(scope)
        prefs.ringPaired
            .runningFold<String?, Pair<String?, String?>>(null to null) { (_, prev), new -> prev to new }
            .drop(1)
            .onEach { (old, new) ->
                logger.d { "Paired ring changed from $old to $new" }
                // remove old if it isnt the same as new, otherwise keep it to avoid UI flickering
                if (old != null && old != new) {
                    logger.d { "Removing old from list" }
                    _rings.update { prev ->
                        prev.filterNot { it.identifier.asString.equals(old, ignoreCase = true) }
                    }
                }
                if (new != null) {
                    logger.d { "Adding new to list" }
                    _rings.update { prev ->
                        // replace if exists and is a DiscoveredIndexDevice, otherwise add
                        val existing = prev.indexOfFirst { it.identifier.asString.equals(new, ignoreCase = true) }
                        val known = deviceFactory.create(
                            identifier = IndexIdentifier(new),
                            name = prefs.ringPairedName.value ?: "Index 01",
                            isPaired = true,
                        )
                        if (existing != -1 && prev[existing] is DiscoveredIndexDevice) {
                            prev
                                .toMutableList()
                                .apply { set(existing, known) }
                        } else {
                            prev + known
                        }
                    }
                }
            }.launchIn(scope)
        satelliteManager.lastRing
            .onEach {
                if (it != null) {
                    // Wait for state to be non-null, just in case
                    withTimeoutOrNull(1.seconds) {
                        it.state.filterNotNull().first()
                    } ?: return@onEach

                    updateRing(it, isUpdating = null)
                }
            }.launchIn(scope)
    }

    fun markFirmwareUpdatingState(identifier: KMPHaversineSatellite, isUpdating: Boolean) {
        updateRing(identifier, isUpdating)
    }

    fun addScanResult(result: IndexScanResult) {
        _rings.update { prev ->
            val existingIdx = prev.indexOfFirst { it.identifier.asString.equals(result.identifier.asString, ignoreCase = true) }
            val existing = existingIdx.takeIf { it != -1 }?.let { prev[it] }
            if (existing is DiscoveredIndexDevice) {
                prev
                    .toMutableList()
                    .apply {
                        set(
                            existingIdx,
                            deviceFactory.create(
                                identifier = result.identifier,
                                name = result.name,
                                scanResult = result,
                                pairingState = existing.pairingState,
                            )
                        )
                    }
            } else if (existing == null) {
                prev + deviceFactory.create(
                    identifier = result.identifier,
                    name = result.name,
                    scanResult = result,
                )
            } else { // existing is a KnownIndexDevice, ignore scan result
                prev
            }
        }
    }

    fun clearScanResults() {
        _rings.update { prev ->
            prev.filterNot { it is DiscoveredIndexDevice }
        }
    }
}

data class IndexScanResult(
    val identifier: IndexIdentifier,
    val name: String,
    val rssi: Int,
)