package coredevices.ring.backup

import co.touchlab.kermit.Logger
import coredevices.indexai.data.entity.RecordingDocument
import coredevices.ring.database.Preferences
import coredevices.ring.database.firestore.dao.FirestoreRecordingsDao
import coredevices.ring.database.room.repository.ItemRepository
import coredevices.ring.database.room.repository.ListRepository
import coredevices.ring.database.room.repository.RecordingRepository
import coredevices.ring.encryption.DocumentEncryptor
import coredevices.ring.encryption.KeyFingerprintMismatchException
import coredevices.ring.encryption.TamperedException
import coredevices.ring.storage.RecordingStorage
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.firestore.DocumentSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlin.time.Clock

sealed interface BackupDeleteProgress {
    data object CollectingAudio : BackupDeleteProgress
    data object DeletingDocuments : BackupDeleteProgress
    data class DeletingAudio(val count: Int) : BackupDeleteProgress
}

class ExportedBackup(val zipPath: Path, val recordings: Int, val audioFiles: Int, val decryptSkipped: Int)

/**
 * Cloud backup maintenance: counting, deleting, and exporting the signed-in user's recordings.
 */
class BackupManager(
    private val firestoreRecordingsDao: FirestoreRecordingsDao,
    private val recordingRepository: RecordingRepository,
    private val recordingStorage: RecordingStorage,
    private val itemRepository: ItemRepository,
    private val listRepository: ListRepository,
    private val backupExporter: BackupExporter,
    private val documentEncryptor: DocumentEncryptor,
    private val preferences: Preferences,
) {
    private val logger = Logger.withTag("Backup")

    /** Refreshes [Preferences.lastBackupCount] with Firestore's server-side count. */
    suspend fun refreshBackupCount() {
        val count = withContext(Dispatchers.IO) { firestoreRecordingsDao.getCount() }
        preferences.setLastBackupCount(count.toInt())
    }

    suspend fun deleteCloudBackup(onProgress: (BackupDeleteProgress) -> Unit = {}) = withContext(Dispatchers.IO) {
        onProgress(BackupDeleteProgress.CollectingAudio)
        val audioFileIds = mutableListOf<String>()
        var cursor: DocumentSnapshot? = null
        while (true) {
            val docs = firestoreRecordingsDao.getPaginated(100, cursor).documents
            if (docs.isEmpty()) break
            for (doc in docs) {
                val recording = runCatching { doc.data<RecordingDocument>() }.getOrNull() ?: continue
                audioFileIds += audioFileIds(recording)
            }
            cursor = docs.last()
        }

        onProgress(BackupDeleteProgress.DeletingDocuments)
        firestoreRecordingsDao.deleteAllRecordings()

        if (audioFileIds.isNotEmpty()) {
            onProgress(BackupDeleteProgress.DeletingAudio(audioFileIds.size))
            for (id in audioFileIds) {
                recordingStorage.deleteFromFirebaseStorage(id)
            }
            logger.i { "Deleted ${audioFileIds.size} audio files from Storage" }
        }
        preferences.setLastBackupCount(0)
        logger.i { "All backup recordings and audio files deleted" }
    }

    /** Wipes Room rows and cached audio. Firestore is untouched: the sync observers
     *  re-ingest everything on the next snapshot. */
    suspend fun deleteLocalFeed() = withContext(Dispatchers.IO) {
        recordingRepository.deleteAllLocalRecordings()
        recordingStorage.deleteAllCachedMetadata()
        recordingStorage.clearCacheDirectory()
        itemRepository.deleteAllLocal()
        listRepository.deleteAllLocal()
        logger.i { "All local feed data deleted (recordings, entries, items, lists, cache, metadata)" }
    }

    /**
     * Exports every cloud recording as cleartext into a zip in the cache directory. The caller
     * owns the returned file. Recordings that can't be decrypted are counted in
     * [ExportedBackup.decryptSkipped]. [onProgress] receives (done, total) where total is null
     * if the count lookup failed.
     */
    suspend fun exportAll(onProgress: (done: Int, total: Int?) -> Unit = { _, _ -> }): ExportedBackup =
        withContext(Dispatchers.IO) {
            val user = Firebase.auth.currentUser ?: throw IllegalStateException("Not signed in")
            logger.i { "Starting full backup for user ${user.uid}" }
            val total = runCatching { firestoreRecordingsDao.getCount().toInt() }.getOrNull()

            val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
            val zipPath = Path(recordingStorage.getCacheDirectory(), "$today Pebble Index backup.zip")

            var decryptSkipped = 0
            val recordings = allRecordingDocuments().mapNotNull { (firestoreId, rawDoc) ->
                decryptForBackup(firestoreId, rawDoc)
                    ?.let { BackupRecording(firestoreId, it) }
                    ?: run { decryptSkipped++; null }
            }
            val result = try {
                backupExporter.export(
                    zipPath = zipPath,
                    userId = user.uid,
                    email = user.email ?: "unknown",
                    recordings = recordings,
                ) { done -> onProgress(done, total) }
            } catch (e: Exception) {
                runCatching { SystemFileSystem.delete(zipPath, mustExist = false) }
                throw e
            }
            logger.i { "Backup complete: ${result.recordings} recordings, ${result.audioFiles} audio files, $decryptSkipped skipped due to decrypt failure" }
            ExportedBackup(zipPath, result.recordings, result.audioFiles, decryptSkipped)
        }

    private fun allRecordingDocuments(): Flow<Pair<String, RecordingDocument>> = flow {
        var cursor: DocumentSnapshot? = null
        while (true) {
            val docs = firestoreRecordingsDao.getPaginated(50, cursor).documents
            if (docs.isEmpty()) break
            for (doc in docs) {
                val data = try {
                    doc.data<RecordingDocument>()
                } catch (e: Exception) {
                    logger.w(e) { "Skipping malformed document ${doc.id}" }
                    continue
                }
                emit(doc.id to data)
            }
            cursor = docs.last()
        }
    }

    /** Backups are always cleartext; returns null when the document can't be decrypted. */
    private suspend fun decryptForBackup(firestoreId: String, rawDoc: RecordingDocument): RecordingDocument? {
        if (rawDoc.encrypted == null) return rawDoc
        val key = documentEncryptor.getKey()
        if (key == null) {
            logger.w { "Encrypted recording $firestoreId but no local key — skipping" }
            return null
        }
        return try {
            documentEncryptor.decryptDocument(rawDoc, key)
        } catch (e: KeyFingerprintMismatchException) {
            logger.e { "Recording $firestoreId encrypted with key ${e.expected} but local key is ${e.actual} — skipping" }
            null
        } catch (e: TamperedException) {
            logger.e(e) { "Recording $firestoreId failed integrity check — skipping" }
            null
        }
    }

    companion object {
        /** Storage ids of every audio variant a recording document may have uploaded. */
        fun audioFileIds(document: RecordingDocument): List<String> =
            document.entries.mapNotNull { it.fileName }.flatMap { listOf(it, "$it${BackupFormat.ORIGINAL_SUFFIX}") }
    }
}
