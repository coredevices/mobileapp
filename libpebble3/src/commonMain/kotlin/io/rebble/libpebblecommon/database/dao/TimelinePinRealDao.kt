package io.rebble.libpebblecommon.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import io.rebble.libpebblecommon.database.entity.TimelinePin
import io.rebble.libpebblecommon.database.entity.TimelinePinDao
import kotlin.uuid.Uuid

@Dao
interface TimelinePinRealDao : TimelinePinDao {
    @Query("SELECT * FROM TimelinePinEntity WHERE parentId = :parentId AND deleted = 0")
    suspend fun getPinsForWatchapp(parentId: Uuid): List<TimelinePin>

    /**
     * You probably should use this instead of [markForDeletion].
     */
    @Transaction
    suspend fun markForDeletionWithReminders(itemId: Uuid, reminderDao: TimelineReminderRealDao) {
        markForDeletion(itemId)
        reminderDao.markForDeletionByParentId(itemId)
    }

    /**
     * You probably should use this instead of [markAllForDeletion].
     */
    @Transaction
    suspend fun markAllForDeletionWithReminders(itemIds: List<Uuid>, reminderDao: TimelineReminderRealDao) {
        markAllForDeletion(itemIds)
        reminderDao.markForDeletionByParentIds(itemIds)
    }

    /**
     * Mark every pin whose parent app is in [parentIds] (plus their reminders)
     * for deletion. Call this when apps are removed from the locker so their
     * pending pins don't stay on the watch as ghost notifications.
     */
    @Transaction
    suspend fun markAllForDeletionByParentIdsWithReminders(
        parentIds: List<Uuid>,
        reminderDao: TimelineReminderRealDao,
    ) {
        if (parentIds.isEmpty()) return
        val pinIds = parentIds.flatMap { parentId ->
            getPinsForWatchapp(parentId).map { it.itemId }
        }
        if (pinIds.isEmpty()) return
        markAllForDeletionWithReminders(pinIds, reminderDao)
    }
}