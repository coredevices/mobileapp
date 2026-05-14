package coredevices.pebble.services

import co.touchlab.kermit.Logger
import com.russhwolf.settings.Settings
import coredevices.database.AnalyticsHeartbeatDao
import coredevices.database.AnalyticsHeartbeatEntity
import coredevices.pebble.ui.SettingsKeys.KEY_ENABLE_MEMFAULT_UPLOADS
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.TimeZone
import kotlinx.datetime.offsetAt
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

private data class PendingHeartbeat(
    val serial: String,
    val fwVersion: String?,
    val tzOffsetMinutes: Int,
    val payload: ByteArray,
)

class AnalyticsHeartbeatQueue(
    private val ingest: AnalyticsIngest,
    private val dao: AnalyticsHeartbeatDao,
    private val settings: Settings,
) {
    private val logger = Logger.withTag("AnalyticsHeartbeatQueue")
    private val channel = Channel<PendingHeartbeat>(Channel.UNLIMITED)
    private val uploadMutex = Mutex()

    // Non-suspending — safe to call from any context
    fun enqueue(serial: String, fwVersion: String?, payload: ByteArray) {
        if (!settings.getBoolean(KEY_ENABLE_MEMFAULT_UPLOADS, true)) {
            logger.d { "Not uploading Memfault chunks (disabled in settings)" }
            return
        }
        logger.v { "enqueue heartbeat: serial=$serial fwVersion=$fwVersion" }
        val tzOffsetMinutes = TimeZone.currentSystemDefault()
            .offsetAt(Clock.System.now())
            .totalSeconds / 60
        channel.trySend(PendingHeartbeat(serial, fwVersion, tzOffsetMinutes, payload))
    }

    fun startProcessing(scope: CoroutineScope) {
        scope.launch {
            // Drain any heartbeats left over from a previous run before accepting new ones
            uploadPendingFromDb()

            while (true) {
                // Block until the first item arrives; persist immediately to establish order
                persistToDb(channel.receive())
                var collected = 1

                // Keep collecting until idle for IDLE_TIMEOUT or we hit the force-flush limit
                while (collected < MAX_COLLECT_BEFORE_FLUSH) {
                    val next = withTimeoutOrNull(IDLE_TIMEOUT) { channel.receive() }
                    next?.let { persistToDb(it); collected++ } ?: break
                }

                uploadPendingFromDb()
            }
        }
    }

    // Called externally (e.g. background sync) to retry any rows that failed to upload.
    // The mutex ensures this doesn't run concurrently with the processing loop's own upload pass.
    suspend fun uploadPendingFromDb() = uploadMutex.withLock {
        evictOldestIfOverLimit()
        while (true) {
            val batch = dao.getBatch(UPLOAD_BATCH_SIZE)
            if (batch.isEmpty()) return@withLock
            var uploaded = 0
            for (row in batch) {
                val success = ingest.uploadHeartbeat(row)
                if (success) {
                    dao.deleteByIds(listOf(row.id))
                    uploaded++
                } else {
                    logger.w { "uploadHeartbeat failed for serial=${row.serial}; will retry later" }
                    return@withLock
                }
            }
            logger.d { "Uploaded $uploaded heartbeats of ${batch.size} from DB" }
        }
    }

    private suspend fun evictOldestIfOverLimit() {
        val count = dao.count()
        if (count > MAX_STORED_HEARTBEATS) {
            val excess = count - MAX_STORED_HEARTBEATS
            logger.w { "Heartbeat DB has $count entries (limit $MAX_STORED_HEARTBEATS), evicting $excess oldest" }
            dao.deleteOldest(excess)
        }
    }

    private suspend fun persistToDb(item: PendingHeartbeat) {
        dao.insert(AnalyticsHeartbeatEntity(
            serial = item.serial,
            fwVersion = item.fwVersion,
            tzOffsetMinutes = item.tzOffsetMinutes,
            payload = item.payload,
            createdAt = Clock.System.now().toEpochMilliseconds(),
        ))
    }

    companion object {
        private val IDLE_TIMEOUT = 10.seconds
        private const val MAX_COLLECT_BEFORE_FLUSH = 50
        private const val UPLOAD_BATCH_SIZE = 100
        private const val MAX_STORED_HEARTBEATS = 500L
    }
}
