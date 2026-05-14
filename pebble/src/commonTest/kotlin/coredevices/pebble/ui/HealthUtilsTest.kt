package coredevices.pebble.ui

import io.rebble.libpebblecommon.services.DailySleep
import io.rebble.libpebblecommon.services.SleepInterval
import io.rebble.libpebblecommon.services.SleepSession
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun light(start: Long, end: Long) = SleepInterval(start, end, isDeep = false)
private fun deep(start: Long, end: Long) = SleepInterval(start, end, isDeep = true)

private fun session(vararg intervals: SleepInterval): SleepSession {
    val light = intervals.filter { !it.isDeep }
    val deep = intervals.filter { it.isDeep }
    return SleepSession(
        start = intervals.minOf { it.start },
        end = intervals.maxOf { it.end },
        totalSleep = light.sumOf { it.end - it.start },
        deepSleep = deep.sumOf { it.end - it.start },
        intervals = intervals.toMutableList(),
    )
}

private fun dailySleep(vararg sessions: SleepSession) = DailySleep(
    sessions = sessions.toList(),
    totalSleep = sessions.sumOf { it.totalSleep },
    deepSleep = sessions.sumOf { it.deepSleep },
)

class HealthUtilsTest {
    @Test
    fun ordinalSuffix_1st() = assertEquals("st", ordinalSuffix(1))

    @Test
    fun ordinalSuffix_2nd() = assertEquals("nd", ordinalSuffix(2))

    @Test
    fun ordinalSuffix_3rd() = assertEquals("rd", ordinalSuffix(3))

    @Test
    fun ordinalSuffix_4th() = assertEquals("th", ordinalSuffix(4))

    @Test
    fun ordinalSuffix_11th() = assertEquals("th", ordinalSuffix(11))

    @Test
    fun ordinalSuffix_12th() = assertEquals("th", ordinalSuffix(12))

    @Test
    fun ordinalSuffix_13th() = assertEquals("th", ordinalSuffix(13))

    @Test
    fun ordinalSuffix_21st() = assertEquals("st", ordinalSuffix(21))

    @Test
    fun ordinalSuffix_22nd() = assertEquals("nd", ordinalSuffix(22))

    @Test
    fun ordinalSuffix_23rd() = assertEquals("rd", ordinalSuffix(23))

    @Test
    fun ordinalSuffix_31st() = assertEquals("st", ordinalSuffix(31))

    @Test
    fun formatDayLabel_today() {
        val today = LocalDate(2026, 4, 21)
        assertEquals("Today", formatDayLabel(today, today))
    }

    @Test
    fun formatDayLabel_yesterday() {
        val today = LocalDate(2026, 4, 21)
        val yesterday = LocalDate(2026, 4, 20)
        assertEquals("Yesterday", formatDayLabel(yesterday, today))
    }

    @Test
    fun formatDayLabel_sameMonth() {
        val today = LocalDate(2026, 4, 21)
        val target = LocalDate(2026, 4, 18)
        assertEquals("Sat 18th", formatDayLabel(target, today))
    }

    @Test
    fun formatDayLabel_differentMonth() {
        val today = LocalDate(2026, 4, 2)
        val target = LocalDate(2026, 3, 30)
        assertEquals("Mon 30th Mar", formatDayLabel(target, today))
    }

    @Test
    fun formatDayLabel_1st() {
        val today = LocalDate(2026, 4, 5)
        val target = LocalDate(2026, 4, 1)
        assertEquals("Wed 1st", formatDayLabel(target, today))
    }

    @Test
    fun formatDayLabel_2nd() {
        val today = LocalDate(2026, 4, 5)
        val target = LocalDate(2026, 4, 2)
        assertEquals("Thu 2nd", formatDayLabel(target, today))
    }

    @Test
    fun formatDayLabel_3rd() {
        val today = LocalDate(2026, 4, 5)
        val target = LocalDate(2026, 4, 3)
        assertEquals("Fri 3rd", formatDayLabel(target, today))
    }

    @Test
    fun averageTimeOfDay_sameTime() {
        val tenPM = 22 * 3600L
        assertEquals(tenPM, averageTimeOfDay(listOf(tenPM, tenPM, tenPM)))
    }

    @Test
    fun averageTimeOfDay_afternoon() {
        val noon = 12 * 3600L
        val twoPM = 14 * 3600L
        assertEquals(13 * 3600L, averageTimeOfDay(listOf(noon, twoPM)))
    }

    @Test
    fun averageTimeOfDay_crossesMidnight() {
        val elevenPM = 23 * 3600L
        val oneAM = 1 * 3600L
        val avg = averageTimeOfDay(listOf(elevenPM, oneAM))
        assertEquals(0L, avg)
    }

