package coredevices.indexai.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import coredevices.indexai.data.entity.RecordingEntryEntity
import coredevices.indexai.data.entity.RecordingEntryStatus
import kotlinx.coroutines.flow.Flow
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
@Dao
interface RecordingEntryDao {
    /** Don't call directly — use [insertRecordingEntry] so the parent
     *  `LocalRecording.updated` column is bumped in the same transaction. */
    @Insert
    suspend fun insertRecordingEntryRaw(recordingEntry: RecordingEntryEntity): Long

    /** Don't call directly — use [insertRecordingEntries]. */
    @Insert
    suspend fun insertRecordingEntriesRaw(entries: List<RecordingEntryEntity>)

    @Query("UPDATE LocalRecording SET updated = :updated WHERE id = :id")
    suspend fun touchLocalRecording(id: Long, updated: Instant)

    @Transaction
    suspend fun insertRecordingEntry(recordingEntry: RecordingEntryEntity): Long {
        val id = insertRecordingEntryRaw(recordingEntry)
        touchLocalRecording(recordingEntry.recordingId, Clock.System.now())
        return id
    }

    @Transaction
    suspend fun insertRecordingEntries(entries: List<RecordingEntryEntity>) {
        if (entries.isEmpty()) return
        insertRecordingEntriesRaw(entries)
        val now = Clock.System.now()
        entries.asSequence().map { it.recordingId }.distinct().forEach { touchLocalRecording(it, now) }
    }

    @Query("SELECT * FROM RecordingEntryEntity WHERE recordingId = :recordingId ORDER BY timestamp ASC")
    fun getEntriesForRecording(recordingId: Long): Flow<List<RecordingEntryEntity>>

    /** Returns the set of recordingIds that have at least one entry.
     *  Used by the push observer to skip empty placeholder rows — a
     *  recording with zero entries hasn't produced user-visible content
     *  yet, so uploading it just creates ghost "Index Recording" rows on
     *  every other device. After server-side cleanup of empty docs,
     *  devices that were offline at delete time would otherwise re-upload
     *  their stale local placeholders and undo the cleanup. */
    @Query("SELECT DISTINCT recordingId FROM RecordingEntryEntity")
    suspend fun getRecordingIdsWithEntries(): List<Long>

    @Query("SELECT * FROM RecordingEntryEntity ORDER BY recordingId, timestamp ASC")
    fun getAllEntriesFlow(): Flow<List<RecordingEntryEntity>>

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

    /** Used by the remote-recording ingest path to wipe-and-replace
     *  children when Firestore has a newer version of the recording. */
    @Query("DELETE FROM RecordingEntryEntity WHERE recordingId = :recordingId")
    suspend fun deleteAllForRecording(recordingId: Long)

    @Update
    suspend fun update(entry: RecordingEntryEntity)
}