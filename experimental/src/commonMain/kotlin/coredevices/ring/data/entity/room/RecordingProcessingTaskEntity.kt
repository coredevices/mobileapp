package coredevices.ring.data.entity.room

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import coredevices.indexai.data.entity.LocalRecording
import coredevices.util.queue.TaskStatus
import kotlin.time.Clock
import kotlin.time.Instant

@Entity(
    foreignKeys = [
        ForeignKey(
            entity = RingTransfer::class,
            parentColumns = ["id"],
            childColumns = ["transferId"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = LocalRecording::class,
            parentColumns = ["id"],
            childColumns = ["recordingId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index("lastAttempt"),
        Index("status"),
    ]
)
data class RecordingProcessingTaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val created: Instant = Clock.System.now(),
    val lastAttempt: Instant? = null,
    val attempts: Int = 0,
    val status: TaskStatus = TaskStatus.Pending,
    val recordingId: Long? = null,

    val type: RecordingProcessingTaskType,
    val buttonSequence: String?,
    val lastSuccessfulStage: String? = null,
    val transferId: Long?,
    val fileId: String?,
    val transcription: String?,
)

enum class RecordingProcessingTaskType {
    AudioRecording,
    LocalAudioRecording,
    TextRecording,
}