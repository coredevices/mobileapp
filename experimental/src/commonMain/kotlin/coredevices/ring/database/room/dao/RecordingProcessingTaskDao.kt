package coredevices.ring.database.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import coredevices.ring.data.entity.room.RecordingProcessingTaskEntity
import coredevices.util.queue.TaskStatus
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

@Dao
interface RecordingProcessingTaskDao {
    @Insert
    suspend fun insertTask(task: RecordingProcessingTaskEntity): Long

    @Query("SELECT * FROM RecordingProcessingTaskEntity WHERE status = 'Pending' ORDER BY created ASC")
    suspend fun getPendingTasks(): List<RecordingProcessingTaskEntity>

    @Query("UPDATE RecordingProcessingTaskEntity SET lastSuccessfulStage = :stage WHERE id = :taskId")
    suspend fun updateLastSuccessfulStage(taskId: Long, stage: String)

    @Query("UPDATE RecordingProcessingTaskEntity SET status = :status WHERE id = :taskId")
    suspend fun updateTaskStatus(taskId: Long, status: TaskStatus)

    @Query("DELETE FROM RecordingProcessingTaskEntity WHERE id = :taskId")
    suspend fun deleteTask(taskId: Long)

    @Query("DELETE FROM RecordingProcessingTaskEntity WHERE status != 'Pending' AND lastAttempt IS NOT NULL and lastAttempt < :before")
    suspend fun deleteCompletedTasksAttemptedBefore(before: Instant)

    @Query("SELECT * FROM RecordingProcessingTaskEntity WHERE id = :taskId LIMIT 1")
    suspend fun getTaskById(taskId: Long): RecordingProcessingTaskEntity?

    @Query("SELECT * FROM RecordingProcessingTaskEntity WHERE id = :taskId LIMIT 1")
    fun getTaskByIdFlow(taskId: Long): Flow<RecordingProcessingTaskEntity?>

    @Query("UPDATE RecordingProcessingTaskEntity SET attempts = attempts + 1, lastAttempt = :currentTime WHERE id = :taskId")
    suspend fun incrementAttempts(taskId: Long, currentTime: Instant)

    @Query("UPDATE RecordingProcessingTaskEntity SET recordingId = :recordingId WHERE id = :taskId")
    suspend fun updateTaskRecordingId(taskId: Long, recordingId: Long)
}