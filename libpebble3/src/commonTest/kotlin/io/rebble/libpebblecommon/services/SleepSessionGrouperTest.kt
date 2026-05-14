package io.rebble.libpebblecommon.services

import io.rebble.libpebblecommon.database.entity.OverlayDataEntity
import io.rebble.libpebblecommon.health.OverlayType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun overlay(start: Long, duration: Long, type: OverlayType) = OverlayDataEntity(
    startTime = start,
    duration = duration,
    type = type.value,
    steps = 0,
    restingKiloCalories = 0,
    activeKiloCalories = 0,
    distanceCm = 0,
    offsetUTC = 0,
)

private fun sleep(start: Long, duration: Long) = overlay(start, duration, OverlayType.Sleep)
private fun deep(start: Long, duration: Long) = overlay(start, duration, OverlayType.DeepSleep)

class SleepSessionGrouperTest {
    @Test
    fun emptyInput_returnsEmpty() {
        assertTrue(groupSleepSessions(emptyList()).isEmpty())
    }

    @Test
    fun singleSleepContainer_oneSessionOneLightInterval() {
        val sessions = groupSleepSessions(listOf(sleep(start = 1000, duration = 3600)))
        assertEquals(1, sessions.size)
        val s = sessions[0]
        assertEquals(1000, s.start)
        assertEquals(4600, s.end)
        assertEquals(3600, s.totalSleep)
        assertEquals(0, s.deepSleep)
        assertEquals(listOf(SleepInterval(1000, 4600, isDeep = false)), s.intervals.toList())
    }

    @Test
    fun sleepWithNestedDeep_correctIntervalsAndTotals() {
        val sessions = groupSleepSessions(listOf(
            sleep(start = 1000, duration = 3600),    // 1 hour starting at t=1000
            deep(start = 1500, duration = 600),       // 10 minutes at t=1500 (nested inside Sleep)
        ))
        assertEquals(1, sessions.size)
        val s = sessions[0]
        // Deep is nested inside the Sleep container, so its duration counts toward
        // deepSleep but NOT additionally toward totalSleep.
        assertEquals(3600, s.totalSleep)
        assertEquals(600, s.deepSleep)
        assertEquals(2, s.intervals.size)
        assertEquals(false, s.intervals[0].isDeep)
        assertEquals(true, s.intervals[1].isDeep)
        assertEquals(1500, s.intervals[1].start)
        assertEquals(2100, s.intervals[1].end)
    }

    @Test
    fun gapWellUnderThreshold_singleSession() {
        // 30-minute gap: under SLEEP_SESSION_GAP_HOURS=1h, so the two Sleep containers
        // remain in one session (with the awake gap preserved as separate intervals).
        val sessions = groupSleepSessions(listOf(
            sleep(start = 0, duration = 3600),
            sleep(start = 3600 + 30 * 60, duration = 3600),
        ))
        assertEquals(1, sessions.size)
        assertEquals(2, sessions[0].intervals.size)
        assertEquals(7200, sessions[0].totalSleep)
    }

    @Test
    fun gapExactlyAtThreshold_singleSession() {
        // SLEEP_SESSION_GAP_HOURS uses an inclusive boundary (`<=`), so a gap of exactly
        // 1h still groups into one session.
        val sessions = groupSleepSessions(listOf(
            sleep(start = 0, duration = 3600),
            sleep(start = 3600 + 3600, duration = 3600),
        ))
        assertEquals(1, sessions.size)
    }

    @Test
    fun gapBeyondThreshold_splitsIntoTwoSessions() {
        // 1 second past the threshold → two separate sessions.
        val sessions = groupSleepSessions(listOf(
            sleep(start = 0, duration = 3600),
            sleep(start = 3600 + 3601, duration = 3600),
        ))
        assertEquals(2, sessions.size)
        assertEquals(1, sessions[0].intervals.size)
        assertEquals(1, sessions[1].intervals.size)
    }

    @Test
    fun unsortedInput_handledByInternalSort() {
        // Caller may pass entries in any order; the grouper sorts by startTime internally.
        val sessions = groupSleepSessions(listOf(
            deep(start = 1500, duration = 600),
            sleep(start = 1000, duration = 3600),
        ))
        assertEquals(1, sessions.size)
        assertEquals(1000, sessions[0].start)
        assertEquals(false, sessions[0].intervals[0].isDeep)
        assertEquals(true, sessions[0].intervals[1].isDeep)
    }

    @Test
    fun mob6591Data_oneSessionWithAllNineIntervals() {
        // Mirrors the user's reported data exactly: two Sleep containers separated by a
        // 14-min awake gap (one session), with seven DeepSleep entries interspersed.
        // Verifies that all overlay entries are preserved as intervals and that the
        // totals match the values seen in the user's logs.
        val s1Start = 1777151100L
        val s1Dur = (5.4 * 3600).toLong()
        val s2Start = s1Start + s1Dur + 14 * 60L
        val s2Dur = (4.6 * 3600).toLong()

        val entries = listOf(
            sleep(s1Start, s1Dur),
            deep(s1Start + 6420, (0.7 * 3600).toLong()),
            deep(s1Start + 11580, (0.4 * 3600).toLong()),
            deep(s1Start + 14160, (0.4 * 3600).toLong()),
            deep(s1Start + 15540, (0.4 * 3600).toLong()),
            sleep(s2Start, s2Dur),
            deep(s2Start + (1.3 * 3600).toLong(), (0.8 * 3600).toLong()),
            deep(s2Start + (2.2 * 3600).toLong(), (0.4 * 3600).toLong()),
            deep(s2Start + (3.3 * 3600).toLong(), (0.5 * 3600).toLong()),
        )
        val sessions = groupSleepSessions(entries)

        assertEquals(1, sessions.size, "14-min gap is well under threshold; expect single session")
        val s = sessions[0]
        assertEquals(9, s.intervals.size)
        assertEquals(2, s.intervals.count { !it.isDeep })
        assertEquals(7, s.intervals.count { it.isDeep })
        // Totals match the user's log line: total=10.0h, deep=3.6h
        assertEquals((10.0 * 3600).toLong(), s.totalSleep)
        assertEquals((3.6 * 3600).toLong(), s.deepSleep)
    }

    @Test
    fun mob6592Data_twoSessionsEachWithItsOwnIntervals() {
        // Mirrors the MOB-6592 reporter's data: 4.5h Sleep, 1h 46m gap, 4.1h Sleep.
        val s1Start = 1777147860L
        val s1Dur = (4.5 * 3600).toLong()
        val s2Start = s1Start + s1Dur + (1 * 3600 + 46 * 60L)
        val s2Dur = (4.1 * 3600).toLong()

        val entries = listOf(
            sleep(s1Start, s1Dur),
            deep(s1Start + 240, (0.6 * 3600).toLong()),
            sleep(s2Start, s2Dur),
            deep(s2Start + (1.4 * 3600).toLong(), (0.7 * 3600).toLong()),
        )
        val sessions = groupSleepSessions(entries)

        assertEquals(2, sessions.size)
        assertEquals(s1Start, sessions[0].start)
        assertEquals(s2Start, sessions[1].start)
        // Each session keeps only its own intervals.
        assertEquals(2, sessions[0].intervals.size)
        assertEquals(2, sessions[1].intervals.size)
        assertEquals(s1Dur, sessions[0].totalSleep)
        assertEquals(s2Dur, sessions[1].totalSleep)
    }
}
