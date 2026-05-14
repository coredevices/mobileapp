package coredevices.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query

@Entity(tableName = "memfault_chunks")
data class MemfaultChunkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val serial: String,
    val chunkData: ByteArray,
    val createdAt: Long,
)

@Dao
interface MemfaultChunkDao {
    @Insert
    suspend fun insert(chunk: MemfaultChunkEntity): Long

    @Query("SELECT DISTINCT serial FROM memfault_chunks ORDER BY id ASC")
    suspend fun getPendingSerials(): List<String>

    @Query("SELECT * FROM memfault_chunks WHERE serial = :serial ORDER BY id ASC")
    suspend fun getChunksForSerial(serial: String): List<MemfaultChunkEntity>

    @Query("DELETE FROM memfault_chunks WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("SELECT COUNT(*) FROM memfault_chunks")
    suspend fun count(): Long

    @Query("DELETE FROM memfault_chunks WHERE id IN (SELECT id FROM memfault_chunks ORDER BY id ASC LIMIT :count)")
    suspend fun deleteOldest(count: Long)
}
