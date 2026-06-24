package io.rebble.libpebblecommon.js

import io.rebble.libpebblecommon.NotificationConfig
import io.rebble.libpebblecommon.WatchConfig
import io.rebble.libpebblecommon.asFlow
import io.rebble.libpebblecommon.database.dao.TimelinePinRealDao
import io.rebble.libpebblecommon.database.dao.TimelineReminderRealDao
import io.rebble.libpebblecommon.database.entity.TimelinePin
import io.rebble.libpebblecommon.database.entity.TimelinePinEntity
import io.rebble.libpebblecommon.database.entity.TimelinePinSyncEntity
import io.rebble.libpebblecommon.database.entity.TimelineReminder
import io.rebble.libpebblecommon.database.entity.TimelineReminderEntity
import io.rebble.libpebblecommon.database.entity.TimelineReminderSyncEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.json.Json
import kotlin.uuid.Uuid

internal fun testWatchConfigFlow() = WatchConfig(emulateRemoteTimeline = false).asFlow()

internal fun testNotificationConfigFlow() = NotificationConfig().asFlow()

internal fun testRemoteTimelineEmulator() = RemoteTimelineEmulator(
    watchConfigFlow = testWatchConfigFlow(),
    json = Json,
    timelinePinRealDao = NoOpTimelinePinRealDao,
    timelineReminderRealDao = NoOpTimelineReminderRealDao,
)

internal fun testHttpInterceptorManager() = HttpInterceptorManager(
    timeline = testRemoteTimelineEmulator(),
    injectedHttpInterceptors = InjectedPKJSHttpInterceptors(emptyList()),
)

private object NoOpTimelinePinRealDao : TimelinePinRealDao {
    override fun dirtyRecordsForWatchInsert(
        identifier: String,
        timestampMs: Long,
        insertOnlyAfterMs: Long,
    ): Flow<List<TimelinePinEntity>> = flowOf(emptyList())

    override fun dirtyRecordsForWatchDelete(identifier: String, timestampMs: Long): Flow<List<TimelinePinEntity>> =
        flowOf(emptyList())

    override suspend fun deleteStaleRecords(timestampMs: Long) = Unit

    override suspend fun markSyncedToWatch(syncRecord: TimelinePinSyncEntity) = Unit

    override suspend fun markDeletedFromWatch(syncRecord: TimelinePinSyncEntity) = Unit

    override fun existsOnWatch(identifier: String, primaryKey: Uuid): Flow<Boolean> = flowOf(false)

    override suspend fun insertOrReplace(item: TimelinePinEntity) = Unit

    override suspend fun insertOrReplaceAll(items: List<TimelinePinEntity>) = Unit

    override suspend fun markForDeletion(itemId: Uuid) = Unit

    override suspend fun markAllForDeletion(itemIds: List<Uuid>) = Unit

    override suspend fun markAllDeletedFromWatch(identifier: String) = Unit

    override suspend fun deleteSyncRecordsForDevicesWhichDontExist() = Unit

    override suspend fun getEntry(itemId: Uuid): TimelinePin? = null

    override fun getEntryFlow(itemId: Uuid): Flow<TimelinePin?> = flowOf(null)

    override suspend fun getPinsForWatchapp(parentId: Uuid): List<TimelinePin> = emptyList()
}

private object NoOpTimelineReminderRealDao : TimelineReminderRealDao {
    override fun dirtyRecordsForWatchInsert(
        identifier: String,
        timestampMs: Long,
        insertOnlyAfterMs: Long,
    ): Flow<List<TimelineReminderEntity>> = flowOf(emptyList())

    override fun dirtyRecordsForWatchDelete(identifier: String, timestampMs: Long): Flow<List<TimelineReminderEntity>> =
        flowOf(emptyList())

    override suspend fun deleteStaleRecords(timestampMs: Long) = Unit

    override suspend fun markSyncedToWatch(syncRecord: TimelineReminderSyncEntity) = Unit

    override suspend fun markDeletedFromWatch(syncRecord: TimelineReminderSyncEntity) = Unit

    override fun existsOnWatch(identifier: String, primaryKey: Uuid): Flow<Boolean> = flowOf(false)

    override suspend fun insertOrReplace(item: TimelineReminderEntity) = Unit

    override suspend fun insertOrReplaceAll(items: List<TimelineReminderEntity>) = Unit

    override suspend fun markForDeletion(itemId: Uuid) = Unit

    override suspend fun markAllForDeletion(itemIds: List<Uuid>) = Unit

    override suspend fun markAllDeletedFromWatch(identifier: String) = Unit

    override suspend fun deleteSyncRecordsForDevicesWhichDontExist() = Unit

    override suspend fun getEntry(itemId: Uuid): TimelineReminder? = null

    override fun getEntryFlow(itemId: Uuid): Flow<TimelineReminder?> = flowOf(null)

    override suspend fun getRemindersForPin(parentId: Uuid): List<TimelinePin> = emptyList()

    override suspend fun markForDeletionByParentId(parentId: Uuid) = Unit

    override suspend fun markForDeletionByParentIds(parentIds: List<Uuid>) = Unit
}
