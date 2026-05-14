package coredevices.libindex.device

import coredevices.haversine.KMPHaversineSatellite
import coredevices.haversine.KMPHaversineSatelliteState
import coredevices.libindex.database.BasePreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.stateIn
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class RealIndexDevice(
    override val identifier: IndexIdentifier,
    override val name: String
) : IndexDevice

class RealDiscoveredIndexDevice(
    indexDevice: IndexDevice,
    private val indexPairing: IndexPairing,
    override val rssi: Int,
    override val name: String,
    override val pairingState: IndexPairingState
): IndexDevice by indexDevice, DiscoveredIndexDevice {
    override suspend fun pair(): IndexPairingResult {
        return indexPairing.pairDevice(this)
    }
}

class RealInterviewedIndexDevice(
    indexDevice: KnownIndexDevice,
    override val name: String,
    override val updating: Boolean,
    private val state: KMPHaversineSatelliteState
): KnownIndexDevice by indexDevice, InterviewedIndexDevice {
    override val firmwareVersion: String = state.firmwareVersion
    override val serialNumber: String = state.programmedSerialNumber ?: state.serialNumber
    override val mac: String = state.serialNumber
}

class RealKnownIndexDevice(
    indexDevice: IndexDevice,
    private val prefs: BasePreferences
): IndexDevice by indexDevice, KnownIndexDevice {
    override val name: String = indexDevice.name
    override fun remove() {
        prefs.setRingPaired(null)
    }
}

class IndexDeviceFactory(
    private val prefs: BasePreferences
): KoinComponent {
    private val indexPairing: IndexPairing by inject()

    fun create(
        identifier: IndexIdentifier,
        name: String,
        scanResult: IndexScanResult? = null,
        isPaired: Boolean = false,
        satellite: KMPHaversineSatellite? = null,
        satelliteState: KMPHaversineSatelliteState? = null,
        pairingState: IndexPairingState = IndexPairingState.NotPaired,
        isUpdating: Boolean = false,
    ): IndexDevice {
        val base = RealIndexDevice(identifier, name)
        val known = if (isPaired) RealKnownIndexDevice(base, prefs) else null

        return when {
            known != null && satellite != null && satelliteState != null ->
                RealInterviewedIndexDevice(known, satellite.name ?: "Index 01", isUpdating, satelliteState)

            known != null -> known

            scanResult != null -> RealDiscoveredIndexDevice(
                base,
                indexPairing,
                scanResult.rssi,
                name,
                pairingState,
            )

            else -> base
        }
    }
}