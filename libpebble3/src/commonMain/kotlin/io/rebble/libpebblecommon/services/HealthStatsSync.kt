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
import io.rebble.libpebblecommon.database.entity.HealthDataEntity
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
import kotlinx.datetime.toLocalDateTime

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
        val dailySleep = fetchAndGroupDailySleep(healthDao, dayStart, timeZone)
        val sleepPayloadData = sleepPayload(
            dayStart,
            dailySleep?.totalSleep?.toInt() ?: 0,
            dailySleep?.deepSleep?.toInt() ?: 0,
            dailySleep?.firstStart?.toInt() ?: 0,
            dailySleep?.lastEnd?.toInt() ?: 0
        )
        stats.add(HealthStat(
            key = sleepKey,
            payload = sleepPayloadData.toByteArray()
        ))
    }

    // Per-weekday typical-step blobs (consumed by firmware activity_insights for the
    // "X% above/below typical" comparison in the end-of-day activity summary notification).
    val typicalsByWeekday = computeAllWeekdayTypicalSteps(healthDao, today, timeZone)
    for ((dayOfWeek, payload) in typicalsByWeekday) {
        val key = STEP_TYPICAL_KEYS.getValue(dayOfWeek)
        stats.add(HealthStat(key = key, payload = payload))
    }
    logger.d { "HEALTH_STATS: Wrote ${typicalsByWeekday.size} weekday typical-step rows" }

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

/**
 * Bins a flat list of HealthDataEntity rows into per-weekday 15-minute typical-step payloads.
 *
 * For each weekday with at least MIN_DAYS_FOR_TYPICAL distinct matching days in the input,
 * emits a 192-byte little-endian payload of 96 UShort values. A slot's value is the average
 * step count across the distinct matching days that had at least one row overlapping that
 * slot; if zero matching days covered the slot, the value is UNKNOWN_TYPICAL_STEPS so the
 * watch sum-skips it. Weekdays below the threshold are omitted from the result.
 */
internal fun buildWeekdayTypicalsFromData(
    allData: List<HealthDataEntity>,
    timeZone: TimeZone,
): Map<DayOfWeek, ByteArray> {
    if (allData.isEmpty()) return emptyMap()

    val slotSteps = Array(7) { LongArray(TYPICAL_STEP_BINS) }
    val slotDays = Array(7) { Array(TYPICAL_STEP_BINS) { mutableSetOf<Long>() } }
    val matchingDays = Array(7) { mutableSetOf<Long>() }

    for (entry in allData) {
        val entryDate = kotlinx.datetime.Instant.fromEpochSeconds(entry.timestamp)
            .toLocalDateTime(timeZone).date
        val wd = entryDate.dayOfWeek.ordinal
        val dayStart = entryDate.atStartOfDayIn(timeZone).epochSeconds
        val slot = ((entry.timestamp - dayStart) / TYPICAL_STEP_BIN_SECONDS)
            .toInt()
            .coerceIn(0, TYPICAL_STEP_BINS - 1)
        slotSteps[wd][slot] += entry.steps.toLong()
        slotDays[wd][slot].add(dayStart)
        matchingDays[wd].add(dayStart)
    }

    val result = mutableMapOf<DayOfWeek, ByteArray>()
    for (wd in 0..6) {
        if (matchingDays[wd].size < MIN_DAYS_FOR_TYPICAL) continue
        val buffer = DataBuffer(TYPICAL_STEP_BINS * UShort.SIZE_BYTES)
            .apply { setEndian(Endian.Little) }
        for (slot in 0 until TYPICAL_STEP_BINS) {
            val count = slotDays[wd][slot].size
            val value: UShort = if (count == 0) {
                UNKNOWN_TYPICAL_STEPS
            } else {
                (slotSteps[wd][slot] / count).coerceIn(0L, 0xFFFEL).toInt().toUShort()
            }
            buffer.putUShort(value)
        }
        result[DayOfWeek.entries[wd]] = buffer.array().toByteArray()
    }
    return result
}

/**
 * Queries the last [TYPICAL_STEP_HISTORY_WEEKS] weeks of HealthDataEntity rows (excluding today)
 * and bins them into per-weekday 15-min typical-step payloads via [buildWeekdayTypicalsFromData].
 *
 * Returns one entry per weekday that has at least [MIN_DAYS_FOR_TYPICAL] distinct matching days
 * in the queried window. Empty map when there's no data or no weekday clears the threshold.
 */
/**
 * Sums the 96 little-endian UShort step counts in a typical-step payload, skipping the
 * UNKNOWN sentinel — i.e., "total typical steps for the day represented by this payload."
 *
 * Used by the debug stats dialog to surface the per-weekday typical totals; mirrors the
 * watch firmware's [`prv_cur_step_avg`](activity_insights.c:1143) which sums the same
 * array.
 */
internal fun decodeTypicalStepTotal(payload: ByteArray): Int {
    require(payload.size == TYPICAL_STEP_BINS * UShort.SIZE_BYTES) {
        "typical-step payload must be ${TYPICAL_STEP_BINS * UShort.SIZE_BYTES} bytes, got ${payload.size}"
    }
    var total = 0
    for (slot in 0 until TYPICAL_STEP_BINS) {
        val byteOffset = slot * 2
        val lo = payload[byteOffset].toInt() and 0xFF
        val hi = payload[byteOffset + 1].toInt() and 0xFF
        val v = (hi shl 8) or lo
        if (v != UNKNOWN_TYPICAL_STEPS.toInt()) total += v
    }
    return total
}

internal suspend fun computeAllWeekdayTypicalSteps(
    healthDao: HealthDao,
    today: LocalDate,
    timeZone: TimeZone,
): Map<DayOfWeek, ByteArray> {
    val rangeEnd = today.atStartOfDayIn(timeZone).epochSeconds
    val rangeStart = today
        .minus(DatePeriod(days = TYPICAL_STEP_HISTORY_WEEKS * 7))
        .atStartOfDayIn(timeZone)
        .epochSeconds
    val allData = healthDao.getHealthDataForRange(rangeStart, rangeEnd)
    return buildWeekdayTypicalsFromData(allData, timeZone)
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

private const val TYPICAL_STEP_BINS = 96
private const val TYPICAL_STEP_BIN_SECONDS = 900L
private const val TYPICAL_STEP_HISTORY_WEEKS = 7
private const val MIN_DAYS_FOR_TYPICAL = 2
private const val UNKNOWN_TYPICAL_STEPS: UShort = 0xFFFFu

private val STEP_TYPICAL_KEYS = mapOf(
    DayOfWeek.MONDAY to "monday_steps",
    DayOfWeek.TUESDAY to "tuesday_steps",
    DayOfWeek.WEDNESDAY to "wednesday_steps",
    DayOfWeek.THURSDAY to "thursday_steps",
    DayOfWeek.FRIDAY to "friday_steps",
    DayOfWeek.SATURDAY to "saturday_steps",
    DayOfWeek.SUNDAY to "sunday_steps",
)
