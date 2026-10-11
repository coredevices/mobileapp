package coredevices.ring.agent.builtin_servlets.calendar

import PlatformUiContext
import com.russhwolf.settings.MapSettings
import coredevices.ring.database.PreferencesImpl
import coredevices.util.AppResumed
import coredevices.util.Permission
import coredevices.util.PermissionRequester
import coredevices.util.PermissionResult
import coredevices.util.RequiredPermissions
import io.rebble.libpebblecommon.calendar.NewCalendarEvent
import io.rebble.libpebblecommon.connection.Calendar
import io.rebble.libpebblecommon.database.entity.CalendarEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TargetCalendarSeederTest {
    private fun calendar(id: Int, name: String, writable: Boolean = true) = CalendarEntity(
        id = id,
        platformId = id.toString(),
        name = name,
        ownerName = "owner",
        ownerId = "owner",
        color = 0,
        enabled = true,
        writable = writable,
    )

    private class FakeCalendar(
        private val calendars: List<CalendarEntity>,
        private val default: CalendarEntity?,
    ) : Calendar {
        override fun calendars(): Flow<List<CalendarEntity>> = flowOf(calendars)
        override fun updateCalendarEnabled(calendarId: Int, enabled: Boolean) {}
        override suspend fun createEvent(calendarId: Int, event: NewCalendarEvent): String? = null
        override suspend fun defaultCalendar(): CalendarEntity? = default
    }

    private fun permissions(granted: Boolean) = object : PermissionRequester(
        RequiredPermissions(flowOf(emptySet())),
        AppResumed(),
    ) {
        override suspend fun requestPlatformPermission(
            permission: Permission,
            uiContext: PlatformUiContext,
        ): PermissionResult = PermissionResult.Granted

        override suspend fun hasPermission(permission: Permission): Boolean = granted
        override fun openPermissionsScreen(uiContext: PlatformUiContext) {}
    }

    private fun preferences(enabled: Boolean, target: Int? = null) =
        PreferencesImpl(MapSettings()).apply {
            setPhoneCalendarEnabled(enabled)
            setTargetCalendar(target)
        }

    @Test
    fun seedsPlatformDefaultWhenWritable() = runTest {
        val preferences = preferences(enabled = true)
        val calendars = listOf(calendar(1, "Work"), calendar(2, "Personal"))
        TargetCalendarSeeder(preferences, FakeCalendar(calendars, calendars[1]), permissions(true))
            .seedIfNeeded()
        assertEquals(2, preferences.targetCalendar.value)
    }

    @Test
    fun fallsBackToFirstWritableCalendar() = runTest {
        val preferences = preferences(enabled = true)
        val calendars = listOf(
            calendar(1, "Holidays", writable = false),
            calendar(2, "Personal"),
        )
        TargetCalendarSeeder(preferences, FakeCalendar(calendars, calendars[0]), permissions(true))
            .seedIfNeeded()
        assertEquals(2, preferences.targetCalendar.value)
    }

    @Test
    fun leavesExistingTargetAlone() = runTest {
        val preferences = preferences(enabled = true, target = 7)
        val calendars = listOf(calendar(1, "Work"))
        TargetCalendarSeeder(preferences, FakeCalendar(calendars, calendars[0]), permissions(true))
            .seedIfNeeded()
        assertEquals(7, preferences.targetCalendar.value)
    }

    @Test
    fun skipsWhenNotConnected() = runTest {
        val calendars = listOf(calendar(1, "Work"))
        val disabled = preferences(enabled = false)
        TargetCalendarSeeder(disabled, FakeCalendar(calendars, calendars[0]), permissions(true))
            .seedIfNeeded()
        assertNull(disabled.targetCalendar.value)

        val noPermission = preferences(enabled = true)
        TargetCalendarSeeder(noPermission, FakeCalendar(calendars, calendars[0]), permissions(false))
            .seedIfNeeded()
        assertNull(noPermission.targetCalendar.value)
    }

    @Test
    fun skipsWhenNothingIsWritable() = runTest {
        val preferences = preferences(enabled = true)
        val calendars = listOf(calendar(1, "Holidays", writable = false))
        TargetCalendarSeeder(preferences, FakeCalendar(calendars, calendars[0]), permissions(true))
            .seedIfNeeded()
        assertNull(preferences.targetCalendar.value)
    }
}
