package io.rebble.libpebblecommon.services

import co.touchlab.kermit.Logger
import coredev.BlobDatabase
import io.rebble.libpebblecommon.database.dao.HealthAggregates
import io.rebble.libpebblecommon.database.dao.HealthDao
import io.rebble.libpebblecommon.packets.blobdb.BlobCommand
import io.rebble.libpebblecommon.packets.blobdb.BlobResponse
import io.rebble.libpebblecommon.services.blobdb.BlobDBService
import io.rebble.libpebblecommon.util.DataBuffer
import io.rebble.libpebblecommon.util.Endian
import kotlin.random.Random
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus

private val logger = Logger.withTag("HealthStatsSync")

/** Sends complete health stats to watch (averages + weekly movement + weekly sleep) */
internal suspend fun sendHealthStatsToWatch(
        healthDao: HealthDao,
        blobDBService: BlobDBService,
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

    logger.i {
        "HEALTH_STATS: 30-day averages window $startDate to $today (range=${averages.rangeDays} days, step days=${averages.stepDaysWithData}, sleep days=${averages.sleepDaysWithData})"
    }
    logger.i {
        "HEALTH_STATS: Average daily steps = ${averages.averageStepsPerDay} (total: ${averages.totalSteps} steps)"
    }
    logger.i {
        val sleepHrs = (averageSleepHours * 10).toInt() / 10.0
        "HEALTH_STATS: Average sleep = ${sleepHrs} hours (${averages.averageSleepSecondsPerDay} seconds, total: ${averages.totalSleepSeconds} seconds)"
    }

    val monthlyStepsSent = sendAverageMonthlySteps(blobDBService, averages.averageStepsPerDay)
    val monthlySleepSent =
            sendAverageMonthlySleep(blobDBService, averages.averageSleepSecondsPerDay)
    val movementSent = sendWeeklyMovementData(healthDao, blobDBService, today, timeZone)
    val sleepSent = sendWeeklySleepData(healthDao, blobDBService, today, timeZone)

    val sentCount = listOf(monthlyStepsSent, monthlySleepSent, movementSent, sleepSent).count { it }
    if (sentCount > 0) {
        logger.i { "HEALTH_STATS: Successfully sent $sentCount stat categories to watch" }
    } else {
        logger.w { "HEALTH_STATS: Failed to send any stats to watch" }
    }

    return monthlyStepsSent || monthlySleepSent || movementSent || sleepSent
}

/** Sends weekly movement data (steps, calories, distance, active time) for the last 7 days */
internal suspend fun sendWeeklyMovementData(
        healthDao: HealthDao,
        blobDBService: BlobDBService,
        endDateInclusive: LocalDate,
        timeZone: TimeZone
): Boolean {
    logger.i { "HEALTH_STATS: Sending weekly movement data for last $MOVEMENT_HISTORY_DAYS days" }

    // Calculate range for single query
    // We want [endDateInclusive - 6 days, endDateInclusive]
    // Start of range is (endDate - 6).startOfDay
    // End of range is (endDate + 1).startOfDay (exclusive)
    val oldestDate = endDateInclusive.minus(DatePeriod(days = MOVEMENT_HISTORY_DAYS - 1))
    val rangeStart = oldestDate.startOfDayEpochSeconds(timeZone)
    val rangeEnd = endDateInclusive.plus(DatePeriod(days = 1)).startOfDayEpochSeconds(timeZone)

    val allAggregates = healthDao.getDailyMovementAggregates(rangeStart, rangeEnd)
    val aggregatesByDayStart =
            allAggregates.associateBy {
                LocalDate.parse(it.day).atStartOfDayIn(timeZone).epochSeconds
            }

    var anySent = false
    var successCount = 0

    repeat(MOVEMENT_HISTORY_DAYS) { offset ->
        val day = endDateInclusive.minus(DatePeriod(days = offset))
        val dayName = day.dayOfWeek.name
        val key = MOVEMENT_KEYS[day.dayOfWeek] ?: return@repeat
        val start = day.startOfDayEpochSeconds(timeZone)

        // Find aggregate for this day (sqlite returns dayStart in local time epoch seconds,
        // which matches startOfDayEpochSeconds if timezone matches)
        // Note: SQLite 'unixepoch' returns UTC so we used localtime conversion.
        // Verification: startOfDayEpochSeconds uses timeZone.
        // If system timezone != timeZone arg, this might drift.
        // Fallback: HealthService passes TimeZone.currentSystemDefault(), so it matches SQLite
        // 'localtime'.

        // We use a tolerance or exact match? Exact match should work if both are aligned to
        // midnight
        // But dayStart from sqlite might be shifted by timezone offset.
        // Ideally we filter list by range.
        val aggregate = aggregatesByDayStart[start]

        val steps = aggregate?.steps ?: 0L
        val activeKcal = (aggregate?.activeGramCalories ?: 0L) / 1000
        val restingKcal = (aggregate?.restingGramCalories ?: 0L) / 1000
        val distanceKm = (aggregate?.distanceCm ?: 0L) / 100000.0
        val activeMin = aggregate?.activeMinutes ?: 0L
        val activeSec = activeMin * 60

        logger.i {
            val distKm = (distanceKm * 100).toInt() / 100.0
            "HEALTH_STATS: $dayName ($day): steps=$steps, activeKcal=$activeKcal, restingKcal=$restingKcal, distance=${distKm}km, activeSec=$activeSec (${activeMin}min)"
        }

        // Convert DailyMovementAggregate to HealthAggregates for payload generation
        val healthAggregates =
                if (aggregate != null) {
                    HealthAggregates(
                            steps = aggregate.steps,
                            activeGramCalories = aggregate.activeGramCalories,
                            restingGramCalories = aggregate.restingGramCalories,
                            activeMinutes = aggregate.activeMinutes,
                            distanceCm = aggregate.distanceCm
                    )
                } else null

        val payload = movementPayload(start, healthAggregates)
        val sent = sendHealthStat(blobDBService, key, payload)
        if (sent) {
            successCount++
            logger.d { "HEALTH_STATS: Successfully sent $dayName movement data to watch" }
        } else {
            logger.w { "HEALTH_STATS: Failed to send $dayName movement data to watch" }
        }
        anySent = anySent || sent
    }

    logger.i {
        "HEALTH_STATS: Weekly movement data: sent $successCount/$MOVEMENT_HISTORY_DAYS days successfully"
    }
    return anySent
}

