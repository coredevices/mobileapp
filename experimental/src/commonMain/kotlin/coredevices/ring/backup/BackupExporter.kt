package coredevices.ring.backup

import co.touchlab.kermit.Logger
import coredevices.indexai.data.entity.RecordingDocument
import coredevices.ring.storage.RecordingStorage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlin.time.Clock

class BackupRecording(val firestoreId: String, val document: RecordingDocument)

/**
 * Writes a full backup zip one recording at a time, streaming audio from storage into the archive.
 */
class BackupExporter(private val recordingStorage: RecordingStorage) {
    private val logger = Logger.withTag("BackupExporter")

    data class Result(val recordings: Int, val audioFiles: Int)

    suspend fun export(
        zipPath: Path,
        userId: String,
        email: String,
        recordings: Flow<BackupRecording>,
        onProgress: (recordingsDone: Int) -> Unit = {},
    ): Result {
        if (SystemFileSystem.exists(zipPath)) {
            SystemFileSystem.delete(zipPath)
        }
        var count = 0
        var audioFiles = 0
        val zip = BackupZipWriter(zipPath)
        zip.open()
        try {
            recordings.collect { recording ->
                val id = recording.firestoreId
                val docJson = BackupFormat.json.encodeToString(RecordingDocument.serializer(), recording.document)
                zip.addEntry(BackupFormat.documentEntry(id), docJson.encodeToByteArray())
                for (entry in recording.document.entries) {
                    val fileName = entry.fileName ?: continue
                    if (addAudio(zip, id, fileName, original = false)) audioFiles++
                    if (addAudio(zip, id, fileName, original = true)) audioFiles++
                }
                onProgress(++count)
            }
            val manifest = BackupManifest(
                version = BackupFormat.VERSION,
                userId = userId,
                email = email,
                exportedAt = Clock.System.now().toString(),
                recordingCount = count,
            )
            zip.addEntry(
                BackupFormat.MANIFEST_ENTRY,
                BackupFormat.json.encodeToString(BackupManifest.serializer(), manifest).encodeToByteArray(),
            )
        } finally {
            zip.close()
        }
        return Result(recordings = count, audioFiles = audioFiles)
    }

    private suspend fun addAudio(zip: BackupZipWriter, firestoreId: String, fileName: String, original: Boolean): Boolean {
        val variant = if (original) "$fileName${BackupFormat.ORIGINAL_SUFFIX}" else fileName
        return try {
            val (source, info) = recordingStorage.openRecordingSource(fileName, useOriginalAudio = original)
            source.use { src ->
                if (info.id != variant) {
                    logger.d { "Skipping $variant: storage returned ${info.id} instead" }
                    return false
                }
                val meta = BackupAudioMeta(info.cachedMetadata.sampleRate, info.cachedMetadata.mimeType)
                zip.addEntry(
                    BackupFormat.audioMetaEntry(firestoreId, variant),
                    BackupFormat.json.encodeToString(BackupAudioMeta.serializer(), meta).encodeToByteArray(),
                )
                zip.addEntry(BackupFormat.audioEntry(firestoreId, variant), src)
                true
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.w { "Could not download audio $variant: ${e.message}" }
            false
        }
    }
}
