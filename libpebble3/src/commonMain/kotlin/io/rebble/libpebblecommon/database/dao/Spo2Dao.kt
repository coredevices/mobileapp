package io.rebble.libpebblecommon.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.rebble.libpebblecommon.database.entity.Spo2ReadingEntity

@Dao
interface Spo2Dao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSpo2Readings(readings: List<Spo2ReadingEntity>)

    @Query("SELECT * FROM spo2_readings WHERE timestamp >= :start AND timestamp < :end ORDER BY timestamp ASC")
    suspend fun getSpo2Readings(start: Long, end: Long): List<Spo2ReadingEntity>

    @Query("SELECT * FROM spo2_readings WHERE timestamp > :afterTimestamp ORDER BY timestamp ASC")
    suspend fun getSpo2ReadingsAfter(afterTimestamp: Long): List<Spo2ReadingEntity>

    @Query("SELECT * FROM spo2_readings ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestSpo2Reading(): Spo2ReadingEntity?

    @Query("SELECT MAX(timestamp) FROM spo2_readings")
    suspend fun getLatestTimestamp(): Long?

    @Query("SELECT AVG(spo2Percent) FROM spo2_readings WHERE timestamp >= :start AND timestamp < :end")
    suspend fun getAverageSpo2(start: Long, end: Long): Double?

    @Query("DELETE FROM spo2_readings WHERE timestamp < :expirationTimestamp")
    suspend fun deleteExpiredSpo2Data(expirationTimestamp: Long): Int
}
