package coredevices.ring.service

import co.touchlab.kermit.Logger
import coredevices.analytics.CoreAnalytics
import coredevices.haversine.KMPHaversineSatelliteManager
import coredevices.ring.database.Preferences
import coredevices.ring.database.room.repository.RingTransferRepository
import coredevices.util.Platform
import coredevices.util.isIOS
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class RingPairing(
    private val preferences: Preferences,
    private val indexStorage: PrefsCollectionIndexStorage,
    private val transferRepo: RingTransferRepository,
    private val satelliteManager: KMPHaversineSatelliteManager,
    private val platform: Platform,
) {

    companion object {
        private val logger = Logger.withTag("RingPairing")
    }

    suspend fun pairRing(satelliteId: String) {
        val uid = Firebase.auth.currentUser?.uid ?: throw RingPairingException.NoUserId()
        logger.d { "Starting pairing process for satellite $satelliteId and user ID $uid" }
        withContext(Dispatchers.IO) {
            if (platform.isIOS) {
                // iOS: Mark as paired immediately to enable service as connection attempt pairs
                preferences.setRingPaired(satelliteId)
            }
            indexStorage.setLastSuccessfulCollectionIndex(null)
            transferRepo.markTransfersAsPreviousIndexIteration()
        }
        val result = satelliteManager.programSatelliteWithUserID(satelliteId, uid)
        suspendCancellableCoroutine { c ->
            result.onSuccess {
                logger.d { "Successfully programmed satellite $satelliteId with user ID" }
                c.resume(Unit)
            }.onFailure { exception ->
                logger.e(exception) { "Failed to program satellite $satelliteId with user ID" }
                c.resumeWithException(RingPairingException.ProgrammingFailed(exception))
            }
        }
    }
}

sealed class RingPairingException: Exception() {
    class NoUserId() : RingPairingException()
    class ProgrammingFailed(override val cause: Throwable) : RingPairingException()
}