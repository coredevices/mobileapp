package coredevices.util

expect fun isLocale24HourFormat(): Boolean

fun formatTimeOfDay(hour: Int, minute: Int, is24Hour: Boolean = isLocale24HourFormat()): String {
    val mm = minute.toString().padStart(2, '0')
    if (is24Hour) return "${hour.toString().padStart(2, '0')}:$mm"
    val h12 = if (hour % 12 == 0) 12 else hour % 12
    return "$h12:$mm ${if (hour < 12) "AM" else "PM"}"
}
