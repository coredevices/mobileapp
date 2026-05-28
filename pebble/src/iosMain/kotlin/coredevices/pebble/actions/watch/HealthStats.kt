package coredevices.pebble.actions.watch

import io.rebble.libpebblecommon.health.HealthDebugStats

/**
 * Returns [HealthDebugStats] as a JSON string.
 */
fun healthDebugStatsToJson(stats: HealthDebugStats): String {
    val lastNight = stats.lastNightSleepHours?.toString() ?: "null"
    val latestTs = stats.latestDataTimestamp?.toString() ?: "null"
    val typicalSteps = stats.weekdayTypicalSteps.entries
        .joinToString(prefix = "{", postfix = "}") { (wd, total) -> "\"$wd\":$total" }
    return """{"totalSteps30Days":${stats.totalSteps30Days},"averageStepsPerDay":${stats.averageStepsPerDay},"totalSleepSeconds30Days":${stats.totalSleepSeconds30Days},"averageSleepSecondsPerDay":${stats.averageSleepSecondsPerDay},"todaySteps":${stats.todaySteps},"lastNightSleepHours":$lastNight,"latestDataTimestamp":$latestTs,"daysOfData":${stats.daysOfData},"weekdayTypicalSteps":$typicalSteps}"""
}
