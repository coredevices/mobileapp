package coredevices.indexai.database.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import coredevices.indexai.data.entity.ConversationMessageEntity
import coredevices.indexai.data.entity.MessageRole
import coredevices.indexai.data.entity.MessageTag
import coredevices.indexai.data.entity.ToolCall
import coredevices.mcp.data.SemanticResult
import kotlinx.coroutines.flow.Flow
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
@Dao
interface ConversationMessageDao {
    @Query("SELECT * FROM ConversationMessageEntity WHERE recordingId = :recordingId ORDER BY id ASC")
    fun getMessagesForRecording(recordingId: Long): Flow<List<ConversationMessageEntity>>
    @Query("SELECT * FROM ConversationMessageEntity WHERE recordingId = :recordingId ORDER BY id DESC LIMIT 1")
    fun getLastMessageForRecording(recordingId: Long): Flow<ConversationMessageEntity?>
    @Query("SELECT * FROM ConversationMessageEntity WHERE recordingId = :recordingId AND role = :role ORDER BY id DESC LIMIT 1")
    fun getLastMessageForRecordingByRole(recordingId: Long, role: MessageRole): Flow<ConversationMessageEntity?>
    @Query("SELECT * FROM ConversationMessageEntity WHERE recordingId = :recordingId AND role = :role ORDER BY id ASC LIMIT 1")
    fun getFirstMessageForRecordingByRole(recordingId: Long, role: MessageRole): Flow<ConversationMessageEntity?>

    /** Don't call directly — use [insertMessage]. */
    @Insert
    suspend fun insertMessageRaw(message: ConversationMessageEntity): Long

    /** Don't call directly — use [insertMessages]. */
    @Insert
    suspend fun insertMessagesRaw(messages: List<ConversationMessageEntity>): List<Long>

    @Query("UPDATE LocalRecording SET updated = :updated WHERE id = :id")
    suspend fun touchLocalRecording(id: Long, updated: Instant)

    @Transaction
    suspend fun insertMessage(message: ConversationMessageEntity): Long {
        val id = insertMessageRaw(message)
        touchLocalRecording(message.recordingId, Clock.System.now())
        return id
    }

    @Transaction
    suspend fun insertMessages(messages: List<ConversationMessageEntity>): List<Long> {
        if (messages.isEmpty()) return emptyList()
        val ids = insertMessagesRaw(messages)
        val now = Clock.System.now()
        messages.asSequence().map { it.recordingId }.distinct().forEach { touchLocalRecording(it, now) }
        return ids
    }

    /** Used by the remote-recording ingest path to wipe-and-replace
     *  children when Firestore has a newer version of the recording. */
    @Query("DELETE FROM ConversationMessageEntity WHERE recordingId = :recordingId")
    suspend fun deleteAllForRecording(recordingId: Long)

    /** Every recording's tool results, the assistant turns that issued them and the assistant's
     *  answers, in conversation order. */
    @Query("""
        SELECT recordingId, id, role, tool_calls, tool_call_id, semantic_result, content, message_tags, language_model_used
        FROM ConversationMessageEntity
        WHERE role IN ('tool', 'assistant')
        ORDER BY recordingId ASC, id ASC
    """)
    fun getToolMessages(): Flow<List<RecordingToolMessage>>

    /** Don't call directly — use [updateSemanticResult]. */
    @Query("UPDATE ConversationMessageEntity SET semantic_result = :result WHERE recordingId = :recordingId AND tool_call_id = :toolCallId")
    suspend fun updateSemanticResultRaw(recordingId: Long, toolCallId: String, result: SemanticResult?)

    @Transaction
    suspend fun updateSemanticResult(recordingId: Long, toolCallId: String, result: SemanticResult?) {
        updateSemanticResultRaw(recordingId, toolCallId, result)
        touchLocalRecording(recordingId, Clock.System.now())
    }
}

/** Projection of a tool or assistant message: enough to pair each tool result with its call
 *  and to surface a tagged answer. */
data class RecordingToolMessage(
    val recordingId: Long,
    val id: Long,
    val role: MessageRole,
    @ColumnInfo(name = "tool_calls")
    val toolCalls: List<ToolCall>?,
    @ColumnInfo(name = "tool_call_id")
    val toolCallId: String?,
    @ColumnInfo(name = "semantic_result")
    val semanticResult: SemanticResult?,
    val content: String? = null,
    @ColumnInfo(name = "message_tags")
    val messageTags: List<MessageTag>? = null,
    @ColumnInfo(name = "language_model_used")
    val languageModelUsed: String? = null,
) {
    companion object {
        fun from(entity: ConversationMessageEntity) = RecordingToolMessage(
            recordingId = entity.recordingId,
            id = entity.id,
            role = entity.document.role,
            toolCalls = entity.document.tool_calls,
            toolCallId = entity.document.tool_call_id,
            semanticResult = entity.document.semantic_result,
            content = entity.document.content,
            messageTags = entity.document.messageTags,
            languageModelUsed = entity.document.language_model_used,
        )
    }
}

/** Set when this is an assistant message tagged as the answer to the user's query. */
val RecordingToolMessage.answerTag: MessageTag.AnswerResponse? get() =
    if (role == MessageRole.assistant) messageTags?.firstNotNullOfOrNull { it as? MessageTag.AnswerResponse } else null
