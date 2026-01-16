package io.rebble.libpebblecommon.services

import co.touchlab.kermit.Logger
import io.rebble.libpebblecommon.connection.ConnectedPebble.Health
import io.rebble.libpebblecommon.connection.PebbleProtocolHandler
import io.rebble.libpebblecommon.database.dao.HealthDao
import io.rebble.libpebblecommon.database.entity.HealthStatDao
import io.rebble.libpebblecommon.di.ConnectionCoroutineScope
import io.rebble.libpebblecommon.packets.HealthCommand
import io.rebble.libpebblecommon.packets.HealthResult
import io.rebble.libpebblecommon.packets.HealthSyncIncomingPacket
import io.rebble.libpebblecommon.packets.HealthSyncOutgoingPacket
import io.rebble.libpebblecommon.web.withTimeoutOr
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onSubscription
import kotlin.time.Clock.System
import kotlin.time.Duration.Companion.seconds

/**
 * Handles health data synchronization between watch and phone.
 *
 * Key responsibilities:
 * - Receiving health data from the watch (steps, sleep, activities, heart rate)
 * - Storing health data in the database with conflict resolution
 * - Sending health statistics back to the watch (averages, daily summaries)
 * - Managing sync lifecycle and preventing battery drain
 *
 * Sync behavior:
 * - Automatic sync on connection
 * - No throttling when app is in foreground (immediate sync)
 * - 30-minute throttle when app is in background (battery saving)
 * - Full stats sync on connection or once per 12 hours
 * - Daily stats update runs every 24 hours
 * - Immediate "today" updates when receiving new data
 *
 * Conflict resolution:
 * - Steps: "highest step count wins" strategy
 * - Phone database is source of truth during reconciliation
 */
class HealthService(
    private val protocolHandler: PebbleProtocolHandler,
    private val healthDao: HealthDao,
) : ProtocolService, Health {
    companion object {
        private val logger = Logger.withTag("HealthService")
    }

    /**
     * Request health data from the watch.
     * @param fullSync If true, requests all historical data. If false, requests data since last
     * sync.
     */
    override suspend fun requestHealthData(fullSync: Boolean): Boolean {
        val lastSync = if (fullSync) {
            0L
        } else {
            healthDao.getLatestTimestamp() ?: 0L
        }
        val timeSinceLastSyncSeconds = (System.now() - lastSync.seconds).epochSeconds
        logger.d {
            "HEALTH_SERVICE: Requesting incremental health data sync (last ${timeSinceLastSyncSeconds}s)"
        }
        val packet = HealthSyncOutgoingPacket.RequestSync(timeSinceLastSyncSeconds.toUInt())
        return withTimeoutOr(timeout = 10.seconds, block = {
            val response = protocolHandler.inboundMessages.onSubscription {
                protocolHandler.send(packet)
            }.filterIsInstance(HealthSyncIncomingPacket::class)
                .first { it.command.get() == HealthCommand.Response.code }
            response.result.get() == HealthResult.Ack.code
        }, onTimeout = {
            logger.w { "Health sync request timed out" }
            false
        })
    }
}
