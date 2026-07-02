package coredevices.pebble.backlight

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

const val BACKLIGHT_COLOR_USE_WEATHER_SCHEDULE_SETTINGS_KEY =
    "backlight_color_use_weather_schedule"

fun backlightScheduleMinuteOfDay(
    instant: Instant,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): Int {
    val local = instant.toLocalDateTime(timeZone)
    return local.hour * 60 + local.minute
}

fun formatBacklightScheduleMinute(minuteOfDay: Long): String {
    val minute = minuteOfDay.coerceIn(0, 1439)
    val hour = minute / 60
    val minutePart = minute % 60
    return "${hour.toString().padStart(2, '0')}:${minutePart.toString().padStart(2, '0')}"
}
