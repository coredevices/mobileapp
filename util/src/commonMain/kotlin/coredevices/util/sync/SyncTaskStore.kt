package coredevices.util.sync

import coredevices.database.SyncTaskEntity
import coredevices.util.queue.TaskStatus

interface SyncTaskStore {
    suspend fun insert(task: SyncTaskEntity): Long
    suspend fun getPending(): List<SyncTaskEntity>
    suspend fun hasPending(type: String, dedupeKey: String): Boolean
    suspend fun markAttempt(id: Long, at: Long)
    suspend fun setStatus(id: Long, status: TaskStatus, error: String?)
    suspend fun delete(id: Long)
    suspend fun deleteFinishedBefore(before: Long)
}
