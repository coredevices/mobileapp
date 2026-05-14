package io.rebble.libpebblecommon.health

import co.touchlab.kermit.Logger
import io.rebble.libpebblecommon.connection.ConnectedPebbleDevice
import io.rebble.libpebblecommon.connection.HealthApi
import io.rebble.libpebblecommon.connection.HealthDataApi
import io.rebble.libpebblecommon.connection.LatestHeartRate
import io.rebble.libpebblecommon.connection.WatchManager
import io.rebble.libpebblecommon.database.dao.HealthDao
import io.rebble.libpebblecommon.database.entity.HealthDataEntity
import io.rebble.libpebblecommon.database.entity.HRMonitoringInterval
import io.rebble.libpebblecommon.database.entity.HealthGender
import io.rebble.libpebblecommon.database.entity.HealthSettingsEntryDao
import io.rebble.libpebblecommon.database.entity.HealthStatDao
import io.rebble.libpebblecommon.database.entity.OverlayDataEntity
import io.rebble.libpebblecommon.database.entity.getWatchSettings
import io.rebble.libpebblecommon.database.entity.setWatchSettings
import io.rebble.libpebblecommon.datalogging.HealthDataProcessor
import io.rebble.libpebblecommon.di.LibPebbleCoroutineScope
import io.rebble.libpebblecommon.services.DailySleep
import io.rebble.libpebblecommon.services.calculateHealthAverages
import io.rebble.libpebblecommon.services.groupSleepSessions
import io.rebble.libpebblecommon.services.fetchAndGroupDailySleep
import io.rebble.libpebblecommon.services.updateHealthStatsInDatabase
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock.System

