package io.rebble.libpebblecommon.calendar

import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

private val PARIS = TimeZone.of("Europe/Paris")
private val NEW_YORK = TimeZone.of("America/New_York")

/** What PhoneCalendarSyncer writes as the reminder's timestamp. */
private fun reminderTime(startTime: Instant, reminder: EventReminder) =
    startTime - reminder.minutesBefore.minutes

class CalendarReminderTimingTest {
    @Test
    fun alarmBeforeStartFiresBeforeStart() {
        val start = Instant.parse("2026-08-13T14:00:00Z")
        val reminder = EventReminder.fromStartOffset(-900.0)
        assertEquals(15, reminder.minutesBefore)
        assertEquals(Instant.parse("2026-08-13T13:45:00Z"), reminderTime(start, reminder))
    }

    @Test
    fun alarmAfterStartFiresAfterStart() {
        val start = Instant.parse("2026-08-13T00:00:00Z")
        val reminder = EventReminder.fromStartOffset(32400.0)
        assertEquals(-540, reminder.minutesBefore)
        assertEquals(Instant.parse("2026-08-13T09:00:00Z"), reminderTime(start, reminder))
    }

    @Test
    fun birthdayAlertLandsOnTheDayItself() {
        // EventKit reports the all-day event at local midnight, with a +9h "9am on the day" alarm.
        val start = Instant.parse("2026-08-12T22:00:00Z").anchorAllDayToUtc(PARIS)
        val reminderAt = reminderTime(start, EventReminder.fromStartOffset(32400.0))
        // Wall-clock 9am on the birthday; the watch applies the +2h offset itself.
        assertEquals(Instant.parse("2026-08-13T09:00:00Z"), reminderAt)
    }

    @Test
    fun allDayAnchoringUsesLocalDateAheadOfUtc() {
        assertEquals(
            Instant.parse("2026-08-13T00:00:00Z"),
            Instant.parse("2026-08-12T22:00:00Z").anchorAllDayToUtc(PARIS),
        )
    }

    @Test
    fun allDayAnchoringUsesLocalDateBehindUtc() {
        assertEquals(
            Instant.parse("2026-08-13T00:00:00Z"),
            Instant.parse("2026-08-13T04:00:00Z").anchorAllDayToUtc(NEW_YORK),
        )
    }

    @Test
    fun anchoringIsANoOpInUtc() {
        val midnight = Instant.parse("2026-08-13T00:00:00Z")
        assertEquals(midnight, midnight.anchorAllDayToUtc(TimeZone.UTC))
    }
}
