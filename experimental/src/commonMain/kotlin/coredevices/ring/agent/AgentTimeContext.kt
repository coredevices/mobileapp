package coredevices.ring.agent

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * A short reference line telling the model the current wall-clock time so time-dependent
 * answers (weather, news, "today"/"now") resolve against the moment the user actually spoke
 * rather than stale data.
 */
fun currentTimeContext(now: Instant, zone: TimeZone = TimeZone.currentSystemDefault()): String {
    val dt = now.toLocalDateTime(zone)
    val dayOfWeek = dt.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }
    val hour = dt.hour.toString().padStart(2, '0')
    val minute = dt.minute.toString().padStart(2, '0')
    return "For reference, the current date and time is $dayOfWeek ${dt.date} $hour:$minute ($zone). " +
        "Only use this if the request depends on the current time (e.g. weather, news, or a " +
        "relative date like \"today\"); otherwise ignore it."
}
