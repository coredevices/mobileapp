package coredevices.util

import kotlinx.datetime.LocalDate

expect fun isLocale24HourFormat(): Boolean

fun formatTimeOfDay(hour: Int, minute: Int, is24Hour: Boolean = isLocale24HourFormat()): String {
    val mm = minute.toString().padStart(2, '0')
    if (is24Hour) return "${hour.toString().padStart(2, '0')}:$mm"
    return "${hour12(hour)}:$mm ${amPm(hour)}"
}

/** "14:00" or "2 PM". */
fun formatHourOfDay(hour: Int, is24Hour: Boolean = isLocale24HourFormat()): String =
    if (is24Hour) formatTimeOfDay(hour, 0, is24Hour = true) else "${hour12(hour)} ${amPm(hour)}"

private fun hour12(hour: Int) = if (hour % 12 == 0) 12 else hour % 12

private fun amPm(hour: Int) = if (hour < 12) "AM" else "PM"

/** Formats [date] for the current locale from a CLDR skeleton, e.g. "yMMMd" -> "Mar 4, 2026" / "4 mar 2026". */
expect fun formatDate(date: LocalDate, skeleton: String): String
