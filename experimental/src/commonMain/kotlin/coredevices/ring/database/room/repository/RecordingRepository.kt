package coredevices.ring.database.room.repository

import coredevices.indexai.data.entity.LocalRecording
import coredevices.indexai.data.entity.RecordingEntryEntity
import coredevices.indexai.data.entity.RecordingEntryStatus
import coredevices.indexai.database.dao.LocalRecordingDao
import coredevices.indexai.database.dao.RecordingEntryDao
import coredevices.ring.database.firestore.dao.FirestoreRecordingsDao
import coredevices.ring.database.room.RingDatabase
import co.touchlab.kermit.Logger
import coredevices.ring.service.RecordingBackgroundScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlin.time.Instant

class RecordingRepository(
    private val localRecordingDao: LocalRecordingDao,
    private val recordingEntryDao: RecordingEntryDao,
    private val firestoreRecordingsDao: FirestoreRecordingsDao,
    private val bgScope: RecordingBackgroundScope,
    private val db: RingDatabase
) {
    /** Insert a new LocalRecording. [firestoreId] is required and pre-
     *  allocated client-side via
     *  `FirestoreRecordingsDao.newDocumentId()` (offline-safe; no network
     *  round trip). Pre-allocation lets the items pipeline reference the
     *  recording by firestoreId at ingest time, even though the recording
     *  itself may not have been uploaded yet — which removes a whole
     *  class of cross-device-broken `local:N` references. */
    suspend fun createRecording(
        firestoreId: String,
        localTimestamp: Instant = Clock.System.now(),
        assistantTitle: String? = null,
        updated: Long? = null
    ): Long {
        return localRecordingDao.insertRecording(
            LocalRecording(
                firestoreId = firestoreId,
                localTimestamp = localTimestamp,
                assistantTitle = assistantTitle,
                updated = updated?.let { Instant.fromEpochMilliseconds(it) } ?: Clock.System.now()
            )
        )
    }

    fun getAllRecordings() =
        localRecordingDao.getAllRecordings()

    suspend fun getMostRecentTimestamp(): LocalRecording? =
        localRecordingDao.getMostRecentTimestamp()

    /** Most recent [limit] recordings, newest first. Used by the bug report
     *  flow to bundle recent recording history as a debug attachment. */
    suspend fun getRecentRecordings(limit: Int): List<LocalRecording> =
        localRecordingDao.getRecentRecordings(limit)

    fun getAllRecordingsAfter(timestamp: Instant) =
        localRecordingDao.getAllRecordingsAfter(timestamp)

    suspend fun updateRecordingFirestoreId(id: Long, firestoreId: String) =
        localRecordingDao.updateRecordingFirestoreId(id, firestoreId)

    /** Explicitly set `LocalRecording.updated`. Used by restore paths to pin the
     *  timestamp to the document's `updated` value after inserting entries/messages
     *  (which would otherwise auto-bump it to now via the DAO wrappers). */
    suspend fun setRecordingUpdated(id: Long, updated: Instant) =
        localRecordingDao.setUpdated(id, updated)

    /** Watermark of the last `updated` value (epoch millis) successfully
     *  pushed to Firestore, so the push observer can detect dirty rows
     *  locally instead of doing a per-row remote read. */
    suspend fun setLastPushedUpdated(id: Long, updated: Long) =
        localRecordingDao.setLastPushedUpdated(id, updated)

    suspend fun getRecording(id: Long): LocalRecording? =
        localRecordingDao.getRecording(id)

    /** Look up a LocalRecording by its Firestore doc id. Used by the
     *  remote-recording ingest path to decide whether to insert a fresh
     *  row or update an existing one. */
    suspend fun getByFirestoreId(firestoreId: String): LocalRecording? =
        localRecordingDao.getByFirestoreId(firestoreId)

    /** Replace a row's mutable fields wholesale. Used by the remote
     *  ingest path when Firestore has a newer version of the recording —
     *  we re-stamp localTimestamp / assistantTitle / updated, then wipe
     *  and reinsert children. */
    suspend fun updateRecording(recording: LocalRecording) =
        localRecordingDao.updateRecording(recording)

    fun getRecordingFlow(id: Long) =
        localRecordingDao.getRecordingFlow(id)

    fun getRecordingEntriesFlow(id: Long) =
        recordingEntryDao.getEntriesForRecording(id)

    fun getAllEntriesFlow() = recordingEntryDao.getAllEntriesFlow()

    fun getPaginatedFeedItems() =
        localRecordingDao.getPaginatedFeedItems()

    suspend fun getAllFirestoreIds(): Set<String> =
        localRecordingDao.getAllFirestoreIds().toHashSet()

    suspend fun deleteAllLocalRecordings() =
        localRecordingDao.deleteAll()

    suspend fun createFailedRecordingEntry(recordingId: Long, errorMessage: String) =
        recordingEntryDao.insertRecordingEntry(
            RecordingEntryEntity(
                recordingId = recordingId,
                status = RecordingEntryStatus.agent_error,
                transcription = "Error: $errorMessage",
                error = errorMessage
            )
        )

    /** Hard-delete a single recording from Room (entries cascade via FK)
     *  and, when authenticated, delete its Firestore doc too. */
    suspend fun deleteRecording(id: Long) {
        withContext(Dispatchers.IO) {
            val rec = localRecordingDao.getRecording(id) ?: return@withContext
            localRecordingDao.deleteRecording(rec)
            bgScope.launch(Dispatchers.IO) {
                rec.firestoreId?.takeIf { it.isNotBlank() }?.let { firestoreId ->
                    try {
                        firestoreRecordingsDao.deleteRecordingsByIds(listOf(firestoreId))
                    } catch (e: Exception) {
                        Logger.withTag("RecordingRepository")
                            .w(e) { "Failed to delete Firestore doc $firestoreId for recording $id" }
                    }
                }
            }
        }
    }
}