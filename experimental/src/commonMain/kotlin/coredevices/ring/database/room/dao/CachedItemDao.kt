package coredevices.ring.database.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import coredevices.ring.data.entity.room.indexfeed.CachedItem
import kotlinx.coroutines.flow.Flow

@Dao
interface CachedItemDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: CachedItem)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<CachedItem>)

    @Query("SELECT * FROM CachedItem WHERE firestoreId = :id")
    suspend fun getById(id: String): CachedItem?

    @Query("SELECT * FROM CachedItem WHERE firestoreId = :id")
    fun getByIdFlow(id: String): Flow<CachedItem?>

    @Query("SELECT * FROM CachedItem WHERE deleted = 0 ORDER BY updatedAt DESC")
    fun getAllFlow(): Flow<List<CachedItem>>

    /** Includes soft-deleted rows. Used by [IndexFeedSyncService] so the
     *  `deleted = true` tombstone propagates to Firestore (and from there
     *  to other devices). Do NOT use for UI lists. */
    @Query("SELECT * FROM CachedItem ORDER BY updatedAt DESC")
    fun getAllForSyncFlow(): Flow<List<CachedItem>>

    @Query("SELECT * FROM CachedItem WHERE sourceRecordingId = :recordingId AND deleted = 0")
    fun getByRecordingFlow(recordingId: String): Flow<List<CachedItem>>

    @Query("SELECT * FROM CachedItem WHERE sourceRecordingId = :recordingId AND deleted = 0")
    suspend fun getByRecording(recordingId: String): List<CachedItem>

    /**
     * Items whose `parentListIdsCsv` contains [listId]. Uses LIKE matching with
     * boundary commas so we don't accidentally match `list_foo` when querying
     * for `list_f`. Caller filters out deleted in the query.
     */
    @Query("""
        SELECT * FROM CachedItem
        WHERE deleted = 0
          AND (',' || parentListIdsCsv || ',') LIKE '%,' || :listId || ',%'
        ORDER BY createdAt DESC
    """)
    fun getByListFlow(listId: String): Flow<List<CachedItem>>

    @Query("DELETE FROM CachedItem WHERE firestoreId = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM CachedItem")
    suspend fun deleteAll()

    @Query("SELECT firestoreId FROM CachedItem")
    suspend fun getAllIds(): List<String>
}
