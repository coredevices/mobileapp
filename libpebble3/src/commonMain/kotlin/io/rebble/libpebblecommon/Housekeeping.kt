package io.rebble.libpebblecommon

import io.rebble.libpebblecommon.database.BlobDbDatabaseManager
import io.rebble.libpebblecommon.database.dao.HealthDao
import io.rebble.libpebblecommon.database.dao.NotificationDao
import io.rebble.libpebblecommon.di.LibPebbleCoroutineScope
import io.rebble.libpebblecommon.permissions.AppDataUsage
import io.rebble.libpebblecommon.plugin.PluginActionLog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

class Housekeeping(
    private val libPebbleCoroutineScope: LibPebbleCoroutineScope,
    private val notificationsDao: NotificationDao,
    private val healthDao: HealthDao,
    private val notificationConfigFlow: NotificationConfigFlow,
    private val clock: Clock,
    private val blodDbDatabaseManager: BlobDbDatabaseManager,
    private val appDataUsage: AppDataUsage,
    private val pluginActionLog: PluginActionLog,
) {
    fun init() {
        libPebbleCoroutineScope.launch {
            blodDbDatabaseManager.deleteSyncRecordsForStaleDevices()
            while (true) {
                doHousekeeping()
                delay(6.hours)
            }
        }
    }

    private suspend fun doHousekeeping() {
        val deleteNotificationsOlderThan = clock.now() - notificationConfigFlow.value.storeNotifiationsForDays.days
        notificationsDao.deleteOldNotifications(deleteNotificationsOlderThan.toEpochMilliseconds())

        appDataUsage.deleteOlderThan(DATA_USAGE_KEPT_DAYS)
        pluginActionLog.deleteOlderThan(PLUGIN_ACTION_LOG_KEPT_DAYS)

        val deleteHealthDataOlderThan = clock.now() - 180.days
        val deletedHealthRecords = healthDao.deleteExpiredHealthData(deleteHealthDataOlderThan.epochSeconds)
        val deletedOverlayRecords = healthDao.deleteExpiredOverlayData(deleteHealthDataOlderThan.epochSeconds)
        if (deletedHealthRecords > 0 || deletedOverlayRecords > 0) {
            println("Housekeeping: Deleted $deletedHealthRecords health records and $deletedOverlayRecords overlay records older than 90 days")
        }
    }
}

const val DATA_USAGE_KEPT_DAYS = 30
const val PLUGIN_ACTION_LOG_KEPT_DAYS = 30
