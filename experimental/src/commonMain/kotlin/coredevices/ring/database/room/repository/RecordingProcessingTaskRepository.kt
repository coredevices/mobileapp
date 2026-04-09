package coredevices.ring.database.room.repository

import coredevices.ring.data.ProcessingTask
import coredevices.ring.data.RecordingProcessingTask
import coredevices.ring.data.entity.room.RecordingProcessingTaskEntity
import coredevices.ring.data.entity.room.RecordingProcessingTaskType
import coredevices.ring.database.room.dao.RecordingProcessingTaskDao
import coredevices.util.queue.QueueTaskRepository
import coredevices.util.queue.TaskStatus
import kotlinx.coroutines.flow.Flow
import kotlin.time.Clock
import kotlin.time.Instant

class RecordingProcessingTaskRepository(
    private val dao: RecordingProcessingTaskDao
): QueueTaskRepository<RecordingProcessingTask> {

    private fun RecordingProcessingTask.toEntity(): RecordingProcessingTaskEntity {
        when (task) {
            is ProcessingTask.AudioRecording -> {
                return RecordingProcessingTaskEntity(
                    id = id,
                    created = created,
                    lastAttempt = lastAttempt,
                    attempts = attempts,
                    status = status,
                    lastSuccessfulStage = lastSuccessfulStage,
                    type = RecordingProcessingTaskType.AudioRecording,
                    buttonSequence = task.buttonSequence,
                    transferId = task.transferId,
                    fileId = null,
                    transcription = null,
                )
            }
            is ProcessingTask.LocalAudioRecording -> {
                return RecordingProcessingTaskEntity(
                    id = id,
                    created = created,
                    lastAttempt = lastAttempt,
                    attempts = attempts,
                    status = status,
                    lastSuccessfulStage = lastSuccessfulStage,
                    type = RecordingProcessingTaskType.LocalAudioRecording,
                    buttonSequence = task.buttonSequence,
                    transferId = null,
                    fileId = task.fileId,
                    transcription = null,
                )
            }
            is ProcessingTask.TextRecording -> {
                return RecordingProcessingTaskEntity(
                    id = id,
                    created = created,
                    lastAttempt = lastAttempt,
                    attempts = attempts,
                    status = status,
                    lastSuccessfulStage = lastSuccessfulStage,
                    type = RecordingProcessingTaskType.TextRecording,
                    buttonSequence = null,
                    transferId = null,
                    fileId = null,
                    transcription = task.transcription,
                )
            }
        }
    }

    private fun RecordingProcessingTaskEntity.toDomain(): RecordingProcessingTask {
        when (type) {
            RecordingProcessingTaskType.AudioRecording -> {
                return RecordingProcessingTask(
                    id = id,
                    created = created,
                    lastAttempt = lastAttempt,
                    attempts = attempts,
                    status = status,
                    lastSuccessfulStage = lastSuccessfulStage,
                    task = ProcessingTask.AudioRecording(
                        buttonSequence = buttonSequence,
                        transferId = transferId ?: error("transferId is null for AudioRecording task"),
                        created = created,
                    ),
                )
            }
            RecordingProcessingTaskType.LocalAudioRecording -> {
                return RecordingProcessingTask(
                    id = id,
                    created = created,
                    lastAttempt = lastAttempt,
                    attempts = attempts,
                    status = status,
                    lastSuccessfulStage = lastSuccessfulStage,
                    task = ProcessingTask.LocalAudioRecording(
                        fileId = fileId ?: error("fileId is null for LocalAudioRecording task"),
                        buttonSequence = buttonSequence,
                        created = created,
                    ),
                )
            }
            RecordingProcessingTaskType.TextRecording -> {
                return RecordingProcessingTask(
                    id = id,
                    created = created,
                    lastAttempt = lastAttempt,
                    attempts = attempts,
                    status = status,
                    lastSuccessfulStage = lastSuccessfulStage,
                    task = ProcessingTask.TextRecording(
                        transcription = transcription ?: error("transcription is null for TextRecording task"),
                        created = created,
                    ),
                )
            }
        }
    }

    override suspend fun insertTask(task: RecordingProcessingTask): Long {
        val entity = task.toEntity()
        return dao.insertTask(entity)
    }

    override suspend fun getPendingTasks(): List<RecordingProcessingTask> {
        return dao.getPendingTasks().map { it.toDomain() }
    }

    override suspend fun updateLastSuccessfulStage(taskId: Long, stage: String) {
        dao.updateLastSuccessfulStage(taskId, stage)
    }

    suspend fun updateTaskRecordingId(taskId: Long, recordingId: Long) {
        dao.updateTaskRecordingId(taskId, recordingId)
    }

    override suspend fun updateStatus(
        taskId: Long,
        status: TaskStatus
    ) {
        dao.updateTaskStatus(taskId, status)
    }

    override suspend fun deleteTask(taskId: Long) {
        dao.deleteTask(taskId)
    }

    override suspend fun deleteCompletedTasksAttemptedBefore(before: Instant) {
        dao.deleteCompletedTasksAttemptedBefore(before)
    }

    override suspend fun getTaskById(taskId: Long): RecordingProcessingTask? {
        return dao.getTaskById(taskId)?.toDomain()
    }

    override suspend fun incrementAttempts(taskId: Long, currentTime: Instant) {
        dao.incrementAttempts(taskId, currentTime)
    }
}