    @Test
    fun averageTimeOfDay_allLateBedtimes() {
        val times = listOf(22 * 3600L, 23 * 3600L, 23 * 3600L + 1800, 0L)
        val avg = averageTimeOfDay(times)
        // Should be around 22:52 (close to 23:00, not 11:00 AM)
        assertTrue(avg > 20 * 3600L || avg < 2 * 3600L, "Expected late night, got ${avg / 3600}h")
    }

    @Test
    fun averageTimeOfDay_singleValue() {
        assertEquals(8 * 3600L, averageTimeOfDay(listOf(8 * 3600L)))
    }

    @Test
    fun averageTimeOfDay_emptyList() {
        assertEquals(0L, averageTimeOfDay(emptyList()))
    }

    @Test
    fun buildDailySleepSegments_nullSession() {
        val result = buildDailySleepSegments(dayStart = 0L, dailySleep = null)
        assertTrue(result.isEmpty())
    }

    @Test
    fun buildDailySleepSegments_fractionsInRange() {
        val dayStart = 86400L // second day
        val sleep = dailySleep(session(
            light(dayStart - 4 * 3600L, dayStart + 4 * 3600L), // 8 PM → 4 AM
            deep(dayStart + 1 * 3600L, dayStart + 3 * 3600L),  // 1 AM → 3 AM (within light)
        ))
        val segments = buildDailySleepSegments(dayStart, sleep)

        assertTrue(segments.isNotEmpty())
        for (seg in segments) {
            assertTrue(seg.startFraction in 0f..1f, "startFraction ${seg.startFraction} out of range")
            assertTrue(seg.widthFraction > 0f, "widthFraction should be positive")
            assertTrue(seg.startFraction + seg.widthFraction <= 1.001f, "segment extends beyond 1.0")
        }
    }

    @Test
    fun buildDailySleepSegments_lightBeforeDeep() {
        // Deep is drawn on top of light at the same timestamps, so the segment list must
        // contain all light segments before any deep segment.
        val dayStart = 86400L
        val sleep = dailySleep(session(
            light(dayStart - 4 * 3600L, dayStart + 2 * 3600L),
            deep(dayStart, dayStart + 1 * 3600L),
        ))
        val segments = buildDailySleepSegments(dayStart, sleep)

        assertEquals(2, segments.size)
        assertEquals(false, segments[0].isDeep)
        assertEquals(true, segments[1].isDeep)
    }

    @Test
    fun buildDailySleepSegments_noDeepSleep() {
        val dayStart = 86400L
        val sleep = dailySleep(session(
            light(dayStart - 4 * 3600L, dayStart + 4 * 3600L),
        ))
        val segments = buildDailySleepSegments(dayStart, sleep)

        assertEquals(1, segments.size)
        assertEquals(false, segments[0].isDeep)
    }

    @Test
    fun buildDailySleepSegments_deepRendersAtRealTimestamp() {
        // MOB-6591 root cause: deep sleep used to always be drawn at the end of the bar.
        // Now it must render at its actual timestamp inside the light container.
        val dayStart = 86400L
        val lightStart = dayStart - 4 * 3600L
        val lightEnd = dayStart + 4 * 3600L
        val deepStart = dayStart - 2 * 3600L  // 1/4 of the way through the light interval
        val deepEnd = dayStart - 1 * 3600L
        val sleep = dailySleep(session(
            light(lightStart, lightEnd),
            deep(deepStart, deepEnd),
        ))
        val segments = buildDailySleepSegments(dayStart, sleep)

        assertEquals(2, segments.size)
        val lightSeg = segments[0]
        val deepSeg = segments[1]
        // Deep should sit inside the light region, not at its end.
        assertTrue(deepSeg.startFraction > lightSeg.startFraction)
        assertTrue(deepSeg.startFraction + deepSeg.widthFraction < lightSeg.startFraction + lightSeg.widthFraction)
    }

    @Test
    fun buildDailySleepSegments_splitSleepRendersBothSessions() {
        // MOB-6592: split sleep with a gap large enough to produce two separate sessions.
        val dayStart = 86400L
        val sleep = dailySleep(
            session(
                light(dayStart - 6 * 3600L, dayStart - 2 * 3600L),  // 6 PM → 10 PM
                deep(dayStart - 5 * 3600L, dayStart - 4 * 3600L),
            ),
            session(
                light(dayStart + 1 * 3600L, dayStart + 5 * 3600L),  // 1 AM → 5 AM
                deep(dayStart + 2 * 3600L, dayStart + 4 * 3600L),
            ),
        )
        val segments = buildDailySleepSegments(dayStart, sleep)

        // 2 light + 2 deep
        assertEquals(4, segments.size)
        val lightSegs = segments.filter { !it.isDeep }
        // The two light intervals must not be contiguous (visible awake gap).
        val firstLightEnd = lightSegs[0].startFraction + lightSegs[0].widthFraction
        assertTrue(lightSegs[1].startFraction > firstLightEnd,
            "Expected gap between sessions; got light1 end=$firstLightEnd, light2 start=${lightSegs[1].startFraction}")
    }

