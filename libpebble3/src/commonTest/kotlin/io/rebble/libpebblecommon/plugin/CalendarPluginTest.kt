package io.rebble.libpebblecommon.plugin

import io.rebble.libpebblecommon.calendar.CalendarEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

private val NOW = Instant.fromEpochSeconds(1_700_000_000)

private fun entry(
    title: String,
    startsInHours: Long,
    durationHours: Long = 1,
    calendarName: String = "Work",
) = EventInCalendar(
    event = CalendarEvent(
        id = title,
        calendarId = "cal",
        title = title,
        description = "",
        location = null,
        startTime = NOW + startsInHours.hours,
        endTime = NOW + (startsInHours + durationHours).hours,
        allDay = false,
        attendees = emptyList(),
        recurs = false,
        reminders = emptyList(),
        availability = CalendarEvent.Availability.Busy,
        status = CalendarEvent.Status.Confirmed,
        baseEventId = title,
    ),
    calendarName = calendarName,
)

class CalendarPluginTest {

    @Test
    fun eventsAreOrderedByStartTimeAcrossCalendars() {
        val ordered = listOf(
            entry("Standup", startsInHours = 3, calendarName = "Work"),
            entry("Dentist", startsInHours = 1, calendarName = "Personal"),
        ).upcoming(NOW, limit = 10)

        assertEquals(listOf("Dentist", "Standup"), ordered.map { it.event.title })
    }

    @Test
    fun anEventInProgressIsStillUpcoming() {
        val ordered = listOf(
            entry("Later", startsInHours = 2),
            entry("In progress", startsInHours = -1, durationHours = 2),
        ).upcoming(NOW, limit = 10)

        assertEquals(listOf("In progress", "Later"), ordered.map { it.event.title })
    }

    @Test
    fun finishedEventsAreDropped() {
        val ordered = listOf(
            entry("Finished", startsInHours = -5, durationHours = 1),
            entry("Later", startsInHours = 2),
        ).upcoming(NOW, limit = 10)

        assertEquals(listOf("Later"), ordered.map { it.event.title })
    }

    @Test
    fun theLimitKeepsTheSoonestEvents() {
        val ordered = listOf(
            entry("Third", startsInHours = 5),
            entry("First", startsInHours = 1),
            entry("Second", startsInHours = 3),
        ).upcoming(NOW, limit = 2)

        assertEquals(listOf("First", "Second"), ordered.map { it.event.title })
    }
}
