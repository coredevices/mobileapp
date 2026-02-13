package coredevices.util.queue

import kotlinx.coroutines.flow.Flow
import kotlin.time.Clock
import kotlin.time.Instant

interface QueueTaskRepository<T : QueueTask> {
    suspend fun insertTask(task: T): Long
    suspend fun getPendingTasks(): List<T>
    suspend fun updateLastSuccessfulStage(taskId: Long, stage: String)
    suspend fun updateStatus(taskId: Long, status: TaskStatus)
    suspend fun deleteTask(taskId: Long)
    suspend fun deleteCompletedTasksAttemptedBefore(before: Instant)
    suspend fun getTaskById(taskId: Long): T?
    suspend fun incrementAttempts(taskId: Long, currentTime: Instant = Clock.System.now())
}