package io.rebble.libpebblecommon.services

/**
 * Health statistics computation and database storage.
 *
 * Computes health statistics and stores them in the HealthStat Room entity.
 * The @GenerateRoomEntity infrastructure automatically syncs these stats to the watch via BlobDB.
 *
 * Stats sent to watch:
 * - 2 averages: average daily steps, average sleep duration (30-day window)
 * - 6 completed days: yesterday through 6 days ago (movement + sleep per day = 12 stats)
 *
 * TODAY's data is intentionally NOT sent to avoid conflicts with the watch's real-time
 * step counting. The day-of-week keys (monday_movementData, etc.) would cause the watch
 * to treat today's incomplete count as the final value, stopping step accumulation.
 *
 * This replaces the old direct BlobDB sending approach with a declarative Room-based pattern.
 */

import co.touchlab.kermit.Logger
import io.rebble.libpebblecommon.database.dao.DailyMovementAggregate
import io.rebble.libpebblecommon.database.dao.HealthAggregates
import io.rebble.libpebblecommon.database.dao.HealthDao
import io.rebble.libpebblecommon.database.entity.HealthStat
import io.rebble.libpebblecommon.database.entity.HealthStatDao
import io.rebble.libpebblecommon.util.DataBuffer
import io.rebble.libpebblecommon.util.Endian
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus

private val logger = Logger.withTag("HealthStatsSync")

/** Updates health stats in database for automatic syncing to watch */
internal suspend fun updateHealthStatsInDatabase(
    healthDao: HealthDao,
    healthStatDao: HealthStatDao,
    today: LocalDate,
    startDate: LocalDate,
    timeZone: TimeZone
): Boolean {
    val averages = calculateHealthAverages(healthDao, startDate, today, timeZone)
    if (averages.rangeDays <= 0) {
        logger.w { "HEALTH_STATS: Invalid date range (start=$startDate end=$today)" }
        return false
    }

    val averageSleepHours = averages.averageSleepSecondsPerDay / 3600.0

    logger.d {
        "HEALTH_STATS: 30-day averages window $startDate to $today (range=${averages.rangeDays} days, step days=${averages.stepDaysWithData}, sleep days=${averages.sleepDaysWithData})"
    }
    logger.d {
        "HEALTH_STATS: Average daily steps = ${averages.averageStepsPerDay} (total: ${averages.totalSteps} steps)"
    }
    logger.d {
        val sleepHrs = (averageSleepHours * 10).toInt() / 10.0
        "HEALTH_STATS: Average sleep = ${sleepHrs} hours (${averages.averageSleepSecondsPerDay} seconds, total: ${averages.totalSleepSeconds} seconds)"
    }

    val stats = mutableListOf<HealthStat>()

    // Add average stats
    stats.add(HealthStat(
        key = KEY_AVERAGE_DAILY_STEPS,
        payload = encodeUInt(averages.averageStepsPerDay.coerceAtLeast(0).toUInt()).toByteArray()
    ))
    stats.add(HealthStat(
        key = KEY_AVERAGE_SLEEP_DURATION,
        payload = encodeUInt(averages.averageSleepSecondsPerDay.coerceAtLeast(0).toUInt()).toByteArray()
    ))

    // Compute weekly movement and sleep data (excluding today)
    val oldestDate = today.minus(DatePeriod(days = MOVEMENT_HISTORY_DAYS - 1))
    val rangeStart = oldestDate.startOfDayEpochSeconds(timeZone)
    val rangeEnd = today.plus(DatePeriod(days = 1)).startOfDayEpochSeconds(timeZone)
    val allAggregates = healthDao.getDailyMovementAggregates(rangeStart, rangeEnd)
    val aggregatesByDayStart =
        allAggregates.associateBy {
            LocalDate.parse(it.day).atStartOfDayIn(timeZone).epochSeconds
        }

    // Send last 6 completed days (offset 1-6), skipping today (offset 0)
    repeat(MOVEMENT_HISTORY_DAYS - 1) { index ->
        val offset = index + 1  // Start from 1 (yesterday) instead of 0 (today)
        val day = today.minus(DatePeriod(days = offset))
        val dayStart = day.startOfDayEpochSeconds(timeZone)
        val movementKey = MOVEMENT_KEYS[day.dayOfWeek] ?: return@repeat
        val sleepKey = SLEEP_KEYS[day.dayOfWeek] ?: return@repeat

        // Add movement stat
        val aggregate = aggregatesByDayStart[dayStart]
        val movementPayloadData = movementPayload(dayStart, aggregate?.toHealthAggregates())
        stats.add(HealthStat(
            key = movementKey,
            payload = movementPayloadData.toByteArray()
        ))

        // Add sleep stat
        val mainSleep = fetchAndGroupDailySleep(healthDao, dayStart, timeZone)
        val sleepPayloadData = sleepPayload(
            dayStart,
            mainSleep?.totalSleep?.toInt() ?: 0,
            mainSleep?.deepSleep?.toInt() ?: 0,
            mainSleep?.start?.toInt() ?: 0,
            mainSleep?.end?.toInt() ?: 0
        )
        stats.add(HealthStat(
            key = sleepKey,
            payload = sleepPayloadData.toByteArray()
        ))
    }

    // Batch insert all stats
    healthStatDao.insertOrReplace(stats)
    logger.d { "HEALTH_STATS: Updated ${stats.size} stats in database for automatic syncing" }

    return true
}

// Payload generation functions - construct binary data for BlobDB
// These are called during stat computation and results are stored in HealthStat entity

