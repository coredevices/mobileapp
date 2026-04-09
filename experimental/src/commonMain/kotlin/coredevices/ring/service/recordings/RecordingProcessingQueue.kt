package coredevices.ring.service.recordings

import co.touchlab.kermit.Logger
import coredevices.indexai.util.JsonSnake
import coredevices.mcp.data.ToolCallResult
import coredevices.ring.agent.builtin_servlets.notes.CreateNoteTool
import coredevices.ring.data.ProcessingTask
import coredevices.ring.data.RecordingProcessingTask
import coredevices.ring.data.entity.room.TraceEventData
import coredevices.ring.database.room.repository.RecordingProcessingTaskRepository
import coredevices.ring.database.room.repository.RecordingRepository
import coredevices.ring.database.room.repository.RingTransferRepository
import coredevices.ring.service.RecordingBackgroundScope
import coredevices.ring.service.parseAsButtonSequence
import coredevices.ring.service.recordings.button.RecordingOperationFactory
import coredevices.ring.storage.RecordingStorage
import coredevices.ring.util.trace.RingTraceSession
import coredevices.util.queue.PersistentQueueScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class RecordingProcessingQueue(
    private val recordingStorage: RecordingStorage,
    private val transferRepository: RingTransferRepository,
    private val recordingRepository: RecordingRepository,
    private val queueTaskRepository: RecordingProcessingTaskRepository,
    private val recordingOperationFactory: RecordingOperationFactory,
    private val scope: RecordingBackgroundScope,
    private val recordingPreprocessor: RecordingPreprocessor,
    private val trace: RingTraceSession,
    rescheduleDelay: Duration = 1.minutes,
    maxConcurrency: Int = 20,
): KoinComponent, PersistentQueueScheduler<RecordingProcessingTask>(
    repository = queueTaskRepository,
    scope = scope,
    label = "RecordingProcessing",
    rescheduleDelay = rescheduleDelay,
    maxConcurrency = maxConcurrency,
) {
    companion object {
        private val logger = Logger.withTag("RecordingProcessingQueue")
    }

    override suspend fun processTask(task: RecordingProcessingTask) {
        val taskData = task.task
        val stage = task.lastSuccessfulStage?.let { RecordingProcessingStage.fromJson(it) }
        val handle = TaskHandle(task.id, stage)
        when (taskData) {
            is ProcessingTask.AudioRecording -> handleRecording(handle, taskData)
            is ProcessingTask.LocalAudioRecording -> handleRecording(handle, taskData)
            is ProcessingTask.TextRecording -> handleChat(handle, taskData)
        }
    }

    private suspend fun forcedNoteTool(messageText: String): ToolCallResult {
        val noteTool: CreateNoteTool = get()
        return noteTool.call(
            JsonSnake.encodeToString(
                JsonSnake.encodeToJsonElement(
                    CreateNoteTool.CreateNoteArgs(
                        text = messageText,
                        automatic = true
                    )
                ).jsonObject
            )
        )
    }

    private suspend fun handleRecording(
        handle: TaskHandle,
        recordingId: Long,
        fileId: String,
        transferId: Long?,
        buttonSequence: String?
    ) {
        try {
            trace.markEvent("recording_preprocessing_start", TraceEventData.TransferIdInfo(transferId ?: -1))
            recordingPreprocessor.preprocess(fileId)
            trace.markEvent("recording_preprocessing_end", TraceEventData.TransferIdInfo(transferId ?: -1))
        } catch (e: Exception) {
            logger.e(e) { "Preprocessing failed for file $fileId: ${e.message}, skipping preprocessing" }
        }
        val operation = recordingOperationFactory.createForButtonSequence(
            recordingId = recordingId,
            fileId = fileId,
            transferId = transferId,
            forcedNoteTool = ::forcedNoteTool,
            sequence = buttonSequence?.parseAsButtonSequence()
        )
        operation.run(handle)
    }

    private suspend fun handleRecording(handle: TaskHandle, task: ProcessingTask.LocalAudioRecording) {
        val (fileId, buttonSequence) = task
        logger.v { "Handling local recording $fileId" }
        val recordingId = if (handle.stage is RecordingProcessingStage.RecordingEntityCreated) {
            (handle.stage as RecordingProcessingStage.RecordingEntityCreated).recordingEntityId
        } else {
            val id = recordingRepository.createRecording()
            handle.updateStage(
                RecordingProcessingStage.RecordingEntityCreated(id)
            )
            id
        }
        handleRecording(
            handle,
            recordingId,
            fileId = fileId,
            transferId = null,
            buttonSequence = buttonSequence
        )
    }

    private suspend fun handleRecording(handle: TaskHandle, task: ProcessingTask.AudioRecording) {
        val (buttonSequence, transferId) = task
        logger.v { "Handling transfer $transferId" }
        trace.markEvent("handling_audio_task_start", TraceEventData.HandlingAudioTask(transferId))
        val transfer = transferRepository.getRingTransferById(transferId)
            ?: throw IllegalStateException("Transfer $transferId not found")
        val fileId = transfer.fileId
            ?: throw IllegalStateException("Transfer $transferId has no associated fileId")
        val recordingId = if (handle.stage is RecordingProcessingStage.RecordingEntityCreated) {
            (handle.stage as RecordingProcessingStage.RecordingEntityCreated).recordingEntityId
        } else {
            val id = recordingRepository.createRecording(
                localTimestamp = transfer.transferInfo?.buttonPressed?.let { Instant.fromEpochMilliseconds(it) } ?: task.created
            )
            queueTaskRepository.updateTaskRecordingId(
                taskId = handle.taskId,
                recordingId = id
            )
            trace.markEvent("recording_entity_created", TraceEventData.RecordingEntityCreated(
                recordingId = id,
                transferId = transferId
            ))
            handle.updateStage(
                RecordingProcessingStage.RecordingEntityCreated(id)
            )
            id
        }
        transferRepository.linkRecordingToTransfer(
            transferId = transferId,
            recordingId = recordingId
        )
        handleRecording(
            handle = handle,
            recordingId = recordingId,
            fileId = fileId,
            transferId = transferId,
            buttonSequence = buttonSequence
        )
        trace.markEvent("handling_audio_task_end", TraceEventData.HandlingAudioTask(transferId))
    }

    private suspend fun handleChat(
        handle: TaskHandle,
        task: ProcessingTask.TextRecording
    ) {
        val (transcription) = task
        logger.v { "Handling text recording" }
        val recordingId = if (handle.stage is RecordingProcessingStage.RecordingEntityCreated) {
            (handle.stage as RecordingProcessingStage.RecordingEntityCreated).recordingEntityId
        } else {
            val id = recordingRepository.createRecording()
            handle.updateStage(
                RecordingProcessingStage.RecordingEntityCreated(id)
            )
            id
        }
        val operation = recordingOperationFactory.createTextOnlyOperation(
            recordingId = recordingId,
            text = transcription,
            forcedTool = { forcedNoteTool(transcription) }
        )
        operation.run(handle)
    }

    private suspend fun scheduleTask(task: RecordingProcessingTask): Long {
        val id = withContext(Dispatchers.IO) {
            queueTaskRepository.insertTask(task)
        }
        super.scheduleTask(id)
        return id
    }

    /**
     * Queues an audio processing task.
     * @return A deferred that completes with the created recording entry ID, or null if none was created/failure.
     */
    suspend fun queueAudioProcessing(
        transferId: Long,
        buttonSequence: String?,
    ) {
        val task = ProcessingTask.AudioRecording(
            transferId = transferId,
            buttonSequence = buttonSequence,
        )
        trace.markEvent("scheduling_audio_task",
            TraceEventData.SchedulingAudioTask(transferId, buttonSequence)
        )
        scheduleTask(
            RecordingProcessingTask(
                task = task
            )
        )
    }

    /**
     * Queues an audio processing task.
     * @return A deferred that completes with the created recording entry ID, or null if none was created/failure.
     */
    suspend fun queueLocalAudioProcessing(
        fileId: String,
        buttonSequence: String? = null,
    ) {
        val task = ProcessingTask.LocalAudioRecording(
            fileId = fileId,
            buttonSequence = buttonSequence,
        )
        trace.markEvent("scheduling_local_audio_task")
        scheduleTask(
            RecordingProcessingTask(
                task = task
            )
        )
    }

    suspend fun queueTextProcessing(
        transcription: String
    ) {
        val task = ProcessingTask.TextRecording(
            transcription = transcription
        )
        trace.markEvent("scheduling_text_task")
        scheduleTask(
            RecordingProcessingTask(
                task = task
            )
        )
    }

    inner class TaskHandle(val taskId: Long, initialStage: RecordingProcessingStage?) {
        val stage: RecordingProcessingStage? get() = _stage
        private var _stage: RecordingProcessingStage? = initialStage
        suspend fun updateStage(newStage: RecordingProcessingStage) {
            _stage = newStage
            val stageString = newStage.toJson()
            withContext(Dispatchers.IO) {
                queueTaskRepository.updateLastSuccessfulStage(taskId, stageString)
            }
        }
    }
}

