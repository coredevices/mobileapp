package io.rebble.libpebblecommon.health

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.minus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.hours

/**
 * Date/time utilities for health data processing
 */

/**
 * Checks if a year is a leap year.
 */
fun isLeapYear(year: Int): Boolean {
    return (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)
}

/**
 * Returns the number of days in a given month and year.
 */
fun getDaysInMonth(month: Month, year: Int): Int {
    return when (month) {
        Month.JANUARY -> 31
        Month.FEBRUARY -> if (isLeapYear(year)) 29 else 28
        Month.MARCH -> 31
        Month.APRIL -> 30
        Month.MAY -> 31
        Month.JUNE -> 30
        Month.JULY -> 31
        Month.AUGUST -> 31
        Month.SEPTEMBER -> 30
        Month.OCTOBER -> 31
        Month.NOVEMBER -> 30
        Month.DECEMBER -> 31
        else -> 30
    }
}

/**
 * Gets the previous Sunday from a given date (or the same date if it's already Sunday).
 */
fun getPreviousSunday(date: LocalDate): LocalDate {
    val dayOfWeek = date.dayOfWeek
    val daysToSubtract = when (dayOfWeek) {
        DayOfWeek.SUNDAY -> 0
        DayOfWeek.MONDAY -> 1
        DayOfWeek.TUESDAY -> 2
        DayOfWeek.WEDNESDAY -> 3
        DayOfWeek.THURSDAY -> 4
        DayOfWeek.FRIDAY -> 5
        DayOfWeek.SATURDAY -> 6
        else -> 0
    }
    return date.minus(DatePeriod(days = daysToSubtract))
}

/**
 * Formats a date range label (e.g., "Mar 27 - Apr 4" or "Mar 3-9").
 */
fun formatDateRangeLabel(startDate: LocalDate, endDate: LocalDate): String {
    val startMonth = startDate.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
    val endMonth = endDate.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }

    return if (startDate.month == endDate.month) {
        "$startMonth ${startDate.dayOfMonth}-${endDate.dayOfMonth}"
    } else {
        "$startMonth ${startDate.dayOfMonth} - $endMonth ${endDate.dayOfMonth}"
    }
}

/**
 * Formats a time label (e.g., "3PM" or "3:30PM").
 */
fun formatTimeLabel(instant: Instant, timeZone: TimeZone): String {
    val localTime = instant.toLocalDateTime(timeZone).time
    val hour24 = localTime.hour
    val minute = localTime.minute
    val amPm = if (hour24 >= 12) "PM" else "AM"
    val displayHour = when (val h = hour24 % 12) {
        0 -> 12
        else -> h
    }

    return if (minute == 0) {
        "$displayHour$amPm"
    } else {
        "%d:%02d%s".format(displayHour, minute, amPm)
    }
}

/**
 * Rounds an instant to the nearest hour.
 */
fun roundToNearestHour(instant: Instant, timeZone: TimeZone): Instant {
    val localDateTime = instant.toLocalDateTime(timeZone)
    val secondsPastHour = localDateTime.minute * 60 + localDateTime.second
    val hourStart = localDateTime.date.atTime(localDateTime.hour, 0, 0)
    val hourStartInstant = hourStart.toInstant(timeZone)
    return if (secondsPastHour >= 30 * 60) {
        hourStartInstant + 1.hours
    } else {
        hourStartInstant
    }
}
