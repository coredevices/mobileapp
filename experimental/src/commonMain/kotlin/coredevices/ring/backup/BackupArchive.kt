package coredevices.ring.backup

import co.touchlab.kermit.Logger
import coredevices.indexai.data.entity.RecordingDocument

/** Read access to a backup archive's entries; implemented by [BackupZipReader]. */
interface BackupEntrySource {
    val entryNames: Set<String>
    suspend fun readBytes(name: String): ByteArray
}

class BackupAudioFile(
    val variant: String,
    val entryName: String,
    val sampleRate: Int,
    val mimeType: String,
)

class BackupRecordingImport(
    val firestoreId: String,
    val document: RecordingDocument,
    val audioFiles: List<BackupAudioFile>,
)

/**
 * Parses the recordings in a backup archive. Only documents are read here; audio is read
 * on demand as each recording is imported.
 */
object BackupArchive {
    private val logger = Logger.withTag("BackupArchive")

    suspend fun readRecordings(source: BackupEntrySource): List<BackupRecordingImport> {
        val entryNames = source.entryNames
        return entryNames
            .mapNotNull { BackupFormat.recordingIdFromEntry(it) }
            .distinct()
            .mapNotNull { firestoreId ->
                val docEntry = BackupFormat.documentEntry(firestoreId)
                if (docEntry !in entryNames) return@mapNotNull null
                val document = try {
                    BackupFormat.json.decodeFromString(
                        RecordingDocument.serializer(),
                        source.readBytes(docEntry).decodeToString(),
                    )
                } catch (e: Exception) {
                    logger.w(e) { "Failed to parse document for $firestoreId" }
                    return@mapNotNull null
                }
                BackupRecordingImport(firestoreId, document, audioFilesFor(source, entryNames, firestoreId, document))
            }
    }

    private suspend fun audioFilesFor(
        source: BackupEntrySource,
        entryNames: Set<String>,
        firestoreId: String,
        document: RecordingDocument,
    ): List<BackupAudioFile> = document.entries.flatMap { entry ->
        val fileName = entry.fileName ?: return@flatMap emptyList()
        listOf(fileName, "$fileName${BackupFormat.ORIGINAL_SUFFIX}").mapNotNull { variant ->
            val rawEntry = BackupFormat.audioEntry(firestoreId, variant)
            if (rawEntry !in entryNames) return@mapNotNull null
            val meta = BackupFormat.audioMetaEntry(firestoreId, variant)
                .takeIf { it in entryNames }
                ?.let { BackupFormat.decodeAudioMeta(source.readBytes(it)) }
                ?: BackupAudioMeta()
            BackupAudioFile(variant, rawEntry, meta.sampleRate, meta.mimeType)
        }
    }
}
