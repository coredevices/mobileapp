package coredevices.ring.data.entity.room

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Entity(
    indices = [
        Index("sessionId")
    ],
    foreignKeys = [
        ForeignKey(
            entity = TraceSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        )
    ]
)
data class TraceEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val sessionId: Long,
    val timeMark: Long,
    val type: String,
    val data: String?,
)

@Serializable
sealed class TraceEventData {
    @Serializable @SerialName("local_recording_upload")
    data class LocalRecordingUpload(
        val recordingId: Long,
        val firestoreId: String?,
    ) : TraceEventData()
    @Serializable @SerialName("transfer_started")
    data class TransferStarted(
        val satellite: String,
        val rollover: Boolean
    ) : TraceEventData()
    @Serializable @SerialName("transfer_type_determined")
    data class TransferTypeDetermined(
        val satellite: String,
        val isAudio: Boolean,
        val buttonSequence: String?,
        val collectionStartIndex: Int?,
        val collectionIndex: Int,
        val final: Boolean,
        val advertisementReceivedTimestamp: Instant,
        val lifetimeCollectionCount: Int?,
    ) : TraceEventData()
    @Serializable @SerialName("past_transfer_failed")
    data class PastTransferFailed(
        val satellite: String,
        val transferId: Long,
    ) : TraceEventData()
    @Serializable @SerialName("transfer_dropped_recoverable")
    data class TransferDroppedRecoverable(
        val satellite: String,
        val collectionIndex: Int
    ) : TraceEventData()
    @Serializable @SerialName("transfer_dropped_unrecoverable")
    data class TransferDroppedUnrecoverable(
        val satellite: String,
        val transferId: Long?,
        val indices: List<Int>?
    ) : TraceEventData()
    @Serializable @SerialName("transfer_progress")
    data class TransferProgress(
        val transferId: Long,
        val startIndex: Int,
        val endIndex: Int,
        val reportedProgress: Float,
    ) : TraceEventData()
    @Serializable @SerialName("transfer_completed")
    data class TransferCompleted(
        val transferId: Long,
        val audioDurationSeconds: Float,
        val buttonReleaseTimestamp: Instant?,
    ): TraceEventData()

    @Serializable @SerialName("scheduling_audio_task")
    data class SchedulingAudioTask(
        val transferId: Long,
        val buttonSequence: String?,
    ) : TraceEventData()

    @Serializable @SerialName("handling_audio_task")
    data class HandlingAudioTask(
        val transferId: Long,
    ): TraceEventData()

    @Serializable @SerialName("recording_entity_created")
    data class RecordingEntityCreated(
        val recordingId: Long,
        val transferId: Long?,
    ): TraceEventData()

    @Serializable @SerialName("transfer_id_info")
    data class TransferIdInfo(
        val transferId: Long,
    ): TraceEventData()

    @Serializable @SerialName("recording_entry_info")
    data class RecordingEntryInfo(
        val recordingEntryId: Long,
        val recordingId: Long,
        val transferId: Long,
    ): TraceEventData()
    @Serializable @SerialName("persist_recording_start")
    data class PersistRecordingStart(
        val recordingId: Long,
        val transferId: Long?,
        val fileId: String,
    ): TraceEventData()

    @Serializable @SerialName("transcription_start")
    data class TranscriptionStart(
        val recordingId: Long,
        val recordingEntryId: Long,
        val transferId: Long?,
    ): TraceEventData()

    @Serializable @SerialName("transcription_end")
    data class TranscriptionEnd(
        val recordingId: Long,
        val recordingEntryId: Long,
        val transferId: Long?,
        val transcriptLength: Int,
        val modelUsed: String?,
    ): TraceEventData()

    @Serializable @SerialName("transcription_fail")
    data class TranscriptionFail(
        val recordingId: Long,
        val recordingEntryId: Long,
        val transferId: Long?,
        val modelUsed: String?,
        val reason: String,
    ): TraceEventData()
    @Serializable @SerialName("agent_processing_start")
    data class AgentProcessingStart(
        val recordingId: Long,
        val recordingEntryId: Long?,
        val forcedToolPresent: Boolean,
        val agent: String,
    ): TraceEventData()
    @Serializable @SerialName("agent_processing_end")
    data class AgentProcessingEnd(
        val recordingId: Long,
        val recordingEntryId: Long?,
        val forcedToolUsed: Boolean,
        val agent: String,
    ): TraceEventData()
    @Serializable @SerialName("agent_processing_failed")
    data class AgentProcessingFailed(
        val recordingId: Long,
        val recordingEntryId: Long?,
        val agent: String,
        val reason: String,
    ): TraceEventData()
    @Serializable @SerialName("agent_conversation_update")
    data class AgentConversationUpdate(
        val recordingId: Long,
        val recordingEntryId: Long?,
        val messageCount: Int,
    ): TraceEventData()
}