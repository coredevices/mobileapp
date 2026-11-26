package io.rebble.libpebblecommon.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import io.rebble.libpebblecommon.database.entity.CalendarEntity
import kotlinx.coroutines.flow.Flow

@Dao
internal interface CalendarDao {
//    @Insert(onConflict = OnConflictStrategy.REPLACE)
//    suspend fun insertOrReplaceCalendars(calendars: List<CalendarEntity>)
//
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(calendar: CalendarEntity)
//
    @Query("SELECT * FROM CalendarEntity")
    suspend fun getAll(): List<CalendarEntity>
//
//    @Query("DELETE FROM CalendarEntity")
//    suspend fun deleteAll()
//
    @Update
    suspend fun update(calendar: CalendarEntity)
//
    @Delete
    suspend fun delete(calendar: CalendarEntity)

    @Query("UPDATE CalendarEntity SET enabled = :enabled WHERE id = :calendarId")
    suspend fun setEnabled(calendarId: Int, enabled: Boolean)

    @Query("UPDATE CalendarEntity SET iconCode = :iconCode WHERE id = :calendarId")
    suspend fun setIconCode(calendarId: Int, iconCode: String?)

    @Query("SELECT * FROM CalendarEntity")
    fun getFlow(): Flow<List<CalendarEntity>>
//
//    @Query("SELECT * FROM CalendarEntity WHERE id = :calendarId")
//    suspend fun get(calendarId: Long): CalendarEntity?
}