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
import kotlin.time.Duration

class RealIndexDevice(
    override val identifier: IndexIdentifier,
    override val name: String
) : IndexDevice

class RealDiscoveredIndexDevice(
    indexDevice: IndexDevice,
    override val rssi: Int,
    override val name: String,
    override val currentImage: IndexImage
): IndexDevice by indexDevice, DiscoveredIndexDevice

class RealPairableIndexDevice(
    indexDevice: IndexDevice,
    private val indexPairing: IndexPairing,
    override val rssi: Int,
    override val name: String,
    override val pairingState: IndexPairingState,
    override val currentImage: IndexImage
): IndexDevice by indexDevice, PairableIndexDevice {
    override suspend fun pair(): IndexPairingResult {
        return indexPairing.pairDevice(this)
    }
}

class RealRepairableIndexDevice(
    indexDevice: IndexDevice,
    private val indexSystem: IndexSystem,
    private val scanResult: IndexScanResult,
): IndexDevice by indexDevice, RepairableIndexDevice {
    override val rssi: Int get() = scanResult.rssi
    override val currentImage: IndexImage get() = scanResult.currentImage

    override suspend fun forceFailsafe() {
        indexSystem.forceFailsafe(scanResult)
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
    private val prefs: BasePreferences,
    private val system: IndexSystem,
): IndexDevice by indexDevice, KnownIndexDevice {
    override val name: String = indexDevice.name
    override fun remove() {
        prefs.setRingPaired(null)
    }

    override suspend fun measureRSSI(connectionTimeout: Duration): RSSIMeasurement {
        return system.measureRSSI(this, connectionTimeout)
    }
}

class IndexDeviceFactory(
    private val prefs: BasePreferences
): KoinComponent {
    private val indexPairing: IndexPairing by inject()
    private val indexSystem: IndexSystem by inject()

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
        val known = if (isPaired) RealKnownIndexDevice(base, prefs, indexSystem) else null

        return when {
            known != null && satellite != null && satelliteState != null ->
                RealInterviewedIndexDevice(known, satellite.name ?: "Index 01", isUpdating, satelliteState)

            known != null -> known

            scanResult != null -> when (scanResult.currentImage) {
                IndexImage.ProductionTest -> RealRepairableIndexDevice(base, indexSystem, scanResult)
                IndexImage.Primary -> RealPairableIndexDevice(
                    base,
                    indexPairing,
                    scanResult.rssi,
                    name,
                    pairingState,
                    scanResult.currentImage
                )
                IndexImage.Failsafe -> RealDiscoveredIndexDevice(
                    base,
                    scanResult.rssi,
                    name,
                    scanResult.currentImage
                )
            }

            else -> base
        }
    }
}