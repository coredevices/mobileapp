package coredevices.libindex.device

import coredevices.haversine.KMPHaversineSatelliteManager
import kotlin.time.Duration

interface IndexSystem {
    suspend fun forceFailsafe(scanResult: IndexScanResult)
    suspend fun measureRSSI(device: IndexDevice, connectionTimeout: Duration): RSSIMeasurement
}

class RealIndexSystem(
    private val satelliteManager: KMPHaversineSatelliteManager
): IndexSystem {
    private val RSSI_SAMPLES = 20

    override suspend fun forceFailsafe(scanResult: IndexScanResult) {
        // The satellite library ignores production test rings in its own scan so that a stray
        // consumer app cannot interfere with factory testing; hand it the app scan's advertisement.
        val satellite = satelliteManager.ingestAdvertisement(
            satelliteId = scanResult.identifier.asString,
            name = scanResult.name,
            rssi = scanResult.rssi,
            manufacturerData = requireNotNull(scanResult.manufacturerData) {
                "No advertisement captured for ${scanResult.identifier.asString}"
            },
        )
        satellite.forceFailsafe()
    }

    override suspend fun measureRSSI(device: IndexDevice, connectionTimeout: Duration): RSSIMeasurement {
        val satellite = requireNotNull(satelliteManager.getSatelliteById(device.identifier.asString)) {
            "Index 01 is not connected"
        }
        val measurement = satellite.measureRSSI(RSSI_SAMPLES, connectionTimeout.inWholeSeconds.toUInt())
        return RSSIMeasurement(deviceRSSI = measurement.rxRSSI, phoneRSSI = measurement.phoneRSSI)
    }
}