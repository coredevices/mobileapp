package coredevices.util

import kotlin.time.Clock
import kotlin.time.Instant

/** "just now" / "5m ago" / "3h ago" / "2d ago": a short relative time. */
fun relativeTime(at: Instant): String {
    val now = Clock.System.now().toEpochMilliseconds()
    val s = ((now - at.toEpochMilliseconds()) / 1000L).toInt()
    if (s < 60) return "just now"
    val m = s / 60
    if (m < 60) return "${m}m ago"
    val h = m / 60
    if (h < 24) return "${h}h ago"
    return "${h / 24}d ago"
}
