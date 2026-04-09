package coredevices.ring.database.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import coredevices.ring.data.entity.room.TraceSessionEntity

@Dao
interface TraceSessionDao {
    @Insert
    suspend fun insertTraceSession(traceSession: TraceSessionEntity): Long

    @Query("SELECT * FROM TraceSessionEntity WHERE id = :id")
    suspend fun getTraceSessionById(id: Long): TraceSessionEntity?

    @Query("SELECT * FROM TraceSessionEntity ORDER BY started DESC LIMIT :limit OFFSET :offset")
    suspend fun getLastNTraceSessions(limit: Int, offset: Int): List<TraceSessionEntity>
}