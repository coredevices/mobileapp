package io.rebble.libpebblecommon.calendar

import io.rebble.libpebblecommon.database.entity.CalendarEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun calendar(
    platformId: String,
    name: String = "Work",
    ownerId: String = "source-1",
    enabled: Boolean = true,
) = CalendarEntity(
    platformId = platformId,
    name = name,
    ownerName = "Owner",
    ownerId = ownerId,
    color = 0,
    enabled = enabled,
)

class CalendarEnabledCarryOverTest {
    @Test
    fun keepsDisabledWhenPlatformIdChanges() {
        val removed = listOf(calendar(platformId = "old", enabled = false))
        val result = calendar(platformId = "new").inheritEnabledFrom(removed)
        assertEquals(false, result.enabled)
        assertEquals("new", result.platformId)
    }

    @Test
    fun genuinelyNewCalendarStaysEnabled() {
        val removed = listOf(calendar(platformId = "old", name = "Home", enabled = false))
        assertTrue(calendar(platformId = "new", name = "Work").inheritEnabledFrom(removed).enabled)
    }

    @Test
    fun sameNameInAnotherAccountIsNotMatched() {
        val removed = listOf(calendar(platformId = "old", ownerId = "source-1", enabled = false))
        val result = calendar(platformId = "new", ownerId = "source-2").inheritEnabledFrom(removed)
        assertTrue(result.enabled)
    }

    @Test
    fun noRemovedCalendarsIsANoOp() {
        val new = calendar(platformId = "new")
        assertEquals(new, new.inheritEnabledFrom(emptyList()))
    }
}
