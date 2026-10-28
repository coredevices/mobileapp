package coredevices.ring.database.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import coredevices.ring.data.entity.room.reminders.LocalReminderData
import kotlin.time.Instant

@Dao
interface LocalReminderDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(reminder: LocalReminderData)

    @Query("SELECT * FROM LocalReminderData")
    suspend fun getAllReminders(): List<LocalReminderData>

    @Query("SELECT * FROM LocalReminderData WHERE itemId = :itemId")
    suspend fun getReminder(itemId: String): LocalReminderData?

    @Query("UPDATE LocalReminderData SET notifyBeforeMillis = NULL WHERE itemId = :itemId")
    suspend fun clearNotifyBefore(itemId: String)

    @Query("UPDATE LocalReminderData SET time = :time WHERE itemId = :itemId")
    suspend fun setTime(itemId: String, time: Instant?)

    @Query("DELETE FROM LocalReminderData WHERE itemId = :itemId")
    suspend fun deleteReminder(itemId: String)
}
