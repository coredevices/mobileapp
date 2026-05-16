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
