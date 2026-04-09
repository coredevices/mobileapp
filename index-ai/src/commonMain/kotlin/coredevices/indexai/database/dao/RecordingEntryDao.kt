package coredevices.indexai.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import coredevices.indexai.data.entity.RecordingEntryEntity
import coredevices.indexai.data.entity.RecordingEntryStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordingEntryDao {
    @Insert
    suspend fun insertRecordingEntry(recordingEntry: RecordingEntryEntity): Long

    @Query("SELECT * FROM RecordingEntryEntity WHERE recordingId = :recordingId ORDER BY timestamp ASC")
    fun getEntriesForRecording(recordingId: Long): Flow<List<RecordingEntryEntity>>

    @Query("SELECT * FROM RecordingEntryEntity WHERE recordingId = :recordingId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getMostRecentEntryForRecording(recordingId: Long): RecordingEntryEntity?

    @Query("UPDATE RecordingEntryEntity SET userMessageId = :userMessageId WHERE id = :recordingId")
    suspend fun updateRecordingEntryMessage(recordingId: Long, userMessageId: Long)
    @Query("UPDATE RecordingEntryEntity SET status = :status, error = :error WHERE id = :recordingId")
    suspend fun updateRecordingEntryStatus(recordingId: Long, status: RecordingEntryStatus, error: String? = null)
    @Query("UPDATE RecordingEntryEntity SET transcription = :transcription, transcribedUsingModel = :modelUsed WHERE id = :recordingId")
    suspend fun updateRecordingEntryTranscription(recordingId: Long, transcription: String?, modelUsed: String? = null)

    @Query("SELECT * FROM RecordingEntryEntity WHERE id = :id")
    suspend fun getById(id: Long): RecordingEntryEntity?

    @Update
    suspend fun update(entry: RecordingEntryEntity)
}