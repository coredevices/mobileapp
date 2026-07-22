package io.rebble.libpebblecommon.services

import io.rebble.libpebblecommon.database.entity.HealthDataEntity
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class HealthStatsSyncTest {
    @Test
    fun buildWeekdayTypicalsFromData_emptyInput_returnsEmptyMap() {
        val result = buildWeekdayTypicalsFromData(emptyList(), TimeZone.UTC)
        assertTrue(result.isEmpty(), "empty input should produce empty map, got keys=${result.keys}")
    }

    @Test
    fun buildWeekdayTypicalsFromData_singleMatchingDay_skipsWeekday() {
        // 2026-01-05 is a Monday. Provide rows for only that one Monday;
        // MIN_DAYS_FOR_TYPICAL = 2, so Monday must be absent from the result.
        val mondayStart = LocalDate(2026, 1, 5).atStartOfDayIn(TimeZone.UTC).epochSeconds
        val rows = (0..23).map { hour ->
            row(timestamp = mondayStart + hour * 3600L, steps = 100)
        }

        val result = buildWeekdayTypicalsFromData(rows, TimeZone.UTC)

        assertFalse(
            result.containsKey(DayOfWeek.MONDAY),
            "Monday should be absent with only 1 matching day, got keys=${result.keys}",
        )
    }

    @Test
    fun buildWeekdayTypicalsFromData_twoMondays_producesPayload() {
        // Two Mondays meet MIN_DAYS_FOR_TYPICAL=2. Each Monday has one row in slot 32
        // (08:00-08:15) with 200 steps. Expected: result has MONDAY, slot 32 = 200.
        val mon1 = LocalDate(2026, 1, 5).atStartOfDayIn(TimeZone.UTC).epochSeconds
        val mon2 = LocalDate(2026, 1, 12).atStartOfDayIn(TimeZone.UTC).epochSeconds
        val slot32Offset = 32 * 900L

        val rows = listOf(
            row(timestamp = mon1 + slot32Offset, steps = 200),
            row(timestamp = mon2 + slot32Offset, steps = 200),
        )

        val result = buildWeekdayTypicalsFromData(rows, TimeZone.UTC)

        val payload = result[DayOfWeek.MONDAY]
        assertNotNull(payload, "Monday payload missing; got keys=${result.keys}")
        assertEquals(192, payload.size, "payload should be 96 * 2 = 192 bytes")
        assertEquals(200, readUShortLE(payload, 32), "slot 32 should be 200")
    }

    @Test
    fun buildWeekdayTypicalsFromData_slotWithNoData_writesSentinel() {
        // Two Mondays each with a row in slot 32. Slot 0 has no data on either Monday.
        // Expected: slot 0 = 0xFFFF (sentinel).
        val mon1 = LocalDate(2026, 1, 5).atStartOfDayIn(TimeZone.UTC).epochSeconds
        val mon2 = LocalDate(2026, 1, 12).atStartOfDayIn(TimeZone.UTC).epochSeconds
        val slot32Offset = 32 * 900L

        val rows = listOf(
            row(timestamp = mon1 + slot32Offset, steps = 200),
            row(timestamp = mon2 + slot32Offset, steps = 200),
        )

        val payload = buildWeekdayTypicalsFromData(rows, TimeZone.UTC)[DayOfWeek.MONDAY]
        assertNotNull(payload)
        assertEquals(0xFFFF, readUShortLE(payload, 0), "slot 0 must be UNKNOWN sentinel")
        assertEquals(0xFFFF, readUShortLE(payload, 95), "slot 95 must be UNKNOWN sentinel")
    }

    @Test
    fun buildWeekdaySleepTypicalsFromData_emptyInput_returnsEmptyMap() {
        val result = buildWeekdaySleepTypicalsFromData(emptyMap(), TimeZone.UTC)
        assertTrue(result.isEmpty(), "empty input should produce empty map, got keys=${result.keys}")
    }

    @Test
    fun buildWeekdaySleepTypicalsFromData_singleMatchingDay_skipsWeekday() {
        val monday = LocalDate(2026, 1, 5)
        val mondayStart = monday.atStartOfDayIn(TimeZone.UTC).epochSeconds
        val sleep = nightSleep(
            startEpochSec = mondayStart - 3600,    // Sun 23:00
            endEpochSec = mondayStart + 7 * 3600,  // Mon 07:00
        )

        val result = buildWeekdaySleepTypicalsFromData(
            mapOf(monday to sleep),
            TimeZone.UTC,
        )

        assertFalse(
            result.containsKey(DayOfWeek.MONDAY),
            "Monday should be absent with only 1 matching day, got keys=${result.keys}",
        )
    }

    @Test
    fun buildWeekdaySleepTypicalsFromData_twoMondays_producesTypicals() {
        val tz = TimeZone.UTC
        val mon1 = LocalDate(2026, 1, 5)
        val mon2 = LocalDate(2026, 1, 12)
        val mon1Start = mon1.atStartOfDayIn(tz).epochSeconds
        val mon2Start = mon2.atStartOfDayIn(tz).epochSeconds

        // Both Mondays: bedtime 23:00 prev day, wake 07:00 Monday, 8h sleep
        val sleep1 = nightSleep(mon1Start - 3600, mon1Start + 7 * 3600)
        val sleep2 = nightSleep(mon2Start - 3600, mon2Start + 7 * 3600)

        val result = buildWeekdaySleepTypicalsFromData(
            mapOf(mon1 to sleep1, mon2 to sleep2),
            tz,
        )

        val monday = result[DayOfWeek.MONDAY]
        assertNotNull(monday, "Monday should be present; got keys=${result.keys}")
        assertEquals(8 * 3600, monday.sleepDurationSeconds, "8h = 28800s")
        assertEquals(0, monday.deepSleepDurationSeconds, "no deep sleep in fixture")
        assertEquals(23 * 3600, monday.fallAsleepSecondsOfDay, "bedtime 23:00 = 82800s")
        assertEquals(7 * 3600, monday.wakeupSecondsOfDay, "wake 07:00 = 25200s")
    }

    @Test
    fun buildWeekdaySleepTypicalsFromData_napOnlyDay_filteredOut() {
        val tz = TimeZone.UTC
        val mon1 = LocalDate(2026, 1, 5)
        val mon2 = LocalDate(2026, 1, 12)
        val mon1Start = mon1.atStartOfDayIn(tz).epochSeconds
        val mon2Start = mon2.atStartOfDayIn(tz).epochSeconds

        // mon1: 25-min nap (below threshold). mon2: full 8h night.
        val nap = nightSleep(
            startEpochSec = mon1Start + 14 * 3600,
            endEpochSec = mon1Start + 14 * 3600 + 1500,
            totalSec = 1500L,
        )
        val night = nightSleep(mon2Start - 3600, mon2Start + 7 * 3600)

        val result = buildWeekdaySleepTypicalsFromData(
            mapOf(mon1 to nap, mon2 to night),
            tz,
        )

        // mon1's only session is filtered out (<1800s), so only mon2 qualifies — that's 1 < threshold
        assertFalse(
            result.containsKey(DayOfWeek.MONDAY),
            "Monday should be absent; nap-only day filtered, only 1 qualifying day remains. Got keys=${result.keys}",
        )
    }

    @Test
    fun buildWeekdaySleepTypicalsFromData_circularMean_handlesMidnightWrap() {
        val tz = TimeZone.UTC
        val mon1 = LocalDate(2026, 1, 5)
        val mon2 = LocalDate(2026, 1, 12)
        val mon1Start = mon1.atStartOfDayIn(tz).epochSeconds
        val mon2Start = mon2.atStartOfDayIn(tz).epochSeconds

        // Bedtimes 23:00 (1h before Monday) and 01:00 (1h into Monday). Each session is 6h long.
        val sleep1 = nightSleep(mon1Start - 3600, mon1Start + 5 * 3600)        // 23:00 → 05:00
        val sleep2 = nightSleep(mon2Start + 3600, mon2Start + 7 * 3600)        // 01:00 → 07:00

        val result = buildWeekdaySleepTypicalsFromData(
            mapOf(mon1 to sleep1, mon2 to sleep2),
            tz,
        )

        val monday = result[DayOfWeek.MONDAY]
        assertNotNull(monday, "Monday should be present; got keys=${result.keys}")
        // Circular mean of 23:00 (82800s) and 01:00 (3600s) should be ~00:00 (0 or 86400-ε),
        // NOT the arithmetic mean (43200, noon). Allow ±60s tolerance for FP rounding.
        val fallAsleep = monday.fallAsleepSecondsOfDay
        val isNearMidnight = fallAsleep <= 60 || fallAsleep >= 86400 - 60
        assertTrue(
            isNearMidnight,
            "Expected fallAsleep near midnight (~0 or ~86400), got $fallAsleep — circular mean broken?",
        )
    }

    @Test
    fun buildWeekdaySleepTypicalsFromData_perWeekdayIndependence_onlyEligibleWeekdaysReturned() {
        val tz = TimeZone.UTC
        val mon1 = LocalDate(2026, 1, 5)
        val mon2 = LocalDate(2026, 1, 12)
        val tue = LocalDate(2026, 1, 6)
        val mon1Start = mon1.atStartOfDayIn(tz).epochSeconds
        val mon2Start = mon2.atStartOfDayIn(tz).epochSeconds
        val tueStart = tue.atStartOfDayIn(tz).epochSeconds

        val mondaySleep1 = nightSleep(mon1Start - 3600, mon1Start + 7 * 3600)
        val mondaySleep2 = nightSleep(mon2Start - 3600, mon2Start + 7 * 3600)
        val tuesdaySleep = nightSleep(tueStart - 3600, tueStart + 7 * 3600)

        val result = buildWeekdaySleepTypicalsFromData(
            mapOf(mon1 to mondaySleep1, mon2 to mondaySleep2, tue to tuesdaySleep),
            tz,
        )

        assertTrue(result.containsKey(DayOfWeek.MONDAY), "Monday should be present (2 qualifying days)")
        assertFalse(
            result.containsKey(DayOfWeek.TUESDAY),
            "Tuesday should be absent (only 1 qualifying day, below threshold)",
        )
    }

    @Test
    fun buildWeekdaySleepTypicalsFromData_splitSleep_usesLastSessionEnd() {
        val tz = TimeZone.UTC
        val mon1 = LocalDate(2026, 1, 5)
        val mon2 = LocalDate(2026, 1, 12)
        val mon1Start = mon1.atStartOfDayIn(tz).epochSeconds
        val mon2Start = mon2.atStartOfDayIn(tz).epochSeconds

        // Split-sleep night: first session 23:00 → 06:30 (7.5h). Second 06:45 → 07:30 (45min).
        // Both >30min; both qualify. last().end is 07:30 = 27000s.
        fun split(dayStart: Long) = nightSleepMulti(
            listOf(
                session(dayStart - 3600, dayStart + 6 * 3600 + 1800),              // 23:00 → 06:30
                session(dayStart + 6 * 3600 + 2700, dayStart + 7 * 3600 + 1800),    // 06:45 → 07:30
            )
        )
        val mon1Sleep = split(mon1Start)
        val mon2Sleep = split(mon2Start)

        val result = buildWeekdaySleepTypicalsFromData(
            mapOf(mon1 to mon1Sleep, mon2 to mon2Sleep),
            tz,
        )

        val monday = result[DayOfWeek.MONDAY]
        assertNotNull(monday)
        assertEquals(
            7 * 3600 + 1800, monday.wakeupSecondsOfDay,
            "Wake derives from last qualifying session's end (07:30 = 27000s), not first's",
        )
    }

    @Test
    fun buildWeekdaySleepTypicalsFromData_mixedValidity_filtersShortSession() {
        val tz = TimeZone.UTC
        val mon1 = LocalDate(2026, 1, 5)
        val mon2 = LocalDate(2026, 1, 12)
        val mon1Start = mon1.atStartOfDayIn(tz).epochSeconds
        val mon2Start = mon2.atStartOfDayIn(tz).epochSeconds

        // Each Monday has a 6h night PLUS a 25-min nap. Nap is filtered; bedtime/wake derive
        // from the 6h night session only.
        // Night: 22:00 → 04:00 (6h, totalSec=21600). Nap: 13:00 → 13:25 (25min, totalSec=1500).
        // Sessions are chronologically ordered (night starts at dayStart - 2*3600, nap at dayStart + 13*3600).
        fun mixed(dayStart: Long) = nightSleepMulti(
            listOf(
                session(dayStart - 2 * 3600, dayStart + 4 * 3600, totalSec = 6L * 3600),  // 22:00 prev → 04:00 (6h)
                session(
                    dayStart + 13 * 3600, dayStart + 13 * 3600 + 1500,
                    totalSec = 1500L,
                ),                                                                          // 13:00 → 13:25 nap
            )
        )
        val mon1Sleep = mixed(mon1Start)
        val mon2Sleep = mixed(mon2Start)

        val result = buildWeekdaySleepTypicalsFromData(
            mapOf(mon1 to mon1Sleep, mon2 to mon2Sleep),
            tz,
        )

        val monday = result[DayOfWeek.MONDAY]
        assertNotNull(monday)
        assertEquals(6 * 3600, monday.sleepDurationSeconds, "Only the 6h session contributes (nap filtered)")
        assertEquals(22 * 3600, monday.fallAsleepSecondsOfDay, "Bedtime 22:00 = 79200s, from 6h session's start")
        assertEquals(4 * 3600, monday.wakeupSecondsOfDay, "Wake 04:00 = 14400s, from 6h session's end")
    }

    @Test
    fun buildWeekdayTypicalsFromData_partialSlotCoverage_avgIsPerSlotNotPerDay() {
        // Five Mondays: each contributes 100 steps in slot 60. Two more Mondays exist
        // (with rows in OTHER slots) so they count toward matchingDays but NOT toward
        // slot 60's per-slot day count. Expected slot 60 = 100 (sum=500, slot-day-count=5),
        // NOT 71 (sum=500, total-matching-days=7).
        val mondays = (0..6).map { week ->
            LocalDate(2026, 1, 5).plus(DatePeriod(days = 7 * week)).atStartOfDayIn(TimeZone.UTC).epochSeconds
        }
        val slot60Offset = 60 * 900L
        val slot10Offset = 10 * 900L

        val rows = buildList {
            // First 5 Mondays: rows in slot 60 with 100 steps
            for (i in 0..4) add(row(timestamp = mondays[i] + slot60Offset, steps = 100))
            // Last 2 Mondays: rows in slot 10 only (count toward matchingDays, not slot-60-day-count)
            for (i in 5..6) add(row(timestamp = mondays[i] + slot10Offset, steps = 50))
        }

        val payload = buildWeekdayTypicalsFromData(rows, TimeZone.UTC)[DayOfWeek.MONDAY]
        assertNotNull(payload)
        assertEquals(100, readUShortLE(payload, 60), "slot 60 must average over per-slot days (5), not total matching days (7)")
    }

    /** Reads a single little-endian UShort at byte offset slotIndex * 2 from payload. Returns Int 0..65535. */
    private fun readUShortLE(payload: ByteArray, slotIndex: Int): Int {
        val byteOffset = slotIndex * 2
        val lo = payload[byteOffset].toInt() and 0xFF
        val hi = payload[byteOffset + 1].toInt() and 0xFF
        return (hi shl 8) or lo
    }
}

private fun row(timestamp: Long, steps: Int) = HealthDataEntity(
    timestamp = timestamp,
    steps = steps,
    orientation = 0,
    intensity = 0,
    lightIntensity = 0,
    activeMinutes = 0,
    restingGramCalories = 0,
    activeGramCalories = 0,
    distanceCm = 0,
)

private fun session(
    startEpochSec: Long,
    endEpochSec: Long,
    totalSec: Long = endEpochSec - startEpochSec,
    deepSec: Long = 0L,
): SleepSession = SleepSession(
    start = startEpochSec,
    end = endEpochSec,
    totalSleep = totalSec,
    deepSleep = deepSec,
    intervals = mutableListOf(),
)

private fun nightSleepMulti(sessions: List<SleepSession>): DailySleep =
    DailySleep(
        sessions = sessions,
        totalSleep = sessions.sumOf { it.totalSleep },
        deepSleep = sessions.sumOf { it.deepSleep },
    )

private fun nightSleep(
    startEpochSec: Long,
    endEpochSec: Long,
    totalSec: Long = endEpochSec - startEpochSec,
    deepSec: Long = 0L,
): DailySleep = nightSleepMulti(listOf(session(startEpochSec, endEpochSec, totalSec, deepSec)))
