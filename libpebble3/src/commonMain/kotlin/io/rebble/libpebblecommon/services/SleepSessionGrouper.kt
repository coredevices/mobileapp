package io.rebble.libpebblecommon.services

import io.rebble.libpebblecommon.database.dao.HealthDao
import io.rebble.libpebblecommon.database.entity.OverlayDataEntity
import io.rebble.libpebblecommon.health.HealthConstants
import io.rebble.libpebblecommon.health.OverlayType
import io.rebble.libpebblecommon.health.calculateSleepSearchWindow
import kotlin.math.round

/**
 * A single sleep overlay interval at its real timestamp.
 * Light intervals correspond to Sleep/Nap containers; deep intervals are
 * the DeepSleep/DeepNap sub-intervals that occur within them.
 */
data class SleepInterval(
    val start: Long,
    val end: Long,
    val isDeep: Boolean,
)

/**
 * Represents a grouped sleep session combining multiple sleep/deep sleep entries.
 * [intervals] holds the constituent overlay intervals at their real timestamps,
 * which the timeline UI renders directly so that interspersed deep-sleep periods
 * and awake gaps within the session show up correctly.
 */
data class SleepSession(
    var start: Long,
    var end: Long,
    var totalSleep: Long = 0,
    var deepSleep: Long = 0,
    val intervals: MutableList<SleepInterval> = mutableListOf(),
)

/**
 * A day's sleep, comprising one or more chronologically-ordered sessions
 * (e.g. a main sleep + a nap, or split sleep with a wake gap) plus the
 * aggregate totals across all sessions.
 *
 * Splitting bedtime/wake-up into [firstStart] / [lastEnd] preserves the full
 * span when there are multiple sessions, instead of collapsing to a single
 * "main" session and losing the others' time ranges.
 */
data class DailySleep(
    val sessions: List<SleepSession>,
    val totalSleep: Long,
    val deepSleep: Long,
) {
    val firstStart: Long get() = sessions.first().start
    val lastEnd: Long get() = sessions.last().end
    val intervals: List<SleepInterval> get() = sessions.flatMap { it.intervals }
}

/**
 * Groups consecutive sleep entries into sessions.
 * Entries within SLEEP_SESSION_GAP_HOURS of each other are part of the same session.
 *
 * Container overlays (Sleep, Nap) count toward totalSleep.
 * Subset overlays (DeepSleep, DeepNap) are nested inside their containers and count
 * toward deepSleep — they represent the same time already in totalSleep, not additional time.
 * This matches the firmware's ActivitySession model.
 */
fun groupSleepSessions(sleepEntries: List<OverlayDataEntity>): List<SleepSession> {
    val sessions = mutableListOf<SleepSession>()

    sleepEntries.sortedBy { it.startTime }.forEach { entry ->
        val overlayType = OverlayType.fromValue(entry.type)
        val isContainer = overlayType == OverlayType.Sleep || overlayType == OverlayType.Nap
        val isDeep = overlayType == OverlayType.DeepSleep || overlayType == OverlayType.DeepNap
        val entryEnd = entry.startTime + entry.duration
        val interval = SleepInterval(entry.startTime, entryEnd, isDeep)

        val existingSession = sessions.lastOrNull()?.takeIf {
            entry.startTime <= it.end + SLEEP_SESSION_GAP_SECONDS
        }

        if (existingSession != null) {
            existingSession.end = maxOf(existingSession.end, entryEnd)
            if (isContainer) existingSession.totalSleep += entry.duration
            if (isDeep) existingSession.deepSleep += entry.duration
            existingSession.intervals.add(interval)
        } else {
            sessions.add(
                SleepSession(
                    start = entry.startTime,
                    end = entryEnd,
                    totalSleep = if (isContainer) entry.duration else 0,
                    deepSleep = if (isDeep) entry.duration else 0,
                    intervals = mutableListOf(interval),
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
 * @return All sessions for the day plus aggregate totals, or null if no sleep data
 */
suspend fun fetchAndGroupDailySleep(
    healthDao: HealthDao,
    dayStartEpochSec: Long,
    timeZone: kotlinx.datetime.TimeZone
): DailySleep? {
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

    if (sessions.isEmpty()) return null
    return DailySleep(
        sessions = sessions,
        totalSleep = sessions.sumOf { it.totalSleep },
        deepSleep = sessions.sumOf { it.deepSleep },
    )
}

private const val SLEEP_SESSION_GAP_SECONDS = HealthConstants.SLEEP_SESSION_GAP_HOURS * 3600L
