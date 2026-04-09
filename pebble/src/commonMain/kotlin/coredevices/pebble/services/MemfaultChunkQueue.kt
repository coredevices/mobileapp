package coredevices.pebble.services

import co.touchlab.kermit.Logger
import coredevices.database.MemfaultChunkDao
import coredevices.database.MemfaultChunkEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

private data class PendingChunk(val serial: String, val bytes: ByteArray)

class MemfaultChunkQueue(
    private val memfault: Memfault,
    private val dao: MemfaultChunkDao,
) {
    private val logger = Logger.withTag("MemfaultChunkQueue")
    private val channel = Channel<PendingChunk>(Channel.UNLIMITED)
    private val uploadMutex = Mutex()

    // Non-suspending — safe to call from any context
    fun enqueue(serial: String, bytes: ByteArray) {
        channel.trySend(PendingChunk(serial, bytes))
    }

    fun startProcessing(scope: CoroutineScope) {
        scope.launch {
            // Upload any chunks left over from a previous run first, before accepting new ones
            uploadPendingFromDb()

            while (true) {
                // Block until the first chunk arrives; persist to DB immediately to establish order
                persistToDb(channel.receive())
                var collected = 1

                // Keep collecting until idle for IDLE_TIMEOUT or we hit the force-flush limit
                while (collected < MAX_COLLECT_BEFORE_FLUSH) {
                    val next = withTimeoutOrNull(IDLE_TIMEOUT) { channel.receive() }
                    next?.let { persistToDb(it); collected++ } ?: break
                }

                // Upload everything in the DB in insertion order (includes any previously failed chunks)
                uploadPendingFromDb()
            }
        }
    }

    // Called externally (e.g. background sync) to retry any chunks that failed to upload.
    // The mutex ensures this doesn't run concurrently with the processing loop's own upload pass.
    suspend fun uploadPendingFromDb() = uploadMutex.withLock {
        evictOldestIfOverLimit()
        dao.getPendingSerials().forEach { serial ->
            val chunks = dao.getChunksForSerial(serial)
            if (chunks.isEmpty()) return@forEach
            chunks.chunked(MAX_CHUNKS_PER_REQUEST).forEach { batch ->
                val success = memfault.uploadChunkBatch(batch.map { it.chunkData }, serial)
                if (success) {
                    dao.deleteByIds(batch.map { it.id })
                } else {
                    logger.w { "uploadChunkBatch failed for serial=$serial; ${chunks.size} chunk(s) will be retried" }
                    return@forEach  // Don't skip ahead — retry from same position next time
                }
            }
        }
    }

    private suspend fun evictOldestIfOverLimit() {
        val count = dao.count()
        if (count > MAX_STORED_CHUNKS) {
            val excess = count - MAX_STORED_CHUNKS
            logger.w { "Chunk DB has $count entries (limit $MAX_STORED_CHUNKS), evicting $excess oldest" }
            dao.deleteOldest(excess)
        }
    }

    private suspend fun persistToDb(chunk: PendingChunk) {
        dao.insert(MemfaultChunkEntity(
            serial = chunk.serial,
            chunkData = chunk.bytes,
            createdAt = Clock.System.now().toEpochMilliseconds(),
        ))
    }

    companion object {
        private val IDLE_TIMEOUT = 10.seconds
        private const val MAX_COLLECT_BEFORE_FLUSH = 1000  // force flush if chunks never stop arriving
        private const val MAX_CHUNKS_PER_REQUEST = 100      // Memfault multipart limit per HTTP call
        private const val MAX_STORED_CHUNKS = 5000L          // evict oldest beyond this limit
    }
}
