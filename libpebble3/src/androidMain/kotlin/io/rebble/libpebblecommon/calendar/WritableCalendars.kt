package io.rebble.libpebblecommon.calendar

import io.rebble.libpebblecommon.database.entity.CalendarEntity

// Kept out of AndroidSystemCalendar.kt: that file's top-level CalendarContract constants are
// null on the host JVM, so a unit test touching its facade class dies in the class initializer.

internal const val UNKNOWN_ACCOUNT = "unknown"

/**
 * Puts the account's own calendar (ownerAccount == accountName) first — that's the one Android
 * treats as primary, and where events go when the user hasn't picked a calendar.
 *
 * Deliberately does NOT order by IS_PRIMARY — that is a computed column that some calendar
 * providers reject in a sort clause (the query then throws / returns null), which would make
 * event creation silently fail even when writable calendars exist.
 */
internal fun List<CalendarEntity>.ownAccountFirst(): List<CalendarEntity> {
    val own = firstOrNull { it.ownerName != UNKNOWN_ACCOUNT && it.ownerId == it.ownerName }
        ?: return this
    return listOf(own) + filterNot { it.platformId == own.platformId }
}
