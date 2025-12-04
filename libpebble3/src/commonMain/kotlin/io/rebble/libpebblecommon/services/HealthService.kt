package io.rebble.libpebblecommon.services

import co.touchlab.kermit.Logger
import io.rebble.libpebblecommon.connection.PebbleProtocolHandler
import io.rebble.libpebblecommon.database.dao.HealthDao
import io.rebble.libpebblecommon.di.ConnectionCoroutineScope
import io.rebble.libpebblecommon.packets.HealthSyncOutgoingPacket
import kotlinx.coroutines.launch

class HealthService(
        private val protocolHandler: PebbleProtocolHandler,
        private val scope: ConnectionCoroutineScope,
        private val healthDao: HealthDao,
) : ProtocolService {
    private val logger = Logger.withTag("HealthService")

    fun init() {
        scope.launch {
            logger.i { "Initializing health service, requesting health sync" }
            fetchHealthData()
        }
    }

    /**
     * Fetches health data from the watch.
     *
     * @param lastSyncTime The time of the last successful sync, in epoch seconds. If this is the
     * first sync, this should be 0.
     */
    fun fetchHealthData(lastSyncTime: UInt = 0u) {
        scope.launch {
            var effectiveLastSync = lastSyncTime
            var firstSync = lastSyncTime == 0u

            if (firstSync) {
                // If we already have any health data, treat this as a follow-up sync using the
                // latest stored timestamp instead of re-requesting full history.
                val latestTimestampMs = healthDao.getLatestTimestamp()
                if (latestTimestampMs != null && latestTimestampMs > 0) {
                    effectiveLastSync = (latestTimestampMs / 1000L).toUInt()
                    firstSync = false
                    logger.i {
                        "Health data already present; using last timestamp=$effectiveLastSync instead of full first sync"
                    }
                }
            }

            val currentTime = kotlin.time.Clock.System.now().epochSeconds.toUInt()

            // Do not sync if the last sync was less than a minute ago
            if (!firstSync && effectiveLastSync > 0u && (currentTime - effectiveLastSync) < 60u) {
                logger.d { "Skipping health sync, last sync was less than a minute ago." }
                return@launch
            }

            val packet =
                    if (firstSync) {
                        // This is the first sync: request all available history
                        logger.i { "Requesting FIRST health sync (full history, timestamp=0)" }
                        HealthSyncOutgoingPacket.RequestFirstSync(0u)
                    } else {
                        // Subsequent sync, request data since the last sync time
                        val timeSinceLastSync = currentTime - effectiveLastSync
                        logger.i {
                            "Requesting health sync for last ${timeSinceLastSync}s (from timestamp=$effectiveLastSync to $currentTime)"
                        }
                        HealthSyncOutgoingPacket.RequestSync(timeSinceLastSync)
                    }
            logger.d { "Health sync packet sent: ${packet::class.simpleName}" }
            protocolHandler.send(packet)
        }
    }
}
