package coredevices.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import coredevices.util.queue.TaskStatus
import coredevices.util.sync.SyncTaskStore

@Entity(tableName = "sync_tasks", indices = [Index("status"), Index("type", "dedupeKey")])
data class SyncTaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val payload: String,
    val createdAt: Long,
    val attempts: Int = 0,
    val lastAttemptAt: Long? = null,
    val status: TaskStatus = TaskStatus.Pending,
    val lastError: String? = null,
    val dedupeKey: String? = null,
)

@Dao
interface SyncTaskDao : SyncTaskStore {
    @Insert
    override suspend fun insert(task: SyncTaskEntity): Long

    @Query("SELECT * FROM sync_tasks WHERE status = 'Pending' ORDER BY id ASC")
    override suspend fun getPending(): List<SyncTaskEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM sync_tasks WHERE status = 'Pending' AND type = :type AND dedupeKey = :dedupeKey)")
    override suspend fun hasPending(type: String, dedupeKey: String): Boolean

    @Query("UPDATE sync_tasks SET attempts = attempts + 1, lastAttemptAt = :at WHERE id = :id")
    override suspend fun markAttempt(id: Long, at: Long)

    @Query("UPDATE sync_tasks SET status = :status, lastError = :error WHERE id = :id")
    override suspend fun setStatus(id: Long, status: TaskStatus, error: String?)

    @Query("DELETE FROM sync_tasks WHERE id = :id")
    override suspend fun delete(id: Long)

    @Query("DELETE FROM sync_tasks WHERE status != 'Pending' AND lastAttemptAt < :before")
    override suspend fun deleteFinishedBefore(before: Long)
}
