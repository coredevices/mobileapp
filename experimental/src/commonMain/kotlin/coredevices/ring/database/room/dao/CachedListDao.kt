package coredevices.ring.database.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import coredevices.ring.data.entity.room.indexfeed.CachedList
import kotlinx.coroutines.flow.Flow

@Dao
interface CachedListDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(list: CachedList)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(lists: List<CachedList>)

    @Query("SELECT * FROM CachedList WHERE firestoreId = :id")
    suspend fun getById(id: String): CachedList?

    @Query("SELECT * FROM CachedList WHERE firestoreId = :id")
    fun getByIdFlow(id: String): Flow<CachedList?>

    @Query("SELECT * FROM CachedList WHERE deleted = 0 ORDER BY updatedAt DESC")
    fun getAllFlow(): Flow<List<CachedList>>

    /** Includes soft-deleted rows. Used by [IndexFeedSyncService] so the
     *  `deleted = true` tombstone propagates to Firestore (and from there
     *  to other devices). Do NOT use for UI lists. */
    @Query("SELECT * FROM CachedList ORDER BY updatedAt DESC")
    fun getAllForSyncFlow(): Flow<List<CachedList>>

    @Query("SELECT * FROM CachedList WHERE seed = :seed LIMIT 1")
    suspend fun getBySeed(seed: String): CachedList?

    @Query("SELECT count(*) FROM CachedList")
    suspend fun count(): Int

    @Query("DELETE FROM CachedList WHERE firestoreId = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM CachedList")
    suspend fun deleteAll()
}
