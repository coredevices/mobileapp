package coredevices.util

import androidx.compose.runtime.Composable
import coreapp.util.generated.resources.Res
import coreapp.util.generated.resources.relative_time_days_ago
import coreapp.util.generated.resources.relative_time_hours_ago
import coreapp.util.generated.resources.relative_time_just_now
import coreapp.util.generated.resources.relative_time_minutes_ago
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock
import kotlin.time.Instant

/** "just now" / "5 min ago" / "3h ago" / "2d ago": a short relative time. */
@Composable
fun relativeTime(at: Instant): String {
    val now = Clock.System.now().toEpochMilliseconds()
    val s = ((now - at.toEpochMilliseconds()) / 1000L).toInt()
    if (s < 60) return stringResource(Res.string.relative_time_just_now)
    val m = s / 60
    if (m < 60) return pluralStringResource(Res.plurals.relative_time_minutes_ago, m, m)
    val h = m / 60
    if (h < 24) return pluralStringResource(Res.plurals.relative_time_hours_ago, h, h)
    val d = h / 24
    return pluralStringResource(Res.plurals.relative_time_days_ago, d, d)
}
