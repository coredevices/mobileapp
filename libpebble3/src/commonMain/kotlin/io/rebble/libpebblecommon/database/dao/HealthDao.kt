package io.rebble.libpebblecommon.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.rebble.libpebblecommon.database.entity.HealthDataEntity
import io.rebble.libpebblecommon.database.entity.OverlayDataEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HealthDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHealthData(data: List<HealthDataEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOverlayData(data: List<OverlayDataEntity>)

    @Query(
            "SELECT * FROM health_data WHERE timestamp >= :start AND timestamp <= :end ORDER BY timestamp ASC"
    )
    fun getHealthData(start: Long, end: Long): Flow<List<HealthDataEntity>>

    @Query(
            "SELECT * FROM overlay_data WHERE startTime >= :start AND startTime <= :end ORDER BY startTime ASC"
    )
    fun getOverlayData(start: Long, end: Long): Flow<List<OverlayDataEntity>>

    @Query("SELECT SUM(steps) FROM health_data WHERE timestamp >= :start AND timestamp <= :end")
    suspend fun getTotalSteps(start: Long, end: Long): Int?

    @Query("SELECT AVG(steps) FROM health_data WHERE timestamp >= :start AND timestamp <= :end")
    suspend fun getAverageSteps(start: Long, end: Long): Double?

    @Query("SELECT COUNT(*) FROM health_data WHERE timestamp >= :start AND timestamp <= :end")
    suspend fun hasDataForRange(start: Long, end: Long): Int

    @Query("SELECT COUNT(*) FROM health_data")
    suspend fun hasAnyHealthData(): Int

    @Query("SELECT MAX(timestamp) FROM health_data")
    suspend fun getLatestTimestamp(): Long?

    @Query("SELECT * FROM health_data WHERE timestamp = :timestamp")
    suspend fun getDataAtTimestamp(timestamp: Long): HealthDataEntity?
}
