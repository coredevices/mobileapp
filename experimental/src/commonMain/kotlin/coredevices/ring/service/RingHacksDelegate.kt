package coredevices.ring.service

import co.touchlab.kermit.Logger
import coredevices.haversine.CollectionIndexStorage
import coredevices.haversine.KMPHaversineHacksDelegate
import coredevices.haversine.KMPHaversineSatellite
import coredevices.haversine.KMPHaversineSatelliteManager
import coredevices.libindex.database.repository.RingTransferRepository
import coredevices.ring.database.Preferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch

class RingHacksDelegate(
    private val prefs: Preferences,
    private val collectionIndexStorage: CollectionIndexStorage,
    private val transferRepo: RingTransferRepository,
    private val scope: RecordingBackgroundScope
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
        logger.i { "Marking paired ring ${satellite.id} as wiped" }
        prefs.setLastWipedRing(satellite.id)
        collectionIndexStorage.setLastSuccessfulCollectionIndex(null)
        scope.launch(Dispatchers.IO) {
            transferRepo.markTransfersAsPreviousIndexIteration()
        }
    }

}