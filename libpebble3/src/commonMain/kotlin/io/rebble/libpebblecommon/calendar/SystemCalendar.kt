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
}
