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

    @Query("SELECT * FROM health_data WHERE timestamp >= :start AND timestamp <= :end ORDER BY timestamp ASC")
    fun getHealthData(start: Long, end: Long): Flow<List<HealthDataEntity>>

    @Query("SELECT * FROM overlay_data WHERE startTime >= :start AND startTime <= :end ORDER BY startTime ASC")
    fun getOverlayData(start: Long, end: Long): Flow<List<OverlayDataEntity>>

    @Query("SELECT SUM(steps) FROM health_data WHERE timestamp >= :start AND timestamp <= :end")
    suspend fun getTotalSteps(start: Long, end: Long): Int?

    @Query("""
        SELECT 
            SUM(steps) AS steps,
            SUM(activeGramCalories) AS activeGramCalories,
            SUM(restingGramCalories) AS restingGramCalories,
            SUM(activeMinutes) AS activeMinutes,
            SUM(distanceCm) AS distanceCm
        FROM health_data
        WHERE timestamp >= :start AND timestamp < :end
        """)
    suspend fun getAggregatedHealthData(start: Long, end: Long): HealthAggregates?

    @Query("SELECT SUM(steps) FROM health_data WHERE timestamp >= :start AND timestamp < :end")
    suspend fun getTotalStepsExclusiveEnd(start: Long, end: Long): Long?

    @Query("SELECT AVG(steps) FROM health_data WHERE timestamp >= :start AND timestamp <= :end")
    suspend fun getAverageSteps(start: Long, end: Long): Double?

    @Query("SELECT AVG(heartRate) FROM health_data WHERE timestamp >= :start AND timestamp < :end AND heartRate > 0")
    suspend fun getAverageHeartRate(start: Long, end: Long): Double?

    @Query("SELECT COUNT(*) FROM health_data WHERE timestamp >= :start AND timestamp <= :end")
    suspend fun hasDataForRange(start: Long, end: Long): Int

    @Query("SELECT COUNT(*) FROM health_data") suspend fun hasAnyHealthData(): Int

    @Query("SELECT MAX(timestamp) FROM health_data") suspend fun getLatestTimestamp(): Long?

    @Query("SELECT * FROM health_data WHERE timestamp = :timestamp")
    suspend fun getDataAtTimestamp(timestamp: Long): HealthDataEntity?

    @Query("SELECT SUM(duration) FROM overlay_data WHERE startTime >= :start AND startTime < :end AND type = :type")
    suspend fun getOverlayDuration(start: Long, end: Long, type: Int): Long?

    @Query("""
        SELECT * FROM overlay_data
        WHERE startTime >= :start AND startTime < :end AND type IN (:types)
        """)
    suspend fun getOverlayEntries(
            start: Long,
            end: Long,
            types: List<Int>
    ): List<OverlayDataEntity>

    @Query("SELECT * FROM overlay_data ORDER BY startTime ASC")
    suspend fun getAllOverlayEntries(): List<OverlayDataEntity>

    @Query("SELECT * FROM overlay_data WHERE startTime = :startTime AND type = :type")
    suspend fun getOverlayAtStartTimeAndType(startTime: Long, type: Int): OverlayDataEntity?

    @Query("""
        SELECT SUM(duration) / 60 FROM overlay_data
        WHERE startTime >= :start AND startTime < :end AND type = 1
        """)
    suspend fun getTotalSleepMinutes(start: Long, end: Long): Long?

    @Query("""
        SELECT SUM(duration) / 60 FROM overlay_data
        WHERE startTime >= :start AND startTime < :end AND type = 2
        """)
    suspend fun getDeepSleepMinutes(start: Long, end: Long): Long?

    @Query("""
        SELECT COUNT(DISTINCT DATE(startTime, 'unixepoch', 'localtime')) FROM overlay_data
        WHERE startTime >= :start AND startTime < :end AND type IN (1, 2) AND duration > 0
        """)
    suspend fun getDaysWithSleepData(start: Long, end: Long): Int

    @Query("""
        SELECT COUNT(DISTINCT DATE(timestamp, 'unixepoch', 'localtime')) FROM health_data
        WHERE timestamp >= :start AND timestamp < :end AND steps > 0
        """)
    suspend fun getDaysWithStepsData(start: Long, end: Long): Int

    @Query("""
        SELECT
            date(timestamp, 'unixepoch', 'localtime') as day,
            SUM(steps) AS steps,
            SUM(activeGramCalories) AS activeGramCalories,
            SUM(restingGramCalories) AS restingGramCalories,
            SUM(activeMinutes) AS activeMinutes,
            SUM(distanceCm) AS distanceCm
        FROM health_data
        WHERE timestamp >= :start AND timestamp < :end
        GROUP BY day
        """)
    suspend fun getDailyMovementAggregates(start: Long, end: Long): List<DailyMovementAggregate>

    @Query("DELETE FROM health_data WHERE timestamp < :expirationTimestamp")
    suspend fun deleteExpiredHealthData(expirationTimestamp: Long): Int

    @Query("DELETE FROM overlay_data WHERE startTime < :expirationTimestamp")
    suspend fun deleteExpiredOverlayData(expirationTimestamp: Long): Int
}

data class HealthAggregates(
    val steps: Long?,
    val activeGramCalories: Long?,
    val restingGramCalories: Long?,
    val activeMinutes: Long?,
    val distanceCm: Long?,
)

data class DailyMovementAggregate(
    val day: String, // YYYY-MM-DD
    val steps: Long?,
    val activeGramCalories: Long?,
    val restingGramCalories: Long?,
    val activeMinutes: Long?,
    val distanceCm: Long?
)
