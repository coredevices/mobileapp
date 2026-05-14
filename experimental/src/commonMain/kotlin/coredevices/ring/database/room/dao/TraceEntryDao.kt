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

    @Query("SELECT * FROM TraceEntryEntity WHERE recordingId = :recordingId ORDER BY timeMark ASC")
    suspend fun getEntriesForRecording(recordingId: Long): List<TraceEntryEntity>

    @Query("SELECT * FROM TraceEntryEntity WHERE transferId = :transferId ORDER BY timeMark ASC")
    suspend fun getEntriesForTransfer(transferId: Long): List<TraceEntryEntity>

    @Query("SELECT * FROM TraceEntryEntity WHERE sessionId = :sessionId AND timeMark < :timeMark AND type = :type ORDER BY timeMark DESC LIMIT 1")
    suspend fun getEntryBeforeTimeMarkOfType(sessionId: Long, timeMark: Long, type: String): TraceEntryEntity?

    @Query("SELECT * FROM TraceEntryEntity WHERE sessionId = :sessionId AND timeMark > :startTimeMark AND timeMark < :endTimeMark AND type = :type ORDER BY timeMark ASC LIMIT 1")
    suspend fun getEntryBetweenTimeMarksOfType(sessionId: Long, startTimeMark: Long, endTimeMark: Long, type: String): TraceEntryEntity?
}