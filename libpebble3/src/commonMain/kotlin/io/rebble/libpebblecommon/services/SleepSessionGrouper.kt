package io.rebble.libpebblecommon.services

import io.rebble.libpebblecommon.database.dao.HealthDao
import io.rebble.libpebblecommon.database.entity.OverlayDataEntity
import io.rebble.libpebblecommon.health.HealthConstants
import io.rebble.libpebblecommon.health.OverlayType
import io.rebble.libpebblecommon.health.calculateSleepSearchWindow
import kotlin.math.round

/**
 * Represents a grouped sleep session combining multiple sleep/deep sleep entries
 */
internal data class SleepSession(
    var start: Long,
    var end: Long,
    var totalSleep: Long = 0,
    var deepSleep: Long = 0
)

/**
 * Groups consecutive sleep entries into sessions.
 * Sleep and DeepSleep entries that are close together (within 1 hour) are part of the same session.
 *
 * Only Sleep type entries count toward total sleep duration.
 * DeepSleep is tracked separately as it's a subset of the total sleep (portion of sleep that was deep).
 */
internal fun groupSleepSessions(sleepEntries: List<OverlayDataEntity>): List<SleepSession> {
    val sessions = mutableListOf<SleepSession>()

    sleepEntries.sortedBy { it.startTime }.forEach { entry ->
        val overlayType = OverlayType.fromValue(entry.type)
        val entryEnd = entry.startTime + entry.duration

        // Find if this entry belongs to an existing session (within 1 hour of last entry)
        val existingSession = sessions.lastOrNull()?.takeIf {
            entry.startTime <= it.end + SLEEP_SESSION_GAP_SECONDS
        }

        if (existingSession != null) {
            // Add to existing session
            existingSession.end = maxOf(existingSession.end, entryEnd)
            // Only Sleep entries count toward total duration
            if (overlayType == OverlayType.Sleep) {
                existingSession.totalSleep += entry.duration
            }
            // DeepSleep is tracked separately (it's a subset of total sleep)
            if (overlayType == OverlayType.DeepSleep) {
                existingSession.deepSleep += entry.duration
            }
        } else {
            // Start new session
            sessions.add(
                SleepSession(
                    start = entry.startTime,
                    end = entryEnd,
                    totalSleep = if (overlayType == OverlayType.Sleep) entry.duration else 0,
                    deepSleep = if (overlayType == OverlayType.DeepSleep) entry.duration else 0
                )
            )
        }
    }

    return sessions
}

/**
 * Fetches sleep entries for a given day and groups them into sessions.
 * "Today's sleep" means you went to bed last night (6 PM yesterday) and woke up this morning/afternoon (2 PM today).
 *
 * @return The longest sleep session (main sleep, not naps) or null if no sleep data
 */
internal suspend fun fetchAndGroupDailySleep(
    healthDao: HealthDao,
    dayStartEpochSec: Long,
    timeZone: kotlinx.datetime.TimeZone
): SleepSession? {
    // Sleep for "today" means you went to bed last night (6 PM yesterday) and woke up this morning/afternoon (2 PM today)
    val (searchStart, searchEnd) = calculateSleepSearchWindow(dayStartEpochSec)

    val sleepEntries = healthDao.getOverlayEntries(searchStart, searchEnd, HealthConstants.SLEEP_TYPES)

    val logger = co.touchlab.kermit.Logger.withTag("SleepSessionGrouper")
    logger.d {
        val entries = sleepEntries.map { entry ->
            val type = OverlayType.fromValue(entry.type)?.name ?: "Unknown"
            val durationHrs = round(entry.duration / 360.0) / 10.0
            "  $type: start=${entry.startTime}, duration=${durationHrs}h"
        }.joinToString("\n")
        "Found ${sleepEntries.size} sleep entries in window [${searchStart}-${searchEnd}]:\n$entries"
    }

    val sessions = groupSleepSessions(sleepEntries)

    logger.d {
        val sessionsInfo = sessions.mapIndexed { idx, session ->
            val totalHrs = round(session.totalSleep / 360.0) / 10.0
            val deepHrs = round(session.deepSleep / 360.0) / 10.0
            "  Session $idx: total=${totalHrs}h, deep=${deepHrs}h, start=${session.start}, end=${session.end}"
        }.joinToString("\n")
        "Grouped into ${sessions.size} sessions:\n$sessionsInfo"
    }

    // Find the longest sleep session (main sleep, not naps)
    return sessions.maxByOrNull { it.totalSleep }
}

private const val SLEEP_SESSION_GAP_SECONDS = HealthConstants.SLEEP_SESSION_GAP_HOURS * 3600L
