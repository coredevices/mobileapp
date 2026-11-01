package coredevices.ring.backup

import co.touchlab.kermit.Logger
import coredevices.indexai.data.entity.ConversationMessageEntity
import coredevices.indexai.data.entity.RecordingEntryEntity
import coredevices.indexai.database.dao.ConversationMessageDao
import coredevices.indexai.database.dao.RecordingEntryDao
import coredevices.ring.database.Preferences
import coredevices.ring.database.firestore.dao.FirestoreRecordingsDao
import coredevices.ring.database.room.repository.RecordingRepository
import coredevices.ring.encryption.DocumentEncryptor
import coredevices.ring.storage.RecordingStorage
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.io.files.Path
import kotlin.time.Instant

/**
 * Restores a backup zip into the cloud and the local feed. Recordings already present
 * locally (matched by firestoreId) are not re-uploaded, but missing entries and messages
 * are backfilled from the document.
 */
class BackupImporter(
    private val recordingRepository: RecordingRepository,
    private val recordingEntryDao: RecordingEntryDao,
    private val conversationMessageDao: ConversationMessageDao,
    private val recordingStorage: RecordingStorage,
    private val firestoreRecordingsDao: FirestoreRecordingsDao,
    private val documentEncryptor: DocumentEncryptor,
    private val preferences: Preferences,
) {
    private val logger = Logger.withTag("BackupImport")

    data class Result(val imported: Int, val skipped: Int, val audioUploaded: Int, val failed: Int)

    /** [onProgress] is called with (imported so far, total recordings in the archive). */
    suspend fun import(
        zipPath: Path,
        onProgress: (imported: Int, total: Int) -> Unit = { _, _ -> },
    ): Result = withContext(Dispatchers.IO) {
        val user = Firebase.auth.currentUser ?: throw IllegalStateException("Not signed in")
        logger.i { "Starting backup import for user ${user.uid}" }

        val reader = BackupZipReader(zipPath)
        reader.open()
        try {
            val recordings = BackupArchive.readRecordings(reader)
            val existingCount = recordingRepository.getAllFirestoreIds().size
            logger.i { "Parsed ${recordings.size} recordings. $existingCount already in local DB." }
            onProgress(0, recordings.size)

            val encryptionKey = if (preferences.useEncryption.value) {
                documentEncryptor.getKey().also { key ->
                    if (key == null) {
                        logger.w { "Encryption is enabled, but no key is available during backup import; uploading audio unencrypted" }
                    }
                }
            } else {
                null
            }

            var imported = 0
            var skipped = 0
            var audioUploaded = 0
            var failed = 0
            val counterMutex = Mutex()
            val zipMutex = Mutex()
            val semaphore = Semaphore(MAX_CONCURRENT_UPLOADS)

            coroutineScope {
                recordings.map { rec ->
                    async {
                        semaphore.withPermit {
                            try {
                                val existingLocalRow = recordingRepository.getByFirestoreId(rec.firestoreId)
                                val localId = if (existingLocalRow != null) {
                                    counterMutex.withLock { skipped++ }
                                    existingLocalRow.id
                                } else {
                                    for (audio in rec.audioFiles) {
                                        val pcmBytes = zipMutex.withLock { reader.readBytes(audio.entryName) }
                                        recordingStorage.uploadRecordingPcm(
                                            id = audio.variant,
                                            sampleRate = audio.sampleRate,
                                            pcmBytes = pcmBytes,
                                            encryptionKey = encryptionKey,
                                        )
                                        counterMutex.withLock { audioUploaded++ }
                                    }
                                    // Exports are cleartext; encrypted accounts must store encrypted docs.
                                    val docToUpload = if (encryptionKey != null) {
                                        documentEncryptor.encryptDocument(rec.document, encryptionKey)
                                    } else {
                                        rec.document
                                    }
                                    firestoreRecordingsDao.setRecording(rec.firestoreId, docToUpload)
                                    recordingRepository.createRecording(
                                        firestoreId = rec.firestoreId,
                                        localTimestamp = rec.document.timestamp,
                                        assistantTitle = rec.document.assistantSession?.title,
                                        updated = rec.document.updated,
                                    )
                                }

                                backfillLocalRows(localId, rec)

                                if (existingLocalRow != null) return@withPermit
                                val count = counterMutex.withLock { ++imported }
                                if (count % PROGRESS_EVERY == 0 || count == recordings.size) {
                                    onProgress(count, recordings.size)
                                }
                            } catch (e: Exception) {
                                counterMutex.withLock { failed++ }
                                logger.e(e) { "Failed to import ${rec.firestoreId}: ${e.message}" }
                            }
                        }
                    }
                }.awaitAll()
            }

            Result(imported, skipped, audioUploaded, failed).also { logger.i { "$it" } }
        } finally {
            reader.close()
        }
    }

    private suspend fun backfillLocalRows(localId: Long, rec: BackupRecordingImport) {
        val existingEntries = recordingEntryDao.getEntriesForRecording(localId).first()
        if (existingEntries.isEmpty() && rec.document.entries.isNotEmpty()) {
            recordingEntryDao.insertRecordingEntries(
                rec.document.entries.map { entry ->
                    RecordingEntryEntity(
                        recordingId = localId,
                        timestamp = entry.timestamp,
                        fileName = entry.fileName,
                        status = entry.status,
                        transcription = entry.transcription,
                        transcribedUsingModel = entry.transcribedUsingModel,
                        transcribedViaLocalFallback = entry.transcribedViaLocalFallback,
                        error = entry.error,
                        ringTransferInfo = entry.ringTransferInfo,
                        userMessageId = entry.userMessageId,
                    )
                }
            )
        }
        val messages = rec.document.assistantSession?.messages
        if (!messages.isNullOrEmpty()) {
            val existingMessages = conversationMessageDao.getMessagesForRecording(localId).first()
            if (existingMessages.isEmpty()) {
                conversationMessageDao.insertMessages(
                    messages.map { msg -> ConversationMessageEntity(recordingId = localId, document = msg) }
                )
            }
        }
        // Entry/message inserts bump `updated` to now, which would make the upload
        // observer re-upload a freshly imported recording.
        recordingRepository.setRecordingUpdated(localId, Instant.fromEpochMilliseconds(rec.document.updated))
    }

    private companion object {
        const val MAX_CONCURRENT_UPLOADS = 6
        const val PROGRESS_EVERY = 5
    }
}
