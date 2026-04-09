package coredevices.indexai.database.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import coredevices.indexai.data.entity.LocalRecording
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

@Dao
interface LocalRecordingDao {
    @Insert
    suspend fun insertRecording(recording: LocalRecording): Long
    @Update
    suspend fun updateRecording(recording: LocalRecording)

    @Query("UPDATE LocalRecording SET firestoreId = :firestoreId WHERE id = :id")
    suspend fun updateRecordingFirestoreId(id: Long, firestoreId: String)

    @Delete
    suspend fun deleteRecording(recording: LocalRecording)

    @Query("SELECT * FROM LocalRecording WHERE id = :id")
    suspend fun getRecording(id: Long): LocalRecording?

    @Query("SELECT * FROM LocalRecording WHERE id = :id")
    fun getRecordingFlow(id: Long): Flow<LocalRecording?>

    @Query("SELECT * FROM LocalRecording")
    fun getAllRecordings(): Flow<List<LocalRecording>>

    @Query("SELECT * FROM LocalRecording WHERE localTimestamp > :timestamp")
    fun getAllRecordingsAfter(timestamp: Instant): Flow<List<LocalRecording>>

    @Query("SELECT count(*) FROM LocalRecording")
    fun getRecordingsCount(): Flow<Int>

    @Query("SELECT * FROM RecordingFeedItem ORDER BY localTimestamp DESC")
    fun getPaginatedFeedItems(): PagingSource<Int, RecordingFeedItem>

    @Query("SELECT * FROM RecordingFeedItem WHERE rootRecordingId = :recordingId")
    fun getFeedItemByIdFlow(recordingId: Long): Flow<RecordingFeedItem?>

    @Query("SELECT * FROM LocalRecording ORDER BY localTimestamp DESC LIMIT 1")
    suspend fun getMostRecentTimestamp(): LocalRecording?
}