class Health(
    private val healthSettingsDao: HealthSettingsEntryDao,
    private val libPebbleCoroutineScope: LibPebbleCoroutineScope,
    private val healthDao: HealthDao,
    private val healthStatDao: HealthStatDao,
    private val watchManager: WatchManager,
    private val healthDataProcessor: HealthDataProcessor,
) : HealthApi, HealthDataApi {
    private val logger = Logger.withTag("Health")

    companion object {
        private val HEALTH_STATS_AVERAGE_DAYS = 30
        private val MORNING_WAKE_HOUR = 7 // 7 AM for daily stats update
        private const val REST_HR_MIN_READINGS = 10
        private const val REST_HR_LOWEST_MINUTES = 5
    }

    override val healthDataUpdated: SharedFlow<Unit> = healthDataProcessor.healthDataUpdated

    override val healthSettings: Flow<HealthSettings> = healthSettingsDao.getWatchSettings()

    fun init() {
        startPeriodicStatsUpdate()
    }

    override fun updateHealthSettings(healthSettings: HealthSettings) {
        logger.d { "updateHealthSettings called: $healthSettings" }
        libPebbleCoroutineScope.launch {
            healthSettingsDao.setWatchSettings(healthSettings)
            logger.d { "Health settings saved to database - will sync to watch via BlobDB" }
        }
    }

    override suspend fun getHealthDebugStats(): HealthDebugStats {
        // This function operates on the shared database, so it doesn't need a connection
        val timeZone = TimeZone.currentSystemDefault()
        val today = System.now().toLocalDateTime(timeZone).date
        val startDate = today.minus(DatePeriod(days = 30))

        val todayStart = today.atStartOfDayIn(timeZone).epochSeconds
        val todayEnd = today.plus(DatePeriod(days = 1)).atStartOfDayIn(timeZone).epochSeconds

        logger.d { "HEALTH_DEBUG: Getting health stats for today=$today, todayStart=$todayStart, todayEnd=$todayEnd" }

        val averages = calculateHealthAverages(healthDao, startDate, today, timeZone)
        val todaySteps = healthDao.getTotalStepsExclusiveEnd(todayStart, todayEnd) ?: 0L
        val latestTimestamp = healthDao.getLatestTimestamp()

        logger.d { "HEALTH_DEBUG: todaySteps=$todaySteps, latestTimestamp=$latestTimestamp, averageSteps=${averages.averageStepsPerDay}" }

        val daysOfData = maxOf(averages.stepDaysWithData, averages.sleepDaysWithData)

        val lastNightSleep = fetchAndGroupDailySleep(healthDao, todayStart, timeZone)
        val lastNightSleepSeconds = lastNightSleep?.totalSleep ?: 0L
        val lastNightSleepHours =
            if (lastNightSleepSeconds > 0) lastNightSleepSeconds / 3600f else null

        return HealthDebugStats(
            totalSteps30Days = averages.totalSteps,
            averageStepsPerDay = averages.averageStepsPerDay,
            totalSleepSeconds30Days = averages.totalSleepSeconds,
            averageSleepSecondsPerDay = averages.averageSleepSecondsPerDay,
            todaySteps = todaySteps,
            lastNightSleepHours = lastNightSleepHours,
            latestDataTimestamp = latestTimestamp,
            daysOfData = daysOfData
        )
    }

    override fun requestHealthData(fullSync: Boolean) {
        libPebbleCoroutineScope.launch {
            val device = watchManager.watches.value.filterIsInstance<ConnectedPebbleDevice>().firstOrNull()
            device?.requestHealthData(fullSync)
        }
    }

    override fun sendHealthAveragesToWatch() {
        libPebbleCoroutineScope.launch {
            updateHealthStats()
        }
    }

    private fun startPeriodicStatsUpdate() {
        libPebbleCoroutineScope.launch {
            // Update health stats once daily at 7 AM
            while (true) {
                val timeZone = TimeZone.currentSystemDefault()
                val now = System.now().toLocalDateTime(timeZone)

                // Calculate next morning update time (7 AM tomorrow)
                val tomorrow = now.date.plus(DatePeriod(days = 1))
                val nextMorning =
                    LocalDateTime(
                        tomorrow.year,
                        tomorrow.month,
                        tomorrow.dayOfMonth,
                        MORNING_WAKE_HOUR,
                        0,
                        0
                    )
                val morningInstant = nextMorning.toInstant(timeZone)
                val delayUntilMorning = (morningInstant.toEpochMilliseconds() - System.now().toEpochMilliseconds()).coerceAtLeast(0L)

                logger.d { "HEALTH_STATS: Next scheduled update at $nextMorning (${delayUntilMorning / (60 * 60 * 1000)}h from now)" }
                delay(delayUntilMorning)

                logger.d { "HEALTH_STATS: Running scheduled daily stats update" }
                updateHealthStats()
            }
        }
    }

    private suspend fun updateHealthStats() {
        val latestTimestamp = healthDao.getLatestTimestamp()
        if (latestTimestamp == null || latestTimestamp <= 0) {
            logger.d { "Skipping health stats update; no health data available" }
            return
        }

        val timeZone = TimeZone.currentSystemDefault()
        val today = kotlin.time.Clock.System.now().toLocalDateTime(timeZone).date
        val startDate = today.minus(DatePeriod(days = HEALTH_STATS_AVERAGE_DAYS))

        val updated = updateHealthStatsInDatabase(healthDao, healthStatDao, today, startDate, timeZone)
        if (!updated) {
            logger.d { "Health stats update attempt finished without any writes" }
        } else {
            logger.d { "Health stats updated (latestTimestamp=$latestTimestamp)" }
        }
    }

    override suspend fun getLatestTimestamp(): Long? = healthDao.getLatestTimestamp()

    override suspend fun getHealthDataAfter(afterTimestamp: Long): List<HealthDataEntity> =
        healthDao.getHealthDataAfter(afterTimestamp)

    override suspend fun getOverlayEntriesAfter(
        afterTimestamp: Long,
        types: List<Int>
    ): List<OverlayDataEntity> = healthDao.getOverlayEntriesAfter(afterTimestamp, types)

    override suspend fun getHealthDataForRange(start: Long, end: Long): List<HealthDataEntity> =
        healthDao.getHealthDataForRange(start, end)

    override suspend fun getDailyAggregates(start: Long, end: Long) =
        healthDao.getDailyMovementAggregates(start, end)

    override suspend fun getTotalHealthData(start: Long, end: Long) =
        healthDao.getAggregatedHealthData(start, end)

    override suspend fun getAverageHeartRate(start: Long, end: Long) =
        healthDao.getAverageHeartRate(start, end)

    override suspend fun getSleepEntries(start: Long, end: Long) =
        healthDao.getOverlayEntries(start, end, HealthConstants.SLEEP_TYPES)

    override suspend fun getDailySleepSession(dayStartEpochSec: Long): DailySleep? =
        fetchAndGroupDailySleep(healthDao, dayStartEpochSec, TimeZone.currentSystemDefault())

    override suspend fun getLatestHeartRateReading(): LatestHeartRate? {
        val entry = healthDao.getLatestHeartRateReading() ?: return null
        return LatestHeartRate(bpm = entry.heartRate, timestampEpochSec = entry.timestamp)
    }

    override suspend fun getRestingHeartRate(dayStartEpochSec: Long): Int? {
        val sleep = getDailySleepSession(dayStartEpochSec) ?: return null
        // Light-sleep containers (Sleep + Nap) cover the full sleep window; deep intervals are
        // nested inside them, so we'd double-count if we summed all intervals. Use containers only.
        val lightIntervals = sleep.intervals.filterNot { it.isDeep }
        val readings = mutableListOf<Int>()
        for (interval in lightIntervals) {
            healthDao.getHealthDataForRange(interval.start, interval.end).forEach { row ->
                if (row.heartRate > 0) readings.add(row.heartRate)
            }
        }
        if (readings.size < REST_HR_MIN_READINGS) return null
        val lowest = readings.sorted().take(REST_HR_LOWEST_MINUTES)
        return (lowest.sum().toDouble() / lowest.size).roundToInt()
    }

    override suspend fun getHRZoneMinutes(start: Long, end: Long): Map<Int, Long> =
        healthDao.getHeartRateZoneMinutes(start, end).associate { it.heartRateZone to it.minutes }

    override suspend fun getActivitySessions(start: Long, end: Long): List<OverlayDataEntity> =
        healthDao.getOverlayEntries(start, end, listOf(
            OverlayType.Walk.value, OverlayType.Run.value, OverlayType.OpenWorkout.value
        ))

    override suspend fun getTypicalSteps(dayOfWeek: Int): List<Long> {
        val timeZone = TimeZone.currentSystemDefault()
        val today = System.now().toLocalDateTime(timeZone).date
        // Find the most recent occurrence of this weekday (excluding today)
        var refDate = today.minus(DatePeriod(days = 1))
        while (refDate.dayOfWeek.ordinal != dayOfWeek) {
            refDate = refDate.minus(DatePeriod(days = 1))
        }
        val oldest = refDate.minus(DatePeriod(days = 7 * 7))
        val rangeStart = oldest.atStartOfDayIn(timeZone).epochSeconds
        val rangeEnd = refDate.plus(DatePeriod(days = 1)).atStartOfDayIn(timeZone).epochSeconds

        val allData = healthDao.getHealthDataForRange(rangeStart, rangeEnd)
        if (allData.isEmpty()) return emptyList()

        val hourlyTotals = LongArray(24)
        val matchingDays = mutableSetOf<Long>()
        for (entry in allData) {
            val entryDate = kotlinx.datetime.Instant.fromEpochSeconds(entry.timestamp)
                .toLocalDateTime(timeZone).date
            if (entryDate.dayOfWeek.ordinal != dayOfWeek) continue
            val dayStart = entryDate.atStartOfDayIn(timeZone).epochSeconds
            matchingDays.add(dayStart)
            val hour = ((entry.timestamp - dayStart) / 3600).toInt().coerceIn(0, 23)
            hourlyTotals[hour] += entry.steps
        }
        if (matchingDays.isEmpty()) return emptyList()
        return hourlyTotals.map { it / matchingDays.size }
    }

    override suspend fun getTypicalSleepSeconds(): Long {
        val timeZone = TimeZone.currentSystemDefault()
        val today = System.now().toLocalDateTime(timeZone).date
        val rangeStart = today.minus(DatePeriod(days = 30)).atStartOfDayIn(timeZone).epochSeconds
        val rangeEnd = today.atStartOfDayIn(timeZone).epochSeconds
        val allEntries = healthDao.getOverlayEntries(rangeStart, rangeEnd, HealthConstants.SLEEP_TYPES)
        if (allEntries.isEmpty()) return 0L

        val sessions = groupSleepSessions(allEntries)
        val validSessions = sessions.filter { it.totalSleep > 1800 }
        return if (validSessions.isNotEmpty()) validSessions.sumOf { it.totalSleep } / validSessions.size else 0L
    }

    override suspend fun populateDebugHealthData() {
        healthDao.deleteExpiredHealthData(Long.MAX_VALUE)
        healthDao.deleteExpiredOverlayData(Long.MAX_VALUE)

        val timeZone = TimeZone.currentSystemDefault()
        val today = System.now().toLocalDateTime(timeZone).date

        for (daysAgo in 0..29) {
            val date = today.minus(DatePeriod(days = daysAgo))
            val dayStart = date.atStartOfDayIn(timeZone).epochSeconds
            val dow = date.dayOfWeek.ordinal
            val random = kotlin.random.Random(dayStart.toInt())

            // Day-of-week personality: weekends are lazier, some weekdays more active
            val activityMultiplier = when (dow) {
                0 -> 1.2f  // Monday - motivated
                1 -> 1.0f  // Tuesday
                2 -> 1.3f  // Wednesday - peak
                3 -> 0.9f  // Thursday - tired
                4 -> 1.1f  // Friday
                5 -> 0.6f  // Saturday - lazy
                6 -> 0.7f  // Sunday - lazy
                else -> 1.0f
            }
            // Per-day variance so each day looks different
            val dayVariance = 0.7f + random.nextFloat() * 0.6f

            val healthEntries = mutableListOf<HealthDataEntity>()
            for (minute in 0 until 1440) {
                val hour = minute / 60
                val isAwake = hour in 7..22
                val baseSteps = if (isAwake) {
                    when {
                        hour in 8..9 -> random.nextInt(5, 35)
                        hour in 12..13 -> random.nextInt(8, 45)
                        hour in 17..18 -> random.nextInt(10, 55)
                        hour == 7 -> random.nextInt(2, 12)
                        hour in 20..22 -> random.nextInt(0, 8)
                        else -> random.nextInt(0, 18)
                    }
                } else 0
                val steps = (baseSteps * activityMultiplier * dayVariance).toInt()
                val heartRate = if (isAwake) {
                    random.nextInt(58, 95) + (steps / 3)
                } else {
                    random.nextInt(48, 63)
                }

                healthEntries.add(
                    HealthDataEntity(
                        timestamp = dayStart + minute * 60L,
                        steps = steps,
                        orientation = 0,
                        intensity = if (steps > 20) 2 else if (steps > 0) 1 else 0,
                        lightIntensity = if (isAwake) 50 else 0,
                        activeMinutes = if (steps > 10) 1 else 0,
                        restingGramCalories = random.nextInt(800, 1200),
                        activeGramCalories = steps * random.nextInt(3, 8),
                        distanceCm = steps * random.nextInt(50, 80),
                        heartRate = heartRate,
                        heartRateZone = when {
                            heartRate > 120 -> 3
                            heartRate > 90 -> 2
                            heartRate > 70 -> 1
                            else -> 0
                        },
                        heartRateWeight = if (heartRate > 0) 10 else 0,
                    )
                )
            }
            healthDao.insertHealthData(healthEntries)

            // Sleep: weekends stay up later, variance per day
            val baseBedtimeHour = if (dow >= 4) 23 else 22
            val bedtimeHour = baseBedtimeHour + random.nextInt(0, 2)
            val bedtimeMinute = random.nextInt(0, 60)
            val sleepStart = dayStart - (24 - bedtimeHour) * 3600L + bedtimeMinute * 60L
            val baseSleepHours = if (dow >= 5) random.nextInt(7, 10) else random.nextInt(5, 8)
            val totalSleepSec = baseSleepHours * 3600L + random.nextInt(0, 60) * 60L
            val deepFraction = random.nextDouble(0.15, 0.35)

            val overlays = mutableListOf<OverlayDataEntity>()
            fun addOverlay(start: Long, duration: Long, type: OverlayType) {
                overlays += OverlayDataEntity(
                    startTime = start, duration = duration, type = type.value,
                    steps = 0, restingKiloCalories = 0, activeKiloCalories = 0,
                    distanceCm = 0, offsetUTC = 0,
                )
            }
            // Drop n DeepSleep blocks roughly evenly inside [start, start+duration].
            fun addDeepSleeps(start: Long, duration: Long, n: Int) {
                if (n <= 0 || duration <= 0) return
                val deepEach = (duration * deepFraction / n).toLong()
                val slot = duration / (n + 1)
                repeat(n) { i ->
                    addOverlay(start + slot * (i + 1) - deepEach / 2, deepEach, OverlayType.DeepSleep)
                }
            }

            // Mix in split-sleep and interrupted-sleep nights so the daily timeline shows
            // off multi-session and within-session-awake-gap rendering.
            when {
                random.nextFloat() < 0.20f -> {
                    // Split sleep: two sessions separated by > 1h (the grouper splits them).
                    val firstDur = (totalSleepSec * 0.55).toLong()
                    val gap = 5400L + random.nextInt(5400)  // 1.5h–3h
                    val secondDur = totalSleepSec - firstDur
                    val secondStart = sleepStart + firstDur + gap
                    addOverlay(sleepStart, firstDur, OverlayType.Sleep)
                    addDeepSleeps(sleepStart, firstDur, random.nextInt(1, 3))
                    addOverlay(secondStart, secondDur, OverlayType.Sleep)
                    addDeepSleeps(secondStart, secondDur, random.nextInt(1, 3))
                }
                random.nextFloat() < 0.25f -> {
                    // Interrupted sleep: one session with a short awake gap inside it.
                    val firstDur = (totalSleepSec * 0.6).toLong()
                    val gap = (random.nextInt(14, 31) * 60).toLong()  // 14–30 min
                    val secondDur = totalSleepSec - firstDur
                    val secondStart = sleepStart + firstDur + gap
                    addOverlay(sleepStart, firstDur, OverlayType.Sleep)
                    addDeepSleeps(sleepStart, firstDur, random.nextInt(2, 4))
                    addOverlay(secondStart, secondDur, OverlayType.Sleep)
                    addDeepSleeps(secondStart, secondDur, random.nextInt(1, 3))
                }
                else -> {
                    // Typical uninterrupted night with multiple deep-sleep cycles.
                    addOverlay(sleepStart, totalSleepSec, OverlayType.Sleep)
                    addDeepSleeps(sleepStart, totalSleepSec, random.nextInt(2, 4))
                }
            }

            // Add activity sessions on some days
            if (random.nextFloat() > 0.4f) {
                val sessionHour = if (random.nextBoolean()) random.nextInt(7, 9) else random.nextInt(17, 19)
                val sessionStart = dayStart + sessionHour * 3600L
                val sessionDur = random.nextInt(15, 60) * 60L
                val sessionType = if (random.nextBoolean()) OverlayType.Walk else OverlayType.Run
                overlays.add(OverlayDataEntity(
                    startTime = sessionStart, duration = sessionDur,
                    type = sessionType.value,
                    steps = (sessionDur / 60 * random.nextInt(80, 160)).toInt(),
                    restingKiloCalories = 0,
                    activeKiloCalories = (sessionDur / 60 * random.nextInt(4, 10)).toInt(),
                    distanceCm = (sessionDur / 60 * random.nextInt(50, 150)).toInt(),
                    offsetUTC = 0,
                ))
            }

            healthDao.insertOverlayData(overlays)
        }

        logger.d { "DEBUG: Populated 30 days of varied fake health data" }
        healthDataProcessor.emitHealthDataUpdated()
    }
}