/** Sends today's movement data to watch */
internal suspend fun sendTodayMovementData(
        healthDao: HealthDao,
        blobDBService: BlobDBService,
        today: LocalDate,
        timeZone: TimeZone
): Boolean {
    val dayName = today.dayOfWeek.name
    val key = MOVEMENT_KEYS[today.dayOfWeek] ?: return false

    val start = today.startOfDayEpochSeconds(timeZone)
    val end = today.plus(DatePeriod(days = 1)).startOfDayEpochSeconds(timeZone)
    val aggregates = healthDao.getAggregatedHealthData(start, end)

    val steps = aggregates?.steps ?: 0L
    val activeKcal = (aggregates?.activeGramCalories ?: 0L) / 1000
    val restingKcal = (aggregates?.restingGramCalories ?: 0L) / 1000
    val distanceKm = (aggregates?.distanceCm ?: 0L) / 100000.0
    val activeMin = aggregates?.activeMinutes ?: 0L
    val activeSec = activeMin * 60

    logger.i {
        val distKm = (distanceKm * 100).toInt() / 100.0
        "HEALTH_STATS: Updating today's movement data ($dayName): steps=$steps, activeKcal=$activeKcal, restingKcal=$restingKcal, distance=${distKm}km, activeSec=$activeSec"
    }

    val payload = movementPayload(start, aggregates)
    val sent = sendHealthStat(blobDBService, key, payload)
    if (sent) {
        logger.d { "HEALTH_STATS: Successfully sent today's movement data to watch" }
    } else {
        logger.w { "HEALTH_STATS: Failed to send today's movement data to watch" }
    }
    return sent
}

/** Sends weekly sleep data for the last 7 days */
internal suspend fun sendWeeklySleepData(
        healthDao: HealthDao,
        blobDBService: BlobDBService,
        endDateInclusive: LocalDate,
        timeZone: TimeZone
): Boolean {
    logger.i { "HEALTH_STATS: Sending weekly sleep data for last $MOVEMENT_HISTORY_DAYS days" }
    var anySent = false
    var successCount = 0

    repeat(MOVEMENT_HISTORY_DAYS) { offset ->
        val day = endDateInclusive.minus(DatePeriod(days = offset))
        val sent = sendSingleDaySleep(healthDao, blobDBService, day, timeZone)
        if (sent) successCount++
        anySent = anySent || sent
    }

    logger.i {
        "HEALTH_STATS: Weekly sleep data: sent $successCount/$MOVEMENT_HISTORY_DAYS days successfully"
    }
    return anySent
}

/** Sends recent sleep data (yesterday and today) to watch */
internal suspend fun sendRecentSleepData(
        healthDao: HealthDao,
        blobDBService: BlobDBService,
        today: LocalDate,
        timeZone: TimeZone
) {
    logger.d { "HEALTH_STATS: Sending recent sleep data (yesterday and today)" }

    val yesterday = today.minus(DatePeriod(days = 1))
    sendSingleDaySleep(healthDao, blobDBService, yesterday, timeZone)
    sendSingleDaySleep(healthDao, blobDBService, today, timeZone)
}

