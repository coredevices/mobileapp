package io.rebble.libpebblecommon.health

/**
 * Centralized constants for health data processing
 */
object HealthConstants {
    /**
     * Sleep overlay types that should be counted in sleep statistics.
     * Matches firmware's ActivityMetricSleepSeconds, which sums Sleep + Nap
     * containers with DeepSleep / DeepNap as nested subset intervals.
     */
    val SLEEP_TYPES = listOf(
        OverlayType.Sleep.value,
        OverlayType.DeepSleep.value,
        OverlayType.Nap.value,
        OverlayType.DeepNap.value,
    )

    /**
     * Sleep search window configuration.
     * Sleep sessions are searched from 6 PM the previous day to 2 PM the current day.
     */
    const val SLEEP_WINDOW_START_OFFSET_HOURS = 6  // 6 PM yesterday
    const val SLEEP_WINDOW_END_OFFSET_HOURS = 14   // 2 PM today

    /**
     * Gap threshold for grouping sleep sessions.
     * Sleep entries within this time gap are considered part of the same session.
     */
    const val SLEEP_SESSION_GAP_HOURS = 1L

    /**
     * Number of days to calculate health statistics averages over.
     */
    const val HEALTH_STATS_AVERAGE_DAYS = 30
}

/**
 * Extension function to check if an OverlayType represents any kind of sleep.
 * Includes regular sleep, deep sleep, naps, and deep naps.
 */
fun OverlayType.isSleepType(): Boolean =
    this == OverlayType.Sleep ||
    this == OverlayType.DeepSleep ||
    this == OverlayType.Nap ||
    this == OverlayType.DeepNap