    @Test
    fun buildDailySleepSegments_awakeGapWithinSession() {
        // MOB-6591: single session containing two Sleep containers separated by a 14-min
        // awake period (well under SLEEP_SESSION_GAP_HOURS = 1h), matching the reporter's
        // actual data. The renderer must still leave the gap visible.
        val dayStart = 86400L
        val firstEnd = dayStart - 1 * 3600L
        val gapSec = 14 * 60L
        val sleep = dailySleep(session(
            light(dayStart - 4 * 3600L, firstEnd),                  // 8 PM → 11 PM
            deep(dayStart - 3 * 3600L, dayStart - 2 * 3600L),
            light(firstEnd + gapSec, firstEnd + gapSec + 3 * 3600L), // 14 min later, +3h
            deep(firstEnd + gapSec + 3600L, firstEnd + gapSec + 2 * 3600L),
        ))
        val segments = buildDailySleepSegments(dayStart, sleep)

        val lightSegs = segments.filter { !it.isDeep }
        assertEquals(2, lightSegs.size, "Expected two light segments, one per Sleep container")
        val firstLightEnd = lightSegs[0].startFraction + lightSegs[0].widthFraction
        assertTrue(lightSegs[1].startFraction > firstLightEnd,
            "Awake gap inside session should be visible; got first=$firstLightEnd, second=${lightSegs[1].startFraction}")
    }

    @Test
    fun buildDailySleepSegments_realisticMob6591Shape() {
        // Mirrors MOB-6591's actual reported data: one session, two Sleep containers with
        // a short awake gap, multiple DeepSleep entries interspersed in each container.
        // Verifies all three failure modes simultaneously: deep at real timestamps (not
        // stacked at end), awake gap visible, every deep entry rendered.
        val dayStart = 86400L
        val s1Start = dayStart - 4 * 3600L  // 8 PM (origin of chart)
        val sleep = dailySleep(session(
            // First Sleep container: 5.4h with 4 deep sub-intervals
            light(s1Start, s1Start + (5.4 * 3600).toLong()),
            deep(s1Start + (1.8 * 3600).toLong(), s1Start + (2.5 * 3600).toLong()),
            deep(s1Start + (3.2 * 3600).toLong(), s1Start + (3.6 * 3600).toLong()),
            deep(s1Start + (3.9 * 3600).toLong(), s1Start + (4.3 * 3600).toLong()),
            deep(s1Start + (4.3 * 3600).toLong(), s1Start + (4.7 * 3600).toLong()),
            // 14-min awake gap, then second Sleep container: 4.6h with 3 deep sub-intervals
            light(s1Start + (5.4 * 3600 + 14 * 60).toLong(), s1Start + (10.0 * 3600 + 14 * 60).toLong()),
            deep(s1Start + (6.7 * 3600 + 14 * 60).toLong(), s1Start + (7.5 * 3600 + 14 * 60).toLong()),
            deep(s1Start + (7.8 * 3600 + 14 * 60).toLong(), s1Start + (8.2 * 3600 + 14 * 60).toLong()),
            deep(s1Start + (8.9 * 3600 + 14 * 60).toLong(), s1Start + (9.4 * 3600 + 14 * 60).toLong()),
        ))
        val segments = buildDailySleepSegments(dayStart, sleep)

        val lightSegs = segments.filter { !it.isDeep }
        val deepSegs = segments.filter { it.isDeep }
        assertEquals(2, lightSegs.size)
        assertEquals(7, deepSegs.size)
        // Awake gap is visible.
        val firstLightEnd = lightSegs[0].startFraction + lightSegs[0].widthFraction
        assertTrue(lightSegs[1].startFraction > firstLightEnd)
        // Every deep segment sits inside one of the two light containers (i.e. at its real
        // time, not stacked at the end of any single bar).
        for (d in deepSegs) {
            val dEnd = d.startFraction + d.widthFraction
            val insideALightContainer = lightSegs.any { l ->
                d.startFraction >= l.startFraction - 1e-4f &&
                    dEnd <= l.startFraction + l.widthFraction + 1e-4f
            }
            assertTrue(insideALightContainer,
                "Deep segment [${d.startFraction}, $dEnd] must overlap a light container")
        }
    }

    @Test
    fun formatHours_wholeNumber() = assertEquals("7h 0m", formatHours(7.0f))

    @Test
    fun formatHours_withMinutes() = assertEquals("7h 30m", formatHours(7.5f))

    @Test
    fun formatHours_zero() = assertEquals("0h 0m", formatHours(0f))
}
