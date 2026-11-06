package coredevices.ring.clock

import coredevices.ring.data.entity.room.clock.LocalClockEntry
import coredevices.ring.database.room.dao.LocalClockDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class LocalClockTest {

    private val now = Instant.parse("2026-10-30T10:00:00Z")
    private val dao = FakeLocalClockDao()
    private val notifier = FakeLocalClockNotifier()
    private val localClock = LocalClock(dao, notifier, FixedClock(now))

    @Test
    fun setTimerArmsNotificationAndRecordsRow() = runTest {
        val entry = localClock.setTimer(20.minutes, label = "  pasta ")

        assertEquals(LocalClockEntry.Kind.Timer, entry.kind)
        assertEquals(now + 20.minutes, entry.fireAt)
        assertEquals(20.minutes.inWholeMilliseconds, entry.durationMillis)
        assertEquals("pasta", entry.label)
        assertEquals(listOf(entry), dao.rows.value)
        assertEquals(listOf(entry.id), notifier.scheduled)
    }

    @Test
    fun blankLabelIsDropped() = runTest {
        assertNull(localClock.setTimer(1.minutes, label = "   ").label)
    }

    @Test
    fun setTimerRejectsNonPositiveDuration() = runTest {
        assertFailsWith<IllegalArgumentException> { localClock.setTimer(0.seconds) }
        assertTrue(dao.rows.value.isEmpty())
        assertTrue(notifier.scheduled.isEmpty())
    }

    @Test
    fun schedulingFailureLeavesNoRow() = runTest {
        notifier.failNext = true
        assertFailsWith<IllegalStateException> { localClock.setTimer(5.minutes) }
        assertTrue(dao.rows.value.isEmpty())
    }

    @Test
    fun setAlarmRejectsInvalidTime() = runTest {
        assertFailsWith<IllegalArgumentException> { localClock.setAlarm(24, 0) }
        assertFailsWith<IllegalArgumentException> { localClock.setAlarm(7, 60) }
        assertTrue(dao.rows.value.isEmpty())
    }

    @Test
    fun setAlarmRecordsAlarmWithoutDuration() = runTest {
        val entry = localClock.setAlarm(7, 30, label = "Wake up")
        assertEquals(LocalClockEntry.Kind.Alarm, entry.kind)
        assertNull(entry.durationMillis)
        assertEquals("Wake up", entry.label)
        assertEquals(listOf(entry.id), notifier.scheduled)
        assertEquals(listOf(entry), dao.rows.value)
    }

    @Test
    fun nextOccurrenceIsTodayWhenTimeStillAhead() {
        assertEquals(
            Instant.parse("2026-10-30T11:00:00Z"),
            LocalClock.nextOccurrence(LocalTime(11, 0), now, TimeZone.UTC),
        )
    }

    @Test
    fun nextOccurrenceIsTomorrowWhenTimeHasPassed() {
        assertEquals(
            Instant.parse("2026-10-31T09:00:00Z"),
            LocalClock.nextOccurrence(LocalTime(9, 0), now, TimeZone.UTC),
        )
    }

    @Test
    fun nextOccurrenceIsTomorrowWhenTimeIsExactlyNow() {
        assertEquals(
            Instant.parse("2026-10-31T10:00:00Z"),
            LocalClock.nextOccurrence(LocalTime(10, 0), now, TimeZone.UTC),
        )
    }

    @Test
    fun cancelDisarmsAndDeletesRow() = runTest {
        val entry = localClock.setTimer(5.minutes)
        localClock.cancel(entry.id)
        assertEquals(listOf(entry.id), notifier.cancelled)
        assertTrue(dao.rows.value.isEmpty())
    }

    @Test
    fun pruneFiredDropsOnlyEntriesThatHaveGoneOff() = runTest {
        val fired = localClock.setTimer(5.minutes)
        val pending = localClock.setTimer(15.minutes)
        val later = LocalClock(dao, notifier, FixedClock(now + 10.minutes))

        later.pruneFired()

        assertEquals(listOf(pending), dao.rows.value)
        assertTrue(fired.id !in dao.rows.value.map { it.id })
    }
}

private class FixedClock(private val instant: Instant) : Clock {
    override fun now(): Instant = instant
}

internal class FakeLocalClockNotifier : LocalClockNotifier {
    val scheduled = mutableListOf<String>()
    val cancelled = mutableListOf<String>()
    var failNext = false

    override suspend fun schedule(entry: LocalClockEntry) {
        if (failNext) {
            failNext = false
            error("notification permission not granted")
        }
        scheduled += entry.id
    }

    override suspend fun cancel(id: String) {
        cancelled += id
    }
}

internal class FakeLocalClockDao : LocalClockDao {
    val rows = MutableStateFlow<List<LocalClockEntry>>(emptyList())

    override suspend fun upsert(entry: LocalClockEntry) {
        rows.value = (rows.value.filter { it.id != entry.id } + entry).sortedBy { it.fireAt }
    }

    override fun getAllFlow(): Flow<List<LocalClockEntry>> = rows

    override suspend fun getAll(): List<LocalClockEntry> = rows.value

    override suspend fun delete(id: String) {
        rows.value = rows.value.filter { it.id != id }
    }

    override suspend fun deleteFiredBefore(before: Instant) {
        rows.value = rows.value.filter { it.fireAt > before }
    }
}
