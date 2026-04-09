package coredevices.ring.database.room.repository

import androidx.room.Transactor
import androidx.room.useWriterConnection
import coredevices.indexai.data.entity.LocalRecording
import coredevices.indexai.data.entity.RecordingEntryEntity
import coredevices.indexai.data.entity.RecordingEntryStatus
import coredevices.indexai.database.dao.LocalRecordingDao
import coredevices.indexai.database.dao.RecordingEntryDao
import coredevices.ring.data.entity.room.RingTransfer
import coredevices.ring.data.entity.room.RingTransferStatus
import coredevices.ring.database.room.RingDatabase
import kotlin.time.Clock
import kotlin.time.Instant

class RecordingRepository(
    private val localRecordingDao: LocalRecordingDao,
    private val recordingEntryDao: RecordingEntryDao,
    private val db: RingDatabase
) {
    suspend fun createRecording(firestoreId: String? = null, localTimestamp: Instant = Clock.System.now()): Long {
        return localRecordingDao.insertRecording(
            LocalRecording(
                firestoreId = firestoreId,
                localTimestamp = localTimestamp,
            )
        )
    }

    fun getAllRecordings() =
        localRecordingDao.getAllRecordings()

    suspend fun getMostRecentTimestamp(): LocalRecording? =
        localRecordingDao.getMostRecentTimestamp()

    fun getAllRecordingsAfter(timestamp: Instant) =
        localRecordingDao.getAllRecordingsAfter(timestamp)

    suspend fun updateRecordingFirestoreId(id: Long, firestoreId: String) =
        localRecordingDao.updateRecordingFirestoreId(id, firestoreId)

    suspend fun getRecording(id: Long): LocalRecording? =
        localRecordingDao.getRecording(id)

    fun getRecordingFlow(id: Long) =
        localRecordingDao.getRecordingFlow(id)

    fun getRecordingEntriesFlow(id: Long) =
        recordingEntryDao.getEntriesForRecording(id)

    fun getPaginatedFeedItems() =
        localRecordingDao.getPaginatedFeedItems()
}