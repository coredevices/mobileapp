package coredevices.ring.database.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import coredevices.ring.data.entity.room.TraceEntryEntity

@Dao
interface TraceEntryDao {
    @Insert
    suspend fun insertTraceEntry(traceEntry: TraceEntryEntity): Long

    @Insert
    suspend fun insertAll(traceEntries: List<TraceEntryEntity>): List<Long>

    @Query("SELECT * FROM TraceEntryEntity WHERE sessionId = :sessionId ORDER BY timeMark ASC")
    suspend fun getEntriesForSession(sessionId: Long): List<TraceEntryEntity>
}