data class HealthSettings(
    val heightMm: Short,
    val weightDag: Short,
    val trackingEnabled: Boolean,
    val activityInsightsEnabled: Boolean,
    val sleepInsightsEnabled: Boolean,
    val ageYears: Int,
    val gender: HealthGender,
    val imperialUnits: Boolean,
    // ActivityHRMSettings
    val hrmEnabled: Boolean,
    val hrmMeasurementInterval: HRMonitoringInterval,
    val hrmActivityTrackingEnabled: Boolean,
    // HeartRatePreferences
    val restingHr: Short,
    val elevatedHr: Short,
    val maxHr: Short,
    val hrZone1Threshold: Short,
    val hrZone2Threshold: Short,
    val hrZone3Threshold: Short,
)

/** Time range for displaying health data */
enum class HealthTimeRange {
    Daily,
    Weekly,
    Monthly
}

/** Data structure for stacked sleep charts (weekly/monthly views). */
data class StackedSleepData(
    val label: String,
    val lightSleepHours: Float,
    val deepSleepHours: Float
)

/** Data structure for weekly aggregated data (for monthly charts broken into weeks). */
data class WeeklyAggregatedData(
    val label: String, // e.g., "Mar 27 - Apr 4"
    val value: Float?, // null when there's no data for this week
    val weekIndex: Int // Position in the overall sequence
)

/** Represents a segment of sleep in the daily view. */
data class SleepSegment(
    val startHour: Float, // Hour of day (0-24)
    val durationHours: Float,
    val type: OverlayType // Sleep or DeepSleep
)

/** Daily sleep data with all segments and timing information. */
data class DailySleepData(
    val segments: List<SleepSegment>,
    val bedtime: Float, // Start hour
    val wakeTime: Float, // End hour
    val totalSleepHours: Float
)
