package coredevices.ring.ui.viewmodel

import coredevices.ring.data.entity.room.clock.LocalClockEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class LocalClockViewModelTest {

    private val now = Instant.parse("2026-10-30T10:00:00Z")

    private fun entry(id: String, kind: LocalClockEntry.Kind, fireAt: Instant) =
        LocalClockEntry(id = id, kind = kind, fireAt = fireAt, createdAt = now)

    @Test
    fun computeSplitsPendingEntriesByKindSoonestFirst() {
        val laterTimer = entry("t2", LocalClockEntry.Kind.Timer, now + 30.minutes)
        val soonTimer = entry("t1", LocalClockEntry.Kind.Timer, now + 5.minutes)
        val alarm = entry("a1", LocalClockEntry.Kind.Alarm, now + 8.hours)

        val state = LocalClockViewModel.compute(listOf(laterTimer, alarm, soonTimer), now)

        assertEquals(listOf("t1", "t2"), state.timers.map { it.entry.id })
        assertEquals(listOf(5.minutes, 30.minutes), state.timers.map { it.remaining })
        assertEquals(listOf(alarm), state.alarms)
    }

    @Test
    fun computeDropsEntriesThatHaveAlreadyFired() {
        val fired = entry("t0", LocalClockEntry.Kind.Timer, now - 1.seconds)
        val dueNow = entry("a0", LocalClockEntry.Kind.Alarm, now)
        val state = LocalClockViewModel.compute(listOf(fired, dueNow), now)
        assertEquals(true, state.isEmpty)
    }

    @Test
    fun formatCountdown() {
        assertEquals("0:07", LocalClockViewModel.formatCountdown(7.seconds))
        assertEquals("12:05", LocalClockViewModel.formatCountdown(12.minutes + 5.seconds))
        assertEquals("1:02:05", LocalClockViewModel.formatCountdown(1.hours + 2.minutes + 5.seconds))
        assertEquals("0:00", LocalClockViewModel.formatCountdown((-3).seconds))
    }
}
