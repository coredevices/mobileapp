package io.rebble.libpebblecommon.database.dao

import androidx.room.Dao
import androidx.room.Query
import io.rebble.libpebblecommon.database.entity.TimelineReminder
import io.rebble.libpebblecommon.database.entity.TimelineReminderDao
import kotlin.uuid.Uuid

@Dao
interface TimelineReminderRealDao : TimelineReminderDao {
    @Query("SELECT * FROM TimelineReminderEntity WHERE parentId = :parentId AND deleted = 0")
    suspend fun getRemindersForPin(parentId: Uuid): List<TimelineReminder>

    @Query("UPDATE TimelineReminderEntity SET deleted = 1 WHERE parentId = :parentId")
    suspend fun markForDeletionByParentId(parentId: Uuid)

    @Query("UPDATE TimelineReminderEntity SET deleted = 1 WHERE parentId IN (:parentIds)")
    suspend fun markForDeletionByParentIds(parentIds: List<Uuid>)
}