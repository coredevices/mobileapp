package coredevices.coreapp.ring.trace

import coredevices.ring.data.entity.room.TraceEntryEntity
import coredevices.ring.data.entity.room.TraceEventData
import coredevices.ring.data.entity.room.TraceSessionEntity
import coredevices.ring.database.room.dao.TraceEntryDao
import coredevices.ring.database.room.dao.TraceSessionDao
import coredevices.ring.util.trace.RingTraceSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.time.Clock

// region Fakes

class FakeTraceSessionDao : TraceSessionDao {
    val sessions = mutableListOf<TraceSessionEntity>()
    private var nextId = 1L

    override suspend fun insertTraceSession(traceSession: TraceSessionEntity): Long {
        val id = nextId++
        sessions.add(traceSession.copy(id = id))
        return id
    }

    override suspend fun getTraceSessionById(id: Long): TraceSessionEntity? =
        sessions.firstOrNull { it.id == id }

    override suspend fun getLastNTraceSessions(limit: Int, offset: Int): List<TraceSessionEntity> =
        sessions.sortedByDescending { it.started }.drop(offset).take(limit)
}

class FakeTraceEntryDao : TraceEntryDao {
    val entries = mutableListOf<TraceEntryEntity>()
    private var nextId = 1L

    override suspend fun insertTraceEntry(traceEntry: TraceEntryEntity): Long {
        val id = nextId++
        entries.add(traceEntry.copy(id = id))
        return id
    }

    override suspend fun insertAll(traceEntries: List<TraceEntryEntity>): List<Long> =
        traceEntries.map { insertTraceEntry(it) }
}

// endregion

class RingTraceSessionTest {

    private lateinit var sessionDao: FakeTraceSessionDao
    private lateinit var entryDao: FakeTraceEntryDao
    private lateinit var scope: CoroutineScope