@Serializable
sealed interface RecordingProcessingStageJson {
    @Serializable
    data class RecordingEntityCreated(val recordingEntityId: Long): RecordingProcessingStageJson
    @Serializable
    data class RecordingEntryCreated(val recordingEntryId: Long, val recordingEntityId: Long): RecordingProcessingStageJson
}

fun RecordingProcessingStage.toJson(): String {
    return Json.encodeToString(
        // Remember this will return first matching type, so subsequent types should be earlier
        when (this) {
            is RecordingProcessingStage.RecordingEntryCreated -> RecordingProcessingStageJson.RecordingEntryCreated(
                recordingEntryId = this.recordingEntryId,
                recordingEntityId = this.recordingEntityId
            )
            is RecordingProcessingStage.RecordingEntityCreated -> RecordingProcessingStageJson.RecordingEntityCreated(
                recordingEntityId = this.recordingEntityId
            )
        }
    )
}

sealed interface RecordingProcessingStage {
    open class RecordingEntityCreated(val recordingEntityId: Long) : RecordingProcessingStage
    open class RecordingEntryCreated : RecordingEntityCreated {
        val recordingEntryId: Long
        constructor(recordingEntryId: Long, previous: RecordingEntityCreated): super(previous.recordingEntityId) {
            this.recordingEntryId = recordingEntryId
        }
        constructor(recordingEntryId: Long, recordingEntityId: Long): super(recordingEntityId) {
            this.recordingEntryId = recordingEntryId
        }
    }

    companion object {
        fun fromJson(json: String): RecordingProcessingStage {
            val jsonElement = Json.decodeFromString<RecordingProcessingStageJson>(json)
            return when (jsonElement) {
                is RecordingProcessingStageJson.RecordingEntityCreated -> RecordingEntityCreated(
                    recordingEntityId = jsonElement.recordingEntityId
                )
                is RecordingProcessingStageJson.RecordingEntryCreated -> RecordingEntryCreated(
                    recordingEntryId = jsonElement.recordingEntryId,
                    recordingEntityId = jsonElement.recordingEntityId
                )
            }
        }
    }
}