/** Sends a single day's sleep data to watch */
internal suspend fun sendSingleDaySleep(
        healthDao: HealthDao,
        blobDBService: BlobDBService,
        day: LocalDate,
        timeZone: TimeZone
): Boolean {
    val dayName = day.dayOfWeek.name
    val key = SLEEP_KEYS[day.dayOfWeek] ?: return false
    val dayStartEpochSec = day.startOfDayEpochSeconds(timeZone)

    val mainSleep = fetchAndGroupDailySleep(healthDao, dayStartEpochSec, timeZone)

    val totalSleepSeconds = mainSleep?.totalSleep ?: 0L
    val deepSleepSeconds = mainSleep?.deepSleep ?: 0L
    val fallAsleepTime = mainSleep?.start?.toInt() ?: 0
    val wakeupTime = mainSleep?.end?.toInt() ?: 0

    val totalSleepHours = totalSleepSeconds / 3600.0

    logger.d {
        val sleepHrs = (totalSleepHours * 10).toInt() / 10.0
        "HEALTH_STATS: $dayName sleep: ${sleepHrs}h"
    }

    val payload =
            sleepPayload(
                    dayStartEpochSec,
                    totalSleepSeconds.toInt(),
                    deepSleepSeconds.toInt(),
                    fallAsleepTime,
                    wakeupTime
            )
    return sendHealthStat(blobDBService, key, payload)
}

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

/** Sends average monthly steps to watch */
private suspend fun sendAverageMonthlySteps(blobDBService: BlobDBService, steps: Int): Boolean {
    val result =
            sendHealthStat(
                    blobDBService,
                    KEY_AVERAGE_DAILY_STEPS,
                    encodeUInt(steps.coerceAtLeast(0).toUInt())
            )
    if (result) {
        logger.i { "HEALTH_STATS: Sent average daily steps to watch: $steps steps/day" }
    } else {
        logger.w { "HEALTH_STATS: Failed to send average daily steps to watch" }
    }
    return result
}

/** Sends average monthly sleep to watch */
private suspend fun sendAverageMonthlySleep(blobDBService: BlobDBService, seconds: Int): Boolean {
    val hours = seconds / 3600.0
    val result =
            sendHealthStat(
                    blobDBService,
                    KEY_AVERAGE_SLEEP_DURATION,
                    encodeUInt(seconds.coerceAtLeast(0).toUInt())
            )
    if (result) {
        val hrs = (hours * 10).toInt() / 10.0
        logger.i {
            "HEALTH_STATS: Sent average sleep duration to watch: ${hrs} hours ($seconds seconds/day)"
        }
    } else {
        logger.w { "HEALTH_STATS: Failed to send average sleep duration to watch" }
    }
    return result
}

/** Sends a health stat to watch via BlobDB */
private suspend fun sendHealthStat(
        blobDBService: BlobDBService,
        key: String,
        payload: UByteArray
): Boolean {
    val response =
            withTimeoutOrNull(HEALTH_STATS_BLOB_TIMEOUT_MS) {
                blobDBService.send(
                        BlobCommand.InsertCommand(
                                token = randomToken(),
                                database = BlobDatabase.HealthStats,
                                key = key.encodeToByteArray().toUByteArray(),
                                value = payload,
                        )
                )
            }
    val status = response?.responseValue ?: BlobResponse.BlobStatus.WatchDisconnected
    val success = status == BlobResponse.BlobStatus.Success
    if (!success) {
        logger.w { "HEALTH_STATS: BlobDB write failed for '$key' (status=$status)" }
    }
    return success
}

// Extension functions
private fun Long.kilocalories(): Long = this / 1000L

private fun Long.kilometers(): Long = this / 100000L

private fun Long.toSeconds(): Long = this * 60L

private fun Long.safeUInt(): UInt =
        this.coerceAtLeast(0L).coerceAtMost(UInt.MAX_VALUE.toLong()).toUInt()

private fun encodeUInt(value: UInt): UByteArray {
    val buffer = DataBuffer(UInt.SIZE_BYTES).apply { setEndian(Endian.Little) }
    buffer.putUInt(value)
    return buffer.array()
}

private fun LocalDate.startOfDayEpochSeconds(timeZone: TimeZone): Long =
        this.atStartOfDayIn(timeZone).epochSeconds

private fun randomToken(): UShort = Random.nextInt(0, UShort.MAX_VALUE.toInt()).toUShort()

// Constants
private const val MOVEMENT_HISTORY_DAYS = 7
private const val HEALTH_STATS_BLOB_TIMEOUT_MS = 5_000L
private const val MOVEMENT_PAYLOAD_SIZE = UInt.SIZE_BYTES * 7
private const val SLEEP_PAYLOAD_SIZE = UInt.SIZE_BYTES * 10
private const val HEALTH_STATS_VERSION: UInt = 1u
private const val KEY_AVERAGE_DAILY_STEPS = "average_dailySteps"
private const val KEY_AVERAGE_SLEEP_DURATION = "average_sleepDuration"

private val MOVEMENT_KEYS =
        mapOf(
                DayOfWeek.MONDAY to "monday_movementData",
                DayOfWeek.TUESDAY to "tuesday_movementData",
                DayOfWeek.WEDNESDAY to "wednesday_movementData",
                DayOfWeek.THURSDAY to "thursday_movementData",
                DayOfWeek.FRIDAY to "friday_movementData",
                DayOfWeek.SATURDAY to "saturday_movementData",
                DayOfWeek.SUNDAY to "sunday_movementData",
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
