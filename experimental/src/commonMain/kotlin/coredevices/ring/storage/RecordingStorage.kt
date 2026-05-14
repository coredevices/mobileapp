package coredevices.ring.storage

import co.touchlab.kermit.Logger
import coredevices.ring.audio.M4aDecoder
import coredevices.ring.audio.M4aEncoder
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
import kotlinx.io.readByteArray
import kotlinx.io.readShortLe
import kotlinx.io.writeShortLe

/**
 * Platform-specific path for caching recordings before they are persisted
 */
internal expect fun getRecordingsCacheDirectory(): Path

/**
 * Platform-specific path for storing complete recordings
 */
internal expect fun getRecordingsDataDirectory(): Path

expect fun getFirebaseStorageFile(path: Path): File

/**
 * Access storage for recordings
 */
class RecordingStorage(
    private val cachedMetadataDao: CachedRecordingMetadataDao,
    private val documentEncryptor: coredevices.ring.encryption.DocumentEncryptor,
    private val preferences: coredevices.ring.database.Preferences,
) {
    companion object {
        private val logger = Logger.withTag(RecordingStorage::class.simpleName!!)
        private const val FS_WRITE_BUFFER_SIZE = 8192
        private const val PCM_MIME = "audio/raw"
        private const val M4A_MIME = "audio/mp4"
    }

    private val m4aEncoder = M4aEncoder()
    private val m4aDecoder = M4aDecoder()
    init {
        ensureDirectories() // Ensure full paths created on first access
    }
    private fun ensureDirectories() {
        val cache = getRecordingsCacheDirectory()
        val data = getRecordingsDataDirectory()
        SystemFileSystem.createDirectories(cache, false)
        SystemFileSystem.createDirectories(data, false)
    }

    fun getCacheDirectory(): Path = getRecordingsCacheDirectory()

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

            // Grab metadata from firebase to learn the original PCM sample rate
            val fbMeta = ref.getMetadata()
            val sampleRate = fbMeta?.customMetadata?.get("sampleRate")?.toInt()
                ?: error("Sample rate for recording $id not in firebase metadata")
            val isEncrypted = fbMeta.customMetadata?.get("encrypted") == "true"
            val isPcm = fbMeta.contentType == PCM_MIME

            // Download the payload to a temporary file in the cache directory
            val m4aTempPath = Path(getRecordingsCacheDirectory(), "$id.download.m4a")
            try {
                val channel = ref.openReadChannel()
                SystemFileSystem.sink(m4aTempPath).buffered().use { output ->
                    val buf = ByteArray(FS_WRITE_BUFFER_SIZE)
                    while (!channel.exhausted()) {
                        val read = channel.readAvailable(buf)
                        output.write(buf, 0, read)
                    }
                }

                // Read the downloaded bytes, decrypting first if necessary
                var payloadBytes = SystemFileSystem.source(m4aTempPath).buffered().use { src ->
                    src.readByteArray()
                }
                if (isEncrypted) {
                    val key = documentEncryptor.getKey()
                        ?: error("Recording $id is encrypted but no decryption key available")
                    payloadBytes = documentEncryptor.decryptAudio(payloadBytes, key)
                }
                if (isPcm) {
                    // Already raw 16-bit LE PCM — write straight to the cache path
                    SystemFileSystem.sink(cachedPath).buffered().use { sink ->
                        sink.write(payloadBytes)
                    }
                } else {
                    // M4A payload — decode to PCM before caching
                    val decoded = m4aDecoder.decode(payloadBytes)
                    SystemFileSystem.sink(cachedPath).buffered().use { sink ->
                        for (s in decoded.samples) sink.writeShortLe(s)
                    }
                }
            } finally {
                if (SystemFileSystem.exists(m4aTempPath)) {
                    SystemFileSystem.delete(m4aTempPath)
                }
            }

            // Cached file is raw PCM regardless of upload format
            cachedMetadata = CachedRecordingMetadata(id, sampleRate, PCM_MIME)
            cachedMetadataDao.insertOrReplace(cachedMetadata)
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
        val encrypt = preferences.useEncryption.value
        val encryptionKey = if (encrypt) documentEncryptor.getKey() else null
        if (encrypt && encryptionKey == null) {
            logger.w { "Encryption enabled but no key available — uploading unencrypted" }
        }

        for (idToMove in listOf(id, "$id-clean")) {
            val source = Path(getRecordingsCacheDirectory(), idToMove)
            val cachedMetadata = cachedMetadataDao.get(idToMove)
                ?: error("Cached metadata for recording $idToMove not found")
            require(SystemFileSystem.exists(source)) {
                "Recording $idToMove does not exist in cache"
            }

            val samples = readPcmFile(source)
            uploadRecordingSamples(
                id = idToMove,
                sampleRate = cachedMetadata.sampleRate,
                samples = samples,
                encryptionKey = encryptionKey,
            )
        }
    }

    suspend fun uploadRecordingPcm(
        id: String,
        sampleRate: Int,
        pcmBytes: ByteArray,
        encryptionKey: String?,
    ) {
        uploadRecordingSamples(
            id = id,
            sampleRate = sampleRate,
            samples = pcmBytesToShortArray(pcmBytes),
            encryptionKey = encryptionKey,
        )
    }

    /**
     * Read a raw PCM 16-bit little-endian mono file into a ShortArray.
     */
    private fun readPcmFile(path: Path): ShortArray {
        val size = SystemFileSystem.metadataOrNull(path)?.size
            ?: error("Failed to get size of recording at $path")
        val numSamples = (size / 2).toInt()
        val samples = ShortArray(numSamples)
        SystemFileSystem.source(path).buffered().use { src ->
            for (i in 0 until numSamples) {
                samples[i] = src.readShortLe()
            }
        }
        return samples
    }

    private fun pcmBytesToShortArray(bytes: ByteArray): ShortArray {
        require(bytes.size % 2 == 0) { "PCM byte array must contain 16-bit samples" }
        val samples = ShortArray(bytes.size / 2)
        var sampleIndex = 0
        var byteIndex = 0
        while (byteIndex < bytes.size) {
            val lo = bytes[byteIndex].toInt() and 0xFF
            val hi = bytes[byteIndex + 1].toInt()
            samples[sampleIndex] = ((hi shl 8) or lo).toShort()
            sampleIndex++
            byteIndex += 2
        }
        return samples
    }

    private suspend fun uploadRecordingSamples(
        id: String,
        sampleRate: Int,
        samples: ShortArray,
        encryptionKey: String?,
    ) {
        val destination = "recordings/${Firebase.auth.currentUser!!.uid}/$id"
        val m4aBytes = m4aEncoder.encode(samples, sampleRate)
        val uploadBytes = if (encryptionKey != null) {
            documentEncryptor.encryptAudio(m4aBytes, encryptionKey)
        } else {
            m4aBytes
        }

        val m4aTempPath = Path(getRecordingsCacheDirectory(), "$id.upload.m4a")
        SystemFileSystem.sink(m4aTempPath).buffered().use { it.write(uploadBytes) }

        val customMeta = mutableMapOf(
            "sampleRate" to sampleRate.toString()
        )
        if (encryptionKey != null) {
            customMeta["encrypted"] = "true"
            customMeta["keyFingerprint"] =
                coredevices.ring.encryption.AesGcmCrypto.keyFingerprint(encryptionKey)
        }

        try {
            Firebase.storage.reference(destination)
                .putFile(
                    getFirebaseStorageFile(m4aTempPath),
                    FirebaseStorageMetadata(
                        contentType = M4A_MIME,
                        customMetadata = customMeta
                    )
                )
        } finally {
            if (SystemFileSystem.exists(m4aTempPath)) {
                SystemFileSystem.delete(m4aTempPath)
            }
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

    /**
     * Delete a recording's audio file from Firebase Storage.
     */
    /**
     * Delete all cached recording metadata from the database.
     */
    suspend fun deleteAllCachedMetadata() {
        cachedMetadataDao.deleteAll()
        logger.i { "Deleted all cached recording metadata" }
    }

    /**
     * Clear all files from the recordings cache directory.
     */
    fun clearCacheDirectory() {
        val cacheDir = getRecordingsCacheDirectory()
        try {
            val entries = SystemFileSystem.list(cacheDir)
            for (entry in entries) {
                try {
                    SystemFileSystem.delete(entry, false)
                } catch (_: Exception) { }
            }
            logger.i { "Cleared ${entries.size} files from cache directory" }
        } catch (e: Exception) {
            logger.w { "Failed to clear cache directory: ${e.message}" }
        }
    }

    suspend fun deleteFromFirebaseStorage(id: String) {
        val path = "recordings/${Firebase.auth.currentUser!!.uid}/$id"
        try {
            Firebase.storage.reference(path).delete()
        } catch (e: Exception) {
            logger.w { "Failed to delete Storage file $id: ${e.message}" }
        }
    }

    /**
     * Encrypt a cached audio file and re-upload to Firebase Storage, overwriting the existing file.
     * Used during encryption migration.
     * @return true if encrypted and uploaded, false if file not found in cache/storage
     */
    suspend fun encryptAndReuploadAudio(id: String, encryptionKey: String): Boolean {
        val cachedPath = Path(getRecordingsCacheDirectory(), id)
        val cachedMetadata = cachedMetadataDao.get(id)

        // If not in local cache, try to download first
        if (!SystemFileSystem.exists(cachedPath) || cachedMetadata == null) {
            try {
                openRecordingSource(id) // downloads + caches
            } catch (e: Exception) {
                logger.w { "Cannot download audio $id for encryption: ${e.message}" }
                return false
            }
        }

        val meta = cachedMetadataDao.get(id) ?: return false
        if (!SystemFileSystem.exists(cachedPath)) return false

        // Read plaintext bytes from cache
        val fileSize = SystemFileSystem.metadataOrNull(cachedPath)?.size?.toInt() ?: return false
        val plainBytes = ByteArray(fileSize)
        SystemFileSystem.source(cachedPath).buffered().use { src ->
            var offset = 0
            while (offset < fileSize) {
                val read = src.readAtMostTo(plainBytes, offset, fileSize)
                if (read == -1) break
                offset += read
            }
        }

        uploadRecordingPcm(
            id = id,
            sampleRate = meta.sampleRate,
            pcmBytes = plainBytes,
            encryptionKey = encryptionKey,
        )

        logger.i { "Re-encoded, encrypted, and re-uploaded audio $id from ${plainBytes.size} bytes of PCM" }
        return true
    }
}
