package coredevices.ring.database.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import coredevices.ring.data.entity.room.clock.LocalClockEntry
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

@Dao
interface LocalClockDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: LocalClockEntry)

    @Query("SELECT * FROM LocalClockEntry ORDER BY fireAt")
    fun getAllFlow(): Flow<List<LocalClockEntry>>

    @Query("SELECT * FROM LocalClockEntry ORDER BY fireAt")
    suspend fun getAll(): List<LocalClockEntry>

    @Query("DELETE FROM LocalClockEntry WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM LocalClockEntry WHERE fireAt <= :before")
    suspend fun deleteFiredBefore(before: Instant)
}
