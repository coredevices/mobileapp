package coredevices.ring.database.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import coredevices.ring.data.entity.room.CachedRecordingMetadata

@Dao
interface CachedRecordingMetadataDao {
    @Insert
    suspend fun insert(metadata: CachedRecordingMetadata): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(metadata: CachedRecordingMetadata): Long

    @Query("SELECT * FROM CachedRecordingMetadata WHERE id = :id")
    suspend fun get(id: String): CachedRecordingMetadata?
}