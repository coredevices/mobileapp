package coredevices.ring.storage

import co.touchlab.kermit.Logger
import coredevices.ring.data.entity.room.CachedRecordingMetadata
import coredevices.ring.database.room.dao.CachedRecordingMetadataDao
import coredevices.ring.util.openReadChannel
import coredevices.util.writeWavHeader
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.storage.File
import dev.gitlive.firebase.storage.FirebaseStorageMetadata
import dev.gitlive.firebase.storage.storage
import io.ktor.utils.io.exhausted
import io.ktor.utils.io.readAvailable
import kotlinx.io.Sink
import kotlinx.io.Source
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

/**
 * Platform-specific path for caching recordings before they are persisted
 */
internal expect fun getRecordingsCacheDirectory(): Path

/**
 * Platform-specific path for storing complete recordings
 */
internal expect fun getRecordingsDataDirectory(): Path

internal expect fun getFirebaseStorageFile(path: Path): File

/**
 * Access storage for recordings
 */
class RecordingStorage(private val cachedMetadataDao: CachedRecordingMetadataDao) {
    companion object {
        private val logger = Logger.withTag(RecordingStorage::class.simpleName!!)
        private const val FS_WRITE_BUFFER_SIZE = 8192
    }
    init {
        ensureDirectories() // Ensure full paths created on first access
    }
    private fun ensureDirectories() {
        val cache = getRecordingsCacheDirectory()
        val data = getRecordingsDataDirectory()
        SystemFileSystem.createDirectories(cache, false)
        SystemFileSystem.createDirectories(data, false)
    }

    /**
     * Export a recording to a WAV file
     * @param id unique identifier for the recording
     * @return path to the exported file
     */
    suspend fun exportRecording(id: String): Path {
        val (source, meta) = openRecordingSource(id)
        val path = Path(getRecordingsCacheDirectory(), "share-$id.wav")
        source.use {
            SystemFileSystem.sink(path).buffered().use { sink ->
                sink.writeWavHeader(meta.cachedMetadata.sampleRate, meta.size.toInt())
                source.transferTo(sink)
            }
        }
        return path
    }

    /**
     * Open a sink for writing recording data, storing temporarily in cache
     * until [persistRecording] is called
     * @param id unique identifier for the recording, cannot contain characters that are invalid in file names
     */
    suspend fun openRecordingSink(id: String, sampleRate: Int, mimeType: String): Sink {
        val metadata = CachedRecordingMetadata(id, sampleRate, mimeType)
        cachedMetadataDao.insertOrReplace(metadata)
        return SystemFileSystem.sink(Path(getRecordingsCacheDirectory(), id)).buffered()
    }

    /**
     * Open a sink for writing the original raw version of a recording, storing temporarily in cache
     * until [persistRecording] is called
     * @param id unique identifier for the recording, cannot contain characters that are invalid in file names
     */
    suspend fun openCleanRecordingSink(id: String, sampleRate: Int, mimeType: String): Sink {
        val metadata = CachedRecordingMetadata("$id-clean", sampleRate, mimeType)
        cachedMetadataDao.insert(metadata)
        return SystemFileSystem.sink(Path(getRecordingsCacheDirectory(), "$id-clean")).buffered()
    }

    /**
     * Open a source for reading recording data
     */
    suspend fun openRecordingSource(id: String): Pair<Source, RecordingSourceInfo> {
        val cachedPath = Path(getRecordingsCacheDirectory(), id)
        var cachedMetadata = cachedMetadataDao.get(id)
        if (!SystemFileSystem.exists(cachedPath) || cachedMetadata == null) { // Not in cache, download
            logger.d { "Downloading recording $id" }
            val path = "recordings/${Firebase.auth.currentUser!!.uid}/$id"
            val ref = Firebase.storage.reference(path)

            // Grab metadata from firebase and insert/update in local db
            val fbMeta = ref.getMetadata()
            val sampleRate = fbMeta?.customMetadata?.get("sampleRate")?.toInt()
                ?: error("Sample rate for recording $id not in firebase metadata")
            val contentType = fbMeta.contentType
                ?: error("Content type for recording $id not in firebase metadata")
            cachedMetadata = CachedRecordingMetadata(id, sampleRate, contentType)
            cachedMetadataDao.insertOrReplace(cachedMetadata)

            val channel = ref.openReadChannel()
            val sink = SystemFileSystem.sink(cachedPath).buffered()
            sink.use { output ->
                val buf = ByteArray(FS_WRITE_BUFFER_SIZE)
                while (!channel.exhausted()) {
                    val read = channel.readAvailable(buf)
                    output.write(buf, 0, read)
                }
            }
        }
        val size = SystemFileSystem.metadataOrNull(cachedPath)?.size
            ?: error("Failed to get size of recording $id")
        return Pair(
            SystemFileSystem.source(cachedPath).buffered(),
            RecordingSourceInfo(id, cachedMetadata, size)
        )
    }

    /**
     * Information about a recording source returned by [openRecordingSource]
     * @param id ID used to obtain the source
     * @param cachedMetadata metadata for the recording
     * @param size size of the recording in bytes
     */
    data class RecordingSourceInfo(
        val id: String,
        val cachedMetadata: CachedRecordingMetadata,
        val size: Long,
    )

    /**
     * Moves a recording from cache to persistent data storage,
     * should be used once recording is complete & validated
     * @param id unique identifier for the recording
     * @param sampleRate sample rate of the recording
     */
    suspend fun persistRecording(id: String) {
        for (idToMove in listOf(id, "$id-clean")) {
            val source = Path(getRecordingsCacheDirectory(), idToMove)
            val cachedMetadata = cachedMetadataDao.get(idToMove)
                ?: error("Cached metadata for recording $idToMove not found")
            val destination = "recordings/${Firebase.auth.currentUser!!.uid}/$idToMove"
            require(SystemFileSystem.exists(source)) {
                "Recording $idToMove does not exist in cache"
            }
            Firebase.storage.reference(destination)
                .putFile(
                    getFirebaseStorageFile(source),
                    FirebaseStorageMetadata(
                        contentType = cachedMetadata.mimeType,
                        customMetadata = mutableMapOf(
                            "sampleRate" to cachedMetadata.sampleRate.toString()
                        )
                    )
                )
        }
    }

    /**
     * Deletes a recording from persistent storage
     * @param id unique identifier for the recording
     */
    fun deleteRecording(id: String) {
        val source = Path(getRecordingsDataDirectory(), id)
        SystemFileSystem.delete(source)
    }

    /**
     * Deletes a recording from cache
     * @param id unique identifier for the recording
     */
    fun deleteRecordingFromCache(id: String) {
        val source = Path(getRecordingsCacheDirectory(), id)
        SystemFileSystem.delete(source)
    }

    /**
     * Check if a recording exists in storage, does not check cache
     * @param id unique identifier for the recording
     */
    fun recordingExists(id: String): Boolean {
        val source = Path(getRecordingsDataDirectory(), id)
        return SystemFileSystem.exists(source)
    }
}