package coredevices.ring.database.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import coredevices.ring.data.entity.room.RingRssiReading
import kotlin.time.Instant

@Dao
interface RingRssiReadingDao {
    @Insert
    suspend fun insert(reading: RingRssiReading): Long

    @Query("DELETE FROM RingRssiReading WHERE ringSerialHash != :ringSerialHash")
    suspend fun deleteOtherRings(ringSerialHash: String)

    @Query("SELECT * FROM RingRssiReading WHERE timestamp >= :since ORDER BY timestamp DESC LIMIT 1")
    suspend fun latest(since: Instant): RingRssiReading?

    @Query("SELECT COUNT(*) FROM RingRssiReading WHERE timestamp >= :since")
    suspend fun count(since: Instant): Int

    @Query(
        "SELECT * FROM RingRssiReading WHERE timestamp >= :since AND phoneRssi IS NOT NULL " +
            "ORDER BY phoneRssi DESC, timestamp DESC LIMIT 1"
    )
    suspend fun maxPhoneRssi(since: Instant): RingRssiReading?

    @Query(
        "SELECT * FROM RingRssiReading WHERE timestamp >= :since AND asymmetryDb IS NOT NULL " +
            "ORDER BY asymmetryDb DESC, timestamp DESC LIMIT 1"
    )
    suspend fun maxAsymmetry(since: Instant): RingRssiReading?

    @Query(
        "SELECT * FROM RingRssiReading WHERE timestamp >= :since AND asymmetryDb IS NOT NULL " +
            "ORDER BY asymmetryDb ASC, timestamp DESC LIMIT 1"
    )
    suspend fun minAsymmetry(since: Instant): RingRssiReading?

    @Query("SELECT COUNT(*) FROM RingRssiReading WHERE timestamp >= :since AND asymmetryDb IS NOT NULL")
    suspend fun asymmetryCount(since: Instant): Int

    /** Reading at position [rank] (0-based) when ordered by ascending asymmetry. */
    @Query(
        "SELECT * FROM RingRssiReading WHERE timestamp >= :since AND asymmetryDb IS NOT NULL " +
            "ORDER BY asymmetryDb ASC, timestamp ASC LIMIT 1 OFFSET :rank"
    )
    suspend fun asymmetryAtRank(since: Instant, rank: Int): RingRssiReading?

    @Query("SELECT COUNT(*) FROM RingRssiReading WHERE timestamp >= :since AND asymmetryDb >= :threshold")
    suspend fun countAsymmetryAtLeast(since: Instant, threshold: Float): Int

    @Query("SELECT COUNT(*) FROM RingRssiReading WHERE timestamp >= :since AND asymmetryDb <= :threshold")
    suspend fun countAsymmetryAtMost(since: Instant, threshold: Float): Int
}
