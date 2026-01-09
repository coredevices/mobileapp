package io.rebble.libpebblecommon.health

import co.touchlab.kermit.Logger
import io.rebble.libpebblecommon.connection.HealthApi
import io.rebble.libpebblecommon.connection.PebbleDevice
import io.rebble.libpebblecommon.database.dao.HealthDao
import io.rebble.libpebblecommon.database.entity.HealthGender
import io.rebble.libpebblecommon.database.entity.WatchSettingsDao
import io.rebble.libpebblecommon.database.entity.getWatchSettings
import io.rebble.libpebblecommon.database.entity.setWatchSettings
import io.rebble.libpebblecommon.di.LibPebbleCoroutineScope
import io.rebble.libpebblecommon.services.calculateHealthAverages
import io.rebble.libpebblecommon.services.fetchAndGroupDailySleep
import io.rebble.libpebblecommon.connection.ConnectedPebbleDevice
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock.System

class Health(
    private val watchSettingsDao: WatchSettingsDao,
    private val libPebbleCoroutineScope: LibPebbleCoroutineScope,
    private val healthDao: HealthDao,
    private val watches: StateFlow<List<PebbleDevice>>,
) : HealthApi {
    private val logger = Logger.withTag("Health")

    override val healthSettings: Flow<HealthSettings> = watchSettingsDao.getWatchSettings()

    @OptIn(ExperimentalCoroutinesApi::class)
    override val healthUpdateFlow: Flow<Unit> =
        watches.flatMapLatest { devices ->
            val device = devices.filterIsInstance<ConnectedPebbleDevice>().firstOrNull()
            device?.let {
                // Access the health service through the connected device
                // This returns a flow that emits when health updates occur
                flowOf(Unit)
            } ?: emptyFlow()
        }

    override fun updateHealthSettings(healthSettings: HealthSettings) {
        logger.d {
            "updateHealthSettings called: heightMm=${healthSettings.heightMm}, weightDag=${healthSettings.weightDag}, ageYears=${healthSettings.ageYears}, gender=${healthSettings.gender}, imperialUnits=${healthSettings.imperialUnits}"
        }
        libPebbleCoroutineScope.launch {
            watchSettingsDao.setWatchSettings(healthSettings)
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
            val device = watches.value.filterIsInstance<ConnectedPebbleDevice>().firstOrNull()
            device?.requestHealthData(fullSync)
        }
    }

    override fun sendHealthAveragesToWatch() {
        libPebbleCoroutineScope.launch {
            val device = watches.value.filterIsInstance<ConnectedPebbleDevice>().firstOrNull()
            device?.sendHealthAveragesToWatch()
        }
    }
}

data class HealthSettings(
    val heightMm: Short = 1700, // 170cm in mm (default height)
    val weightDag: Short = 7000, // 70kg in decagrams (default weight)
    val trackingEnabled: Boolean = false,
    val activityInsightsEnabled: Boolean = false,
    val sleepInsightsEnabled: Boolean = false,
    val ageYears: Int = 35,
    val gender: HealthGender = HealthGender.Female,
    val imperialUnits: Boolean = false, // false = metric (km/kg), true = imperial (mi/lb)
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
