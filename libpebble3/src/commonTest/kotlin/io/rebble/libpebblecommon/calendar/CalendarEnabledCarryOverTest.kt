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
    iconCode: String? = null,
) = CalendarEntity(
    platformId = platformId,
    name = name,
    ownerName = "Owner",
    ownerId = ownerId,
    color = 0,
    enabled = enabled,
    iconCode = iconCode,
)

class CalendarEnabledCarryOverTest {
    @Test
    fun keepsDisabledWhenPlatformIdChanges() {
        val removed = listOf(calendar(platformId = "old", enabled = false))
        val result = calendar(platformId = "new").inheritUserSettingsFrom(removed)
        assertEquals(false, result.enabled)
        assertEquals("new", result.platformId)
    }

    @Test
    fun keepsIconWhenPlatformIdChanges() {
        val removed = listOf(calendar(platformId = "old", iconCode = "system://images/TIMELINE_SUN"))
        val result = calendar(platformId = "new").inheritUserSettingsFrom(removed)
        assertEquals("system://images/TIMELINE_SUN", result.iconCode)
    }

    @Test
    fun genuinelyNewCalendarStaysEnabled() {
        val removed = listOf(calendar(platformId = "old", name = "Home", enabled = false))
        assertTrue(calendar(platformId = "new", name = "Work").inheritUserSettingsFrom(removed).enabled)
    }

    @Test
    fun sameNameInAnotherAccountIsNotMatched() {
        val removed = listOf(calendar(platformId = "old", ownerId = "source-1", enabled = false))
        val result = calendar(platformId = "new", ownerId = "source-2").inheritUserSettingsFrom(removed)
        assertTrue(result.enabled)
    }

    @Test
    fun noRemovedCalendarsIsANoOp() {
        val new = calendar(platformId = "new")
        assertEquals(new, new.inheritUserSettingsFrom(emptyList()))
    }
}
