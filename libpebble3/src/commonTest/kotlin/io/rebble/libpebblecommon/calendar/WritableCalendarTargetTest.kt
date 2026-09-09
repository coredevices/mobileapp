package io.rebble.libpebblecommon.calendar

import io.rebble.libpebblecommon.database.entity.CalendarEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private fun calendar(platformId: String) = CalendarEntity(
    platformId = platformId,
    name = "Work",
    ownerName = "Owner",
    ownerId = "owner",
    color = 0,
    enabled = true,
)

class WritableCalendarTargetTest {
    private val calendars = listOf(calendar("1"), calendar("2"), calendar("3"))

    @Test
    fun usesTheRequestedCalendar() {
        assertEquals("2", calendars.resolveWritableTarget("2")?.platformId)
    }

    @Test
    fun noChoiceUsesTheDefault() {
        assertEquals("1", calendars.resolveWritableTarget(null)?.platformId)
    }

    @Test
    fun goneCalendarFallsBackToTheDefault() {
        assertEquals("1", calendars.resolveWritableTarget("99")?.platformId)
    }

    @Test
    fun noWritableCalendars() {
        assertNull(emptyList<CalendarEntity>().resolveWritableTarget("1"))
    }
}
