package io.rebble.libpebblecommon.calendar

import io.rebble.libpebblecommon.database.entity.BaseAttribute
import io.rebble.libpebblecommon.database.entity.CalendarEntity
import io.rebble.libpebblecommon.packets.blobdb.TimelineAttribute
import io.rebble.libpebblecommon.packets.blobdb.TimelineIcon
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

private val event = CalendarEvent(
    id = "1",
    calendarId = "cal",
    title = "Meeting",
    description = "",
    location = null,
    startTime = Instant.fromEpochSeconds(1_700_000_000),
    endTime = Instant.fromEpochSeconds(1_700_003_600),
    allDay = false,
    attendees = emptyList(),
    recurs = false,
    reminders = emptyList(),
    availability = CalendarEvent.Availability.Busy,
    status = CalendarEvent.Status.Confirmed,
    baseEventId = "1",
)

private fun calendar(iconCode: String?) = CalendarEntity(
    platformId = "cal",
    name = "Work",
    ownerName = "Owner",
    ownerId = "source-1",
    color = 0,
    enabled = true,
    iconCode = iconCode,
)

private fun pinIcon(iconCode: String?): TimelineIcon? =
    event.toTimelinePin(calendar(iconCode), supportsRsvpActions = false)
        .content.attributes
        .filterIsInstance<BaseAttribute.IconAttribute>()
        .find { it.attribute == TimelineAttribute.TinyIcon }?.icon

class CalendarPinIconTest {
    @Test
    fun usesCalendarIcon() {
        assertEquals(TimelineIcon.TimelineSun, pinIcon(TimelineIcon.TimelineSun.code))
    }

    @Test
    fun defaultsToCalendarIconWhenUnset() {
        assertEquals(TimelineIcon.TimelineCalendar, pinIcon(null))
    }

    @Test
    fun defaultsToCalendarIconWhenUnknown() {
        assertEquals(TimelineIcon.TimelineCalendar, pinIcon("system://images/NOT_A_REAL_ICON"))
    }
}
