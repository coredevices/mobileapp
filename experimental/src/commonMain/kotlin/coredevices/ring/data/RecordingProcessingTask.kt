package coredevices.ring.data

import coredevices.util.queue.QueueTask
import coredevices.util.queue.TaskStatus
import kotlinx.coroutines.CompletableDeferred
import kotlin.time.Clock
import kotlin.time.Instant

data class RecordingProcessingTask(
    override val id: Long = 0,
    override val created: Instant = Clock.System.now(),
    override val lastAttempt: Instant? = null,
    override val attempts: Int = 0,
    override val status: TaskStatus = TaskStatus.Pending,

    val lastSuccessfulStage: String? = null,
    val task: ProcessingTask,
) : QueueTask

sealed interface ProcessingTask {
    val created: Instant
    data class AudioRecording(
        val buttonSequence: String?,
        val transferId: Long,
        override val created: Instant = Clock.System.now()
    ) : ProcessingTask
    data class LocalAudioRecording(
        val fileId: String,
        val buttonSequence: String?,
        override val created: Instant = Clock.System.now()
    ) : ProcessingTask
    data class TextRecording(
        val transcription: String,
        override val created: Instant = Clock.System.now()
    ) : ProcessingTask
}