/** Creates a sleep data payload for BlobDB */
private fun sleepPayload(
    dayStartEpochSec: Long,
    sleepDuration: Int,
    deepSleepDuration: Int,
    fallAsleepTime: Int,
    wakeupTime: Int
): UByteArray {
    val buffer = DataBuffer(SLEEP_PAYLOAD_SIZE).apply { setEndian(Endian.Little) }

    buffer.putUInt(HEALTH_STATS_VERSION) // version
    buffer.putUInt(dayStartEpochSec.toUInt()) // last_processed_timestamp
    buffer.putUInt(sleepDuration.toUInt()) // sleep_duration
    buffer.putUInt(deepSleepDuration.toUInt()) // deep_sleep_duration
    buffer.putUInt(fallAsleepTime.toUInt()) // fall_asleep_time
    buffer.putUInt(wakeupTime.toUInt()) // wakeup_time
    buffer.putUInt(0u) // typical_sleep_duration (we don't calculate this yet)
    buffer.putUInt(0u) // typical_deep_sleep_duration
    buffer.putUInt(0u) // typical_fall_asleep_time
    buffer.putUInt(0u) // typical_wakeup_time

    logger.d {
        "HEALTH_STATS: Sleep payload - version=$HEALTH_STATS_VERSION, timestamp=$dayStartEpochSec, " +
                "sleepDuration=$sleepDuration, deepSleep=$deepSleepDuration, fallAsleep=$fallAsleepTime, wakeup=$wakeupTime"
    }

    return buffer.array()
}

/** Creates a movement data payload for BlobDB */
private fun movementPayload(dayStartEpochSec: Long, aggregates: HealthAggregates?): UByteArray {
    val buffer = DataBuffer(MOVEMENT_PAYLOAD_SIZE).apply { setEndian(Endian.Little) }
    val steps = (aggregates?.steps ?: 0L).safeUInt()
    val activeKcal = (aggregates?.activeGramCalories ?: 0L).kilocalories().safeUInt()
    val restingKcal = (aggregates?.restingGramCalories ?: 0L).kilocalories().safeUInt()
    val distanceKm = (aggregates?.distanceCm ?: 0L).kilometers().safeUInt()
    val activeSec = (aggregates?.activeMinutes ?: 0L).toSeconds().safeUInt()

    buffer.putUInt(HEALTH_STATS_VERSION)
    buffer.putUInt(dayStartEpochSec.toUInt())
    buffer.putUInt(steps)
    buffer.putUInt(activeKcal)
    buffer.putUInt(restingKcal)
    buffer.putUInt(distanceKm)
    buffer.putUInt(activeSec)

    logger.d {
        "HEALTH_STATS: Movement payload - version=$HEALTH_STATS_VERSION, timestamp=$dayStartEpochSec, steps=$steps, activeKcal=$activeKcal, restingKcal=$restingKcal, distanceKm=$distanceKm, activeSec=$activeSec"
    }

    return buffer.array()
}

// Extension functions
private fun Long.kilocalories(): Long = this / 1000L

private fun Long.kilometers(): Long = this / 100000L

private fun Long.toSeconds(): Long = this * 60L

private fun Long.safeUInt(): UInt = this.coerceAtLeast(0L).coerceAtMost(UInt.MAX_VALUE.toLong()).toUInt()

private fun encodeUInt(value: UInt): UByteArray {
    val buffer = DataBuffer(UInt.SIZE_BYTES).apply { setEndian(Endian.Little) }
    buffer.putUInt(value)
    return buffer.array()
}

private fun LocalDate.startOfDayEpochSeconds(timeZone: TimeZone): Long = this.atStartOfDayIn(timeZone).epochSeconds

private fun DailyMovementAggregate.toHealthAggregates(): HealthAggregates =
    HealthAggregates(
        steps = this.steps,
        activeGramCalories = this.activeGramCalories,
        restingGramCalories = this.restingGramCalories,
        activeMinutes = this.activeMinutes,
        distanceCm = this.distanceCm
    )

// Constants
private const val MOVEMENT_HISTORY_DAYS = 7
private const val MOVEMENT_PAYLOAD_SIZE = UInt.SIZE_BYTES * 7
private const val SLEEP_PAYLOAD_SIZE = UInt.SIZE_BYTES * 10
private const val HEALTH_STATS_VERSION: UInt = 1u
private const val KEY_AVERAGE_DAILY_STEPS = "average_dailySteps"
private const val KEY_AVERAGE_SLEEP_DURATION = "average_sleepDuration"

private val MOVEMENT_KEYS =
    mapOf(DayOfWeek.MONDAY to "monday_movementData",
        DayOfWeek.TUESDAY to "tuesday_movementData",
        DayOfWeek.WEDNESDAY to "wednesday_movementData",
        DayOfWeek.THURSDAY to "thursday_movementData",
        DayOfWeek.FRIDAY to "friday_movementData",
        DayOfWeek.SATURDAY to "saturday_movementData",
        DayOfWeek.SUNDAY to "sunday_movementData"
    )

private val SLEEP_KEYS =
    mapOf(
        DayOfWeek.MONDAY to "monday_sleepData",
        DayOfWeek.TUESDAY to "tuesday_sleepData",
        DayOfWeek.WEDNESDAY to "wednesday_sleepData",
        DayOfWeek.THURSDAY to "thursday_sleepData",
        DayOfWeek.FRIDAY to "friday_sleepData",
        DayOfWeek.SATURDAY to "saturday_sleepData",
        DayOfWeek.SUNDAY to "sunday_sleepData",
    )
