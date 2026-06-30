@file:OptIn(ExperimentalTime::class)

package coredevices.ring.agent.builtin_servlets.calendar

import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class CreateCalendarEventToolTest {

    private val tz = TimeZone.UTC
    // Fixed reference "now" so relative phrases resolve deterministically.
    private val now = Instant.parse("2026-06-17T12:00:00Z")

    @Test
    fun defaultsEndToOneHourAfterStartWhenEndOmitted() {
        val (start, end) = CreateCalendarEventTool.resolveEventTimes("in 2 hours", null, null, tz, now)!!
        assertEquals(now + 2.hours, start)
        assertEquals(start + CreateCalendarEventTool.DEFAULT_DURATION, end)
    }

    @Test
    fun durationHumanResolvesRelativeToStart() {
        val (start, end) = CreateCalendarEventTool.resolveEventTimes("in 2 hours", null, "in 30 minutes", tz, now)!!
        assertEquals(now + 2.hours, start)
        assertEquals(start + 30.minutes, end)
    }

    @Test
    fun explicitEndTimeResolvesRelativeToStartDay() {
        // Start 2pm UTC today; end "at 5pm" must land on the start's day (relative to start, not now).
        val (start, end) = CreateCalendarEventTool.resolveEventTimes("at 2pm", "at 5pm", null, tz, now)!!
        assertEquals(Instant.parse("2026-06-17T14:00:00Z"), start)
        assertEquals(Instant.parse("2026-06-17T17:00:00Z"), end)
    }

    @Test
    fun durationTakesPrecedenceOverEndTime() {
        val (start, end) = CreateCalendarEventTool.resolveEventTimes("at 2pm", "at 5pm", "in 15 minutes", tz, now)!!
        assertEquals(start + 15.minutes, end)
    }

    @Test
    fun clampsEndNotAfterStartToDefaultDuration() {
        // end "at 2pm" relative to a 2pm start lands exactly on start -> clamp to default duration.
        val (start, end) = CreateCalendarEventTool.resolveEventTimes("at 2pm", "at 2pm", null, tz, now)!!
        assertTrue(end > start)
        assertEquals(start + CreateCalendarEventTool.DEFAULT_DURATION, end)
    }

    @Test
    fun returnsNullWhenStartUnparseable() {
        assertNull(CreateCalendarEventTool.resolveEventTimes("asdfqwer zzz", null, null, tz, now))
    }

    @Test
    fun dateOnlyStartsAreFlaggedAmbiguousButTimedOnesAreNot() {
        // Date with no time of day -> ambiguous (agent should make a note instead).
        assertTrue(CreateCalendarEventTool.isDateOnly("tomorrow", tz))
        assertTrue(CreateCalendarEventTool.isDateOnly("next Friday", tz))
        // A specific time of day -> not ambiguous.
        assertFalse(CreateCalendarEventTool.isDateOnly("tomorrow at 3pm", tz))
        assertFalse(CreateCalendarEventTool.isDateOnly("at 3pm", tz))
        assertFalse(CreateCalendarEventTool.isDateOnly("in 2 hours", tz))
    }

}
