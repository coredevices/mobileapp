package io.rebble.libpebblecommon.health

import co.touchlab.kermit.Logger
import io.rebble.libpebblecommon.connection.ConnectedPebbleDevice
import io.rebble.libpebblecommon.connection.HealthApi
import io.rebble.libpebblecommon.connection.WatchManager
import io.rebble.libpebblecommon.database.dao.HealthDao
import io.rebble.libpebblecommon.database.entity.HealthGender
import io.rebble.libpebblecommon.database.entity.HealthSettingsEntryDao
import io.rebble.libpebblecommon.database.entity.HealthStatDao
import io.rebble.libpebblecommon.database.entity.getWatchSettings
import io.rebble.libpebblecommon.database.entity.setWatchSettings
import io.rebble.libpebblecommon.di.LibPebbleCoroutineScope
import io.rebble.libpebblecommon.services.calculateHealthAverages
import io.rebble.libpebblecommon.services.fetchAndGroupDailySleep
import io.rebble.libpebblecommon.services.updateHealthStatsInDatabase
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
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
) : HealthApi {
    private val logger = Logger.withTag("Health")

    companion object {
        private val HEALTH_STATS_AVERAGE_DAYS = 30
        private val MORNING_WAKE_HOUR = 7 // 7 AM for daily stats update
    }

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

        val lastNightSession = fetchAndGroupDailySleep(healthDao, todayStart, timeZone)
        val lastNightSleepSeconds = lastNightSession?.totalSleep ?: 0L
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
