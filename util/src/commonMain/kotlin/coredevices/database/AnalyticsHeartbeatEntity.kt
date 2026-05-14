package coredevices.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query

@Entity(tableName = "analytics_heartbeats")
data class AnalyticsHeartbeatEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val serial: String,
    val fwVersion: String?,
    val tzOffsetMinutes: Int,
    val payload: ByteArray,
    val createdAt: Long,
)

@Dao
interface AnalyticsHeartbeatDao {
    @Insert
    suspend fun insert(row: AnalyticsHeartbeatEntity): Long

    @Query("SELECT * FROM analytics_heartbeats ORDER BY id ASC LIMIT :limit")
    suspend fun getBatch(limit: Int): List<AnalyticsHeartbeatEntity>

    @Query("DELETE FROM analytics_heartbeats WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("SELECT COUNT(*) FROM analytics_heartbeats")
    suspend fun count(): Long

    @Query("DELETE FROM analytics_heartbeats WHERE id IN (SELECT id FROM analytics_heartbeats ORDER BY id ASC LIMIT :count)")
    suspend fun deleteOldest(count: Long)
}
