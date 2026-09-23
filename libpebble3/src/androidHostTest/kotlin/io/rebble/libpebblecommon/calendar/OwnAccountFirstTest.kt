package io.rebble.libpebblecommon.calendar

import io.rebble.libpebblecommon.database.entity.CalendarEntity
import kotlin.test.Test
import kotlin.test.assertEquals

private fun calendar(
    platformId: String,
    accountName: String = "me@example.com",
    ownerAccount: String = "shared@example.com",
) = CalendarEntity(
    platformId = platformId,
    name = "Calendar $platformId",
    ownerName = accountName,
    ownerId = ownerAccount,
    color = 0,
    enabled = true,
)

class OwnAccountFirstTest {
    @Test
    fun ownCalendarMovesToTheFront() {
        val calendars = listOf(
            calendar("1"),
            calendar("2", ownerAccount = "me@example.com"),
            calendar("3"),
        )
        assertEquals(
            listOf("2", "1", "3"),
            calendars.ownAccountFirst().map { it.platformId },
        )
    }

    @Test
    fun withoutOwnCalendarOrderIsUnchanged() {
        val calendars = listOf(calendar("1"), calendar("2"))
        assertEquals(calendars, calendars.ownAccountFirst())
    }

    @Test
    fun calendarWithNoAccountIsNotTreatedAsOwn() {
        val calendars = listOf(
            calendar("1", accountName = "unknown", ownerAccount = "unknown"),
            calendar("2"),
        )
        assertEquals(calendars, calendars.ownAccountFirst())
    }

    @Test
    fun emptyList() {
        assertEquals(emptyList<CalendarEntity>(), emptyList<CalendarEntity>().ownAccountFirst())
    }
}
