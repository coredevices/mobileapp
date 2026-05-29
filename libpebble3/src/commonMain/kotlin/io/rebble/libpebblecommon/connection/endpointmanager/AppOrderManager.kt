package io.rebble.libpebblecommon.connection.endpointmanager

import co.touchlab.kermit.Logger
import com.russhwolf.settings.Settings
import com.russhwolf.settings.set
import io.rebble.libpebblecommon.WatchConfigFlow
import io.rebble.libpebblecommon.connection.PebbleIdentifier
import io.rebble.libpebblecommon.connection.endpointmanager.blobdb.TimeProvider
import io.rebble.libpebblecommon.database.dao.LockerEntryRealDao
import io.rebble.libpebblecommon.di.ConnectionCoroutineScope
import io.rebble.libpebblecommon.locker.AppType
import io.rebble.libpebblecommon.packets.AppReorderRequest
import io.rebble.libpebblecommon.services.AppReorderService
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

class AppOrderManager(
    identifier: PebbleIdentifier,
    private val settings: Settings,
    private val lockerDao: LockerEntryRealDao,
    private val connectionScope: ConnectionCoroutineScope,
    private val watchConfigFlow: WatchConfigFlow,
    private val service: AppReorderService,
    private val timeProvider: TimeProvider,
    private val json: Json,
) {
    private val logger = Logger.withTag("AppOrderManager")
    private val transport = identifier.asString
    private val settingsKey = "app_order_-$transport"
    private var stored: AppOrder = settings.getStringOrNull(settingsKey)?.let {
        json.decodeFromString(it)
    } ?: AppOrder(emptyList(), emptyList())

    /**
     * @param forceResend true when the watch was wiped (unfaithful / first connection).
     */
    fun init(forceResend: Boolean) {
        connectionScope.launch {
            if (forceResend) {
                resendOrderAfterWatchRepopulated()
            }
            watchOrderChanges()
        }
    }

    private suspend fun resendOrderAfterWatchRepopulated() {
        // Wait until the app blobdb has been re-inserted and acked by the watch, so the firmware
        // can resolve our UUIDs to the (newly assigned) install_ids when it stores the order.
        try {
            withTimeout(WAIT_FOR_SYNC_TIMEOUT) {
                lockerDao.dirtyRecordsForWatchInsert(
                    identifier = transport,
                    timestampMs = timeProvider.now().toEpochMilliseconds(),
                    insertOnlyAfterMs = -1L,
                ).first { it.isEmpty() }
            }
        } catch (e: TimeoutCancellationException) {
            logger.w { "Timed out waiting for locker re-sync; re-sending app order anyway" }
        }
        val limit = watchConfigFlow.value.lockerSyncLimitV2
        stored = AppOrder(
            watchfaces = lockerDao.getAppOrderFlow(AppType.Watchface.code, limit).first(),
            watchapps = lockerDao.getAppOrderFlow(AppType.Watchapp.code, limit).first(),
        )
        logger.d { "Re-sending app order after watch wipe" }
        updateOrder()
    }

    private fun watchOrderChanges() {
        connectionScope.launch {
            lockerDao.getAppOrderFlow(
                type = AppType.Watchapp.code,
                limit = watchConfigFlow.value.lockerSyncLimitV2,
            ).distinctUntilChanged().collect { newOrder ->
                if (newOrder != stored.watchapps) {
                    stored = stored.copy(watchapps = newOrder)
                    updateOrder()
                }
            }
        }
        connectionScope.launch {
            lockerDao.getAppOrderFlow(
                type = AppType.Watchface.code,
                limit = watchConfigFlow.value.lockerSyncLimitV2,
            ).distinctUntilChanged().collect { newOrder ->
                if (newOrder != stored.watchfaces) {
                    stored = stored.copy(watchfaces = newOrder)
                    updateOrder()
                }
            }
        }
    }

    private suspend fun updateOrder() {
        logger.d { "Sending app order update: $stored" }
        service.send(AppReorderRequest(stored.watchapps + stored.watchfaces))
        settings.set(settingsKey, json.encodeToString(stored))
    }

    companion object {
        private val WAIT_FOR_SYNC_TIMEOUT = 60.seconds
    }
}

@Serializable
private data class AppOrder(
    val watchfaces: List<Uuid>,
    val watchapps: List<Uuid>,
)