    @Before
    fun setUp() {
        sessionDao = FakeTraceSessionDao()
        entryDao = FakeTraceEntryDao()
        scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    private fun entriesForSession(sessionId: Long) =
        entryDao.entries.filter { it.sessionId == sessionId }

    @Test
    fun closeFlushesSessionAndEntriesImmediately() = runBlocking {
        val session = RingTraceSession(entryDao, sessionDao)

        session.markEvent("transfer_started")
        session.close()

        assertEquals(1, sessionDao.sessions.size)
        val entries = entriesForSession(sessionDao.sessions.first().id)
        assertEquals(1, entries.size)
        assertEquals("transfer_started", entries.first().type)
    }

    @Test
    fun multipleEventsAreAllFlushedOnClose() = runBlocking {
        val session = RingTraceSession(entryDao, sessionDao)

        session.markEvent("event_a")
        session.markEvent("event_b")
        session.markEvent("event_c")
        session.close()

        assertEquals(1, sessionDao.sessions.size)
        val entries = entriesForSession(sessionDao.sessions.first().id)
        assertEquals(3, entries.size)
        assertEquals(setOf("event_a", "event_b", "event_c"), entries.map { it.type }.toSet())
    }

    @Test
    fun debounceFlushWritesToDatabaseAfterDelay() = runBlocking {
        val session = RingTraceSession(entryDao, sessionDao)

        session.markEvent("debounced_event")
        // Wait past the 500ms debounce
        delay(800)

        assertEquals(1, sessionDao.sessions.size)
        val entries = entriesForSession(sessionDao.sessions.first().id)
        assertEquals(1, entries.size)
        assertEquals("debounced_event", entries.first().type)
    }

    @Test
    fun rapidEventsResetDebounceAndOnlyFlushOnce() = runBlocking {
        val session = RingTraceSession(entryDao, sessionDao)

        // Fire events every 80ms (well within 500ms debounce window)
        repeat(5) { i ->
            session.markEvent("event_$i")
            delay(80)
        }
        // Let the single debounced flush fire
        delay(700)

        assertEquals(1, sessionDao.sessions.size)
        assertEquals(5, entryDao.entries.size)
    }

    @Test
    fun sessionNotCreatedUntilFirstFlush() = runBlocking {
        @Suppress("UNUSED_VARIABLE")
        val session = RingTraceSession(entryDao, sessionDao)

        assertEquals(0, sessionDao.sessions.size)
    }

    @Test
    fun closingWithNoEventsCreatesNoSession() = runBlocking {
        val session = RingTraceSession(entryDao, sessionDao)
        session.close()

        assertEquals(0, sessionDao.sessions.size)
        assertEquals(0, entryDao.entries.size)
    }

    @Test
    fun eventDataIsSerializedCorrectly() = runBlocking {
        val session = RingTraceSession(entryDao, sessionDao)

        val expected = TraceEventData.TransferStarted(satellite = "sat-abc", rollover = true)
        session.markEvent("transfer_started", expected)
        session.close()

        val entries = entriesForSession(sessionDao.sessions.first().id)
        assertEquals(1, entries.size)
        val rawData = entries.first().data
        assertNotNull(rawData)
        val decoded = Json.decodeFromString<TraceEventData>(rawData!!)
        assertEquals(expected, decoded)
    }

    @Test
    fun eventWithNoDataHasNullDataField() = runBlocking {
        val session = RingTraceSession(entryDao, sessionDao)

        session.markEvent("plain_event", data = null)
        session.close()

        val entries = entriesForSession(sessionDao.sessions.first().id)
        assertEquals(1, entries.size)
        assertNull(entries.first().data)
    }

    @Test
    fun timeMarksAreNonNegativeAndNonDecreasing() = runBlocking {
        val session = RingTraceSession(entryDao, sessionDao)

        session.markEvent("first")
        delay(10)
        session.markEvent("second")
        session.close()

        val entries = entriesForSession(sessionDao.sessions.first().id).sortedBy { it.timeMark }
        assertEquals(2, entries.size)
        assertTrue(entries[0].timeMark >= 0)
        assertTrue(entries[1].timeMark >= entries[0].timeMark)
    }

    @Test
    fun allEntriesAreAssignedTheCorrectSessionId() = runBlocking {
        val session = RingTraceSession(entryDao, sessionDao)

        session.markEvent("e1")
        session.markEvent("e2")
        session.close()

        val sessionId = sessionDao.sessions.first().id
        assertTrue(entryDao.entries.all { it.sessionId == sessionId })
    }

    @Test
    fun twoIndependentSessionsAreStoredSeparately() = runBlocking {
        val session1 = RingTraceSession(entryDao, sessionDao)
        session1.markEvent("s1_event")
        session1.close()

        val session2 = RingTraceSession(entryDao, sessionDao)
        session2.markEvent("s2_event_a")
        session2.markEvent("s2_event_b")
        session2.close()

        assertEquals(2, sessionDao.sessions.size)
        val (id1, id2) = sessionDao.sessions.map { it.id }
        assertNotEquals(id1, id2)
        assertEquals(1, entriesForSession(id1).size)
        assertEquals(2, entriesForSession(id2).size)
    }

    // Events added after a debounce flush has already created the session should
    // land in the same session, not create a second one.
    @Test
    fun eventsAfterDebounceFlushReuseSession() = runBlocking {
        val session = RingTraceSession(entryDao, sessionDao)

        session.markEvent("first")
        delay(800) // debounce fires, session + first entry written

        session.markEvent("second")
        session.close() // should reuse the already-created session

        assertEquals(1, sessionDao.sessions.size)
        assertEquals(2, entryDao.entries.size)
        val sessionId = sessionDao.sessions.first().id
        assertTrue(entryDao.entries.all { it.sessionId == sessionId })
    }

    // The session's started timestamp should reflect when the RingTraceSession was
    // constructed, not when the first flush happens.
    @Test
    fun sessionStartedTimestampReflectsConstructionTime() = runBlocking {
        val before = Clock.System.now()
        val session = RingTraceSession(entryDao, sessionDao)
        val after = Clock.System.now()

        session.markEvent("e")
        session.close()

        val started = sessionDao.sessions.first().started
        assertTrue(
            "started ($started) should be >= construction time ($before)",
            started >= before
        )
        assertTrue(
            "started ($started) should be <= time after construction ($after)",
            started <= after
        )
    }

    // Concurrent markEvent calls must not lose entries due to mutex contention.
    @Test
    fun concurrentMarkEventsAreAllCaptured() = runBlocking {
        val session = RingTraceSession(entryDao, sessionDao)
        val eventCount = 50

        // coroutineScope suspends until all children complete, ensuring every
        // markEvent call has been dispatched before we move on to close().
        coroutineScope {
            repeat(eventCount) { i ->
                launch(Dispatchers.Default) { session.markEvent("concurrent_$i") }
            }
        }
        session.close()

        assertEquals(eventCount, entryDao.entries.size)
        assertEquals(1, sessionDao.sessions.size)
    }

    // Calling markEvent after close() should be a silent no-op, not a crash.
    @Test
    fun markEventAfterCloseIsIgnored() = runBlocking {
        val session = RingTraceSession(entryDao, sessionDao)
        session.markEvent("before_close")
        session.close()

        val countAfterClose = entryDao.entries.size

        session.markEvent("after_close")
        delay(800) // wait for any debounce that might fire

        assertEquals(countAfterClose, entryDao.entries.size)
    }

    // Every concrete TraceEventData subtype must survive a serialize → deserialize
    // round-trip. TransferDroppedUnrecoverable is intentionally included to catch
    // the missing @Serializable / @SerialName annotations on that class.
    @Test
    fun allEventDataSubtypesSerializeCorrectly() = runBlocking {
        val now = Clock.System.now()
        val cases: List<TraceEventData> = listOf(
            TraceEventData.TransferStarted(satellite = "sat-1", rollover = false),
            TraceEventData.TransferTypeDetermined(
                satellite = "sat-1",
                isAudio = true,
                buttonSequence = "single",
                collectionStartIndex = 0,
                collectionIndex = 3,
                final = true,
                advertisementReceivedTimestamp = now,
                lifetimeCollectionCount = 10,
            ),
            TraceEventData.PastTransferFailed(satellite = "sat-1", transferId = 42L),
            TraceEventData.TransferDroppedRecoverable(satellite = "sat-1", collectionIndex = 2),
            TraceEventData.TransferDroppedUnrecoverable(
                satellite = "sat-1",
                transferId = 7L,
                indices = listOf(1, 2, 3)
            ),
            TraceEventData.TransferProgress(
                transferId = 99L,
                startIndex = 0,
                endIndex = 10,
                reportedProgress = 0.5f,
            ),
            TraceEventData.TransferCompleted(
                transferId = 99L,
                audioDurationSeconds = 3.14f,
                buttonReleaseTimestamp = now,
            ),
        )

        cases.forEach { eventData ->
            val session = RingTraceSession(entryDao, sessionDao)
            session.markEvent(eventData::class.simpleName ?: "unknown", eventData)
            session.close()
        }

        val allEntries = entryDao.entries.filter { it.data != null }
        assertEquals(cases.size, allEntries.size)

        allEntries.zip(cases).forEach { (entry, expected) ->
            val decoded = Json.decodeFromString<TraceEventData>(entry.data!!)
            assertEquals(
                "Round-trip failed for ${expected::class.simpleName}",
                expected,
                decoded
            )
        }
    }
}
