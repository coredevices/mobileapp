package coredevices.indexai.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import coredevices.indexai.data.entity.ConversationMessageEntity
import coredevices.indexai.data.entity.MessageRole
import kotlinx.coroutines.flow.Flow

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
    @Insert
    suspend fun insertMessage(message: ConversationMessageEntity): Long
    @Insert
    suspend fun insertMessages(messages: List<ConversationMessageEntity>): List<Long>
}