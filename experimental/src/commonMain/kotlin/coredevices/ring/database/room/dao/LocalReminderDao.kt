package coredevices.ring.database.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import coredevices.ring.data.entity.room.reminders.LocalReminderData
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

@Dao
interface LocalReminderDao {
    @Insert
    suspend fun insertReminder(reminder: LocalReminderData): Long

    @Query("SELECT * FROM LocalReminderData")
    suspend fun getAllReminders(): List<LocalReminderData>

    @Query("SELECT * FROM LocalReminderData WHERE time >= :start AND time <= :end")
    suspend fun getAllRemindersInRange(start: Instant, end: Instant): List<LocalReminderData>

    @Query("SELECT * FROM LocalReminderData")
    fun getAllRemindersFlow(): Flow<List<LocalReminderData>>

    @Query("SELECT * FROM LocalReminderData WHERE id = :id")
    suspend fun getReminder(id: Int): LocalReminderData?

    @Query("DELETE FROM LocalReminderData WHERE id = :id")
    suspend fun deleteReminder(id: Int)
}