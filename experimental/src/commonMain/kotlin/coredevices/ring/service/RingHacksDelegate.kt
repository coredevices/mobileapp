package coredevices.ring.service

import co.touchlab.kermit.Logger
import coredevices.haversine.KMPHaversineHacksDelegate
import coredevices.haversine.KMPHaversineSatellite
import coredevices.ring.database.Preferences

class RingHacksDelegate(
    private val prefs: Preferences
): KMPHaversineHacksDelegate {
    private val logger = Logger.withTag("RingHacksDelegate")
    override fun shouldWipeCollectionsBeforeTransfer(satellite: KMPHaversineSatellite): Boolean {
        if (satellite.id == prefs.ringPaired.value && prefs.lastWipedRing.value != satellite.id) {
            logger.i { "First time seeing paired ring ${satellite.id}, erasing collections" }
            return true
        } else {
            return false
        }
    }

    override fun wipedCollectionsBeforeTransfer(satellite: KMPHaversineSatellite) {
        if (satellite.id == prefs.ringPaired.value) {
            logger.i { "Marking paired ring ${satellite.id} as wiped" }
            prefs.setLastWipedRing(satellite.id)
        }
    }

}