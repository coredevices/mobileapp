@file:OptIn(ExperimentalTime::class)

package coredevices.indexai.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import coredevices.indexai.data.NoteMetadata
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.InstantComponentSerializer
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant


@Entity(
    indices = [
        Index(value = ["firestoreId"], unique = true),
        Index(value = ["localTimestamp"]),
    ]
)
data class LocalRecording(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val localTimestamp: Instant = Clock.System.now(),
    val firestoreId: String? = null,
    @ColumnInfo(defaultValue = "0")
    val updated: Instant = Clock.System.now(),
    val assistantTitle: String? = null,
) {
    fun toDocument(
        entries: List<RecordingEntry>,
        messages: List<ConversationMessageDocument>,
        metadata: NoteMetadata? = null
    ): RecordingDocument {
        return RecordingDocument(
            timestamp = localTimestamp,
            updated = updated.toEpochMilliseconds(),
            entries = entries,
            assistantSession = AssistantSessionDocument(title = assistantTitle, messages = messages),
            metadata = metadata
        )
    }
}

@Entity(
    foreignKeys = [
        ForeignKey(
            entity = LocalRecording::class,
            parentColumns = ["id"],
            childColumns = ["recordingId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["userMessageId"]),
        Index(value = ["recordingId"]),
        Index(value = ["recordingId", "timestamp"])
    ]
)
data class RecordingEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recordingId: Long,
    val timestamp: Instant = Clock.System.now(),
    /**
     * The file name of the recording in Firebase Storage (under the user's recordings directory).
     */
    val fileName: String? = null,
    /**
     * The status of the recording entry. See [RecordingEntryStatus].
     */
    val status: RecordingEntryStatus = RecordingEntryStatus.pending,
    val transcription: String? = null,
    @ColumnInfo(defaultValue = "NULL")
    val transcribedUsingModel: String? = null,
    val error: String? = null,
    val ringTransferInfo: RingTransferInfo? = null,
    val userMessageId: Long? = null
)

@Serializable
data class RecordingDocument(
    @Serializable(with = InstantComponentSerializer::class)
    val timestamp: Instant = Clock.System.now(),
    val updated: Long = Clock.System.now().toEpochMilliseconds(),
    /**
     * List of entries in the recording. First entry is the initial recording, subsequent entries are
     * the result of further user voice responses if any.
     */
    val entries: List<RecordingEntry> = emptyList(),
    /**
     * The assistant session associated with this recording.
     */
    @SerialName("assistant_session")
    val assistantSession: AssistantSessionDocument? = null,
    val metadata: NoteMetadata? = null
) {
    fun firstUserMessage(): String {
        return assistantSession?.messages?.firstOrNull { it.role == MessageRole.user }?.content
            ?: entries.firstOrNull()?.transcription ?: ""
    }

    fun firstAssistantMessage(): ConversationMessageDocument? {
        return assistantSession?.messages?.firstOrNull { it.role == MessageRole.assistant }
    }

    fun toolCallFor(toolCallId: String): ConversationMessageDocument? {
        return assistantSession?.messages?.firstOrNull { msg ->
            msg.role == MessageRole.tool && msg.tool_call_id == toolCallId
        }
    }

    fun firstRecording(): RecordingEntry? {
        return entries.firstOrNull()
    }
}

@Serializable
data class RecordingEntry(
    @Serializable(with = InstantComponentSerializer::class)
    val timestamp: Instant,
    /**
     * The file name of the recording in Firebase Storage (under the user's recordings directory).
     */
    val fileName: String? = null,
    /**
     * The status of the recording entry. See [RecordingEntryStatus].
     */
    val status: RecordingEntryStatus = RecordingEntryStatus.pending,
    val transcription: String? = null,
    val transcribedUsingModel: String? = null,
    val error: String? = null,
    val ringTransferInfo: RingTransferInfo? = null,
    val userMessageId: Long? = null,
)

@Suppress("EnumEntryName")
enum class RecordingEntryStatus {
    /**
     * Used to indicate a recording that has been initiated by the user but may be in progress or failed to reach transcription.
     */
    pending,
    agent_processing,
    /**
     * Used to indicate a recording has been transcribed and processed, e.g. becoming an assistant message in the session.
     */
    completed,
    transcription_error,
    agent_error;

    fun isError(): Boolean = this == transcription_error || this == agent_error
}