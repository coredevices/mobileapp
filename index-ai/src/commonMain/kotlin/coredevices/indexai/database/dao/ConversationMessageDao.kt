package coredevices.indexai.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import coredevices.indexai.data.entity.ConversationMessageEntity
import coredevices.indexai.data.entity.MessageRole
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
}