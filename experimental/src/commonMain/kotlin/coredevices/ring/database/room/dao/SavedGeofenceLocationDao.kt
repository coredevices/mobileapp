package coredevices.ring.database.room.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import coredevices.ring.data.entity.room.SavedGeofenceLocation
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Dao
interface SavedGeofenceLocationDao {
    // REPLACE would delete the row and null out LocalReminderData.geofenceLocationId.
    @Upsert
    suspend fun insertUpdate(entity: SavedGeofenceLocation)

    @Query("UPDATE SavedGeofenceLocation SET deleted = 1, updatedAt = :now WHERE id = :uuid")
    suspend fun markDeleted(uuid: Uuid, now: Instant)

    @Query("DELETE FROM SavedGeofenceLocation WHERE id = :uuid")
    suspend fun deleteById(uuid: Uuid)

    /** Includes tombstones. */
    @Query("SELECT * FROM SavedGeofenceLocation WHERE id = :uuid")
    suspend fun getById(uuid: Uuid): SavedGeofenceLocation?

    @Query("SELECT * FROM SavedGeofenceLocation WHERE deleted = 0")
    fun getAllFlow(): Flow<List<SavedGeofenceLocation>>

    /** Includes tombstones, so deletes propagate to Firestore. */
    @Query("SELECT * FROM SavedGeofenceLocation")
    fun getAllForSyncFlow(): Flow<List<SavedGeofenceLocation>>
}
