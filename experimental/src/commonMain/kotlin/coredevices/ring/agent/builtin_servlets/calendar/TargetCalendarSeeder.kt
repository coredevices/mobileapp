package coredevices.ring.agent.builtin_servlets.calendar

import co.touchlab.kermit.Logger
import coredevices.ring.database.Preferences
import coredevices.util.Permission
import coredevices.util.PermissionRequester
import io.rebble.libpebblecommon.connection.Calendar
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.minutes

/**
 * Users who connected Phone Calendar before a target calendar could be chosen get the platform's
 * default calendar (or the first writable one) filled in on start.
 */
class TargetCalendarSeeder(
    private val preferences: Preferences,
    private val calendar: Calendar,
    private val permissionRequester: PermissionRequester,
) {
    private val logger = Logger.withTag("TargetCalendarSeeder")

    suspend fun seedIfNeeded() {
        if (preferences.targetCalendar.value != null) return
        if (!preferences.phoneCalendarEnabled.value) return
        if (!permissionRequester.hasPermission(Permission.Calendar)) return

        val calendars = withTimeoutOrNull(CALENDAR_SYNC_TIMEOUT) {
            calendar.calendars().first { it.isNotEmpty() }
        } ?: return
        val default = calendar.defaultCalendar()?.takeIf { it.writable }
        val target = default ?: calendars.firstOrNull { it.writable } ?: return
        if (preferences.targetCalendar.value != null) return
        logger.i { "Seeding target calendar with '${target.name}'" }
        preferences.setTargetCalendar(target.id)
    }

    private companion object {
        val CALENDAR_SYNC_TIMEOUT = 1.minutes
    }
}
