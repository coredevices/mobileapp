package coredevices.libindex.device

import coredevices.haversine.KMPHaversineSatelliteManager

interface IndexSystem {
    suspend fun forceFailsafe(device: IndexDevice)
}

class RealIndexSystem(
    private val satelliteManager: KMPHaversineSatelliteManager
): IndexSystem {
    override suspend fun forceFailsafe(device: IndexDevice) {
        val device = requireNotNull(satelliteManager.getSatelliteById(device.identifier.asString))
        device.forceFailsafe()
    }
}