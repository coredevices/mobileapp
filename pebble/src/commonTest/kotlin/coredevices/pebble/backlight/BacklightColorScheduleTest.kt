package coredevices.pebble.backlight

import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class BacklightColorScheduleTest {
    @Test
    fun minuteOfDayUsesProvidedTimeZone() {
        val instant = Instant.parse("2025-12-02T00:50:45Z")

        assertEquals(
            16 * 60 + 50,
            backlightScheduleMinuteOfDay(instant, TimeZone.of("America/Los_Angeles")),
        )
    }

    @Test
    fun formatMinuteOfDayPadsHourAndMinute() {
        assertEquals("06:05", formatBacklightScheduleMinute(6L * 60 + 5))
    }
}
