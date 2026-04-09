package coredevices.ring.util.trace

import coredevices.ring.data.entity.room.TraceEntryEntity
import coredevices.ring.data.entity.room.TraceEventData
import coredevices.ring.data.entity.room.TraceSessionEntity
import coredevices.ring.database.room.dao.TraceEntryDao
import coredevices.ring.database.room.dao.TraceSessionDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.TimeSource

@OptIn(FlowPreview::class)
class RingTraceSession(
    private val entryDao: TraceEntryDao,
    private val sessionDao: TraceSessionDao,
) {
    private val startMark = TimeSource.Monotonic.markNow()
    private val sessionScope = CoroutineScope(Dispatchers.Default)
    private val pending = mutableListOf<TraceEntryEntity>()
    private val pendingMutex = Mutex()
    private var flushJob: Job? = null
    private val startTime = Clock.System.now()
    private var sessionId = -1L

    private suspend fun flush() {
        pendingMutex.withLock {
            if (pending.isEmpty()) return
            if (sessionId == -1L) {
                sessionId = sessionDao.insertTraceSession(
                    TraceSessionEntity(
                        id = 0L,
                        started = startTime
                    )
                )
            }
            val toInsert = pending.toList().map {
                it.copy(sessionId = sessionId)
            }
            pending.clear()
            withContext(Dispatchers.IO) {
                entryDao.insertAll(toInsert)
            }
        }
    }

    fun markEvent(type: String, data: TraceEventData? = null) {
        val timeMark = startMark.elapsedNow().inWholeMilliseconds
        sessionScope.launch {
            val traceEntry = TraceEntryEntity(
                id = 0L,
                sessionId = -1L,
                timeMark = timeMark,
                type = type,
                data = data?.let { serialize(it) }
            )
            pendingMutex.withLock {
                pending.add(traceEntry)
            }
        }
        flushJob?.cancel()
        flushJob = sessionScope.launch {
            delay(500)
            flush()
        }
    }

    suspend fun close() {
        flushJob?.cancel()
        // Wait for any in-flight markEvent coroutines to finish adding to pending
        sessionScope.coroutineContext[Job]?.children?.forEach { it.join() }
        flush()
        sessionScope.cancel()
    }

    private inline fun <reified T : TraceEventData> serialize(data: T): String {
        return Json.encodeToString<T>(data)
    }
}