package io.rebble.libpebblecommon.calendar

import io.rebble.libpebblecommon.database.entity.CalendarEntity
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

interface SystemCalendar {
    suspend fun getCalendars(): List<CalendarEntity>
    suspend fun getCalendarEvents(calendar: CalendarEntity, startDate: Instant, endDate: Instant): List<CalendarEvent>
    suspend fun enableSyncForCalendar(calendar: CalendarEntity)
    fun registerForCalendarChanges(): Flow<Unit>?
    fun hasPermission(): Boolean

    /**
     * Calendars the user can create events in, the platform's default for new events first.
     */
    suspend fun getWritableCalendars(): List<CalendarEntity>

    /**
     * Create an event in [NewCalendarEvent.calendarId], or the default calendar when that is
     * null or no longer writable.
     * @return the platform id of the created event, or null if creation failed (no permission,
     *         no writable calendar, or a platform error).
     */
    suspend fun createEvent(event: NewCalendarEvent): String?

    /**
     * Whether this platform can execute write-back pin actions (RSVP, cancel event).
     * Android: yes, via CalendarContract. iOS: no — EKParticipant is read-only and
     * EKEvent mutation requires user-owned calendars we don't reliably identify.
     */
    fun supportsPinActions(): Boolean
}

/**
 * Which calendar an event goes in, given [getWritableCalendars] and the user's choice: their
 * calendar if it's still writable, otherwise the platform default (first in the list).
 */
internal fun List<CalendarEntity>.resolveWritableTarget(requestedId: String?): CalendarEntity? =
    firstOrNull { it.platformId == requestedId } ?: firstOrNull()
