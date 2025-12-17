package io.rebble.libpebblecommon.health

import io.rebble.libpebblecommon.connection.HealthApi
import io.rebble.libpebblecommon.database.dao.HealthDao
import io.rebble.libpebblecommon.database.entity.HealthGender
import io.rebble.libpebblecommon.database.entity.WatchSettingsDao
import io.rebble.libpebblecommon.database.entity.getWatchSettings
import io.rebble.libpebblecommon.database.entity.setWatchSettings
import io.rebble.libpebblecommon.di.LibPebbleCoroutineScope
import io.rebble.libpebblecommon.services.HealthDebugStats
import io.rebble.libpebblecommon.services.calculateHealthAverages
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Interface for accessing connection-scoped HealthService functionality
 */
interface HealthServiceAccessor {
    fun requestHealthData(fullSync: Boolean)
    fun sendHealthAveragesToWatch()
    fun forceHealthDataOverwrite()
}

/**
 * Implementation that finds connected pebbles and calls their HealthService
 *
 * This uses a registry pattern where HealthService instances register themselves
 * when they're created for a connection.
 */
class RealHealthServiceAccessor(
    private val registry: HealthServiceRegistry,
): HealthServiceAccessor {
    private val logger = co.touchlab.kermit.Logger.withTag("RealHealthServiceAccessor")

    override fun requestHealthData(fullSync: Boolean) {
        val service = registry.getActiveHealthService()
        if (service == null) {
            logger.w { "No active watch to request health data from" }
            return
        }

        try {
            service.requestHealthData(fullSync)
            logger.i { "Requested ${if (fullSync) "full" else "incremental"} health data sync" }
        } catch (e: Exception) {
            logger.e(e) { "Failed to request health data" }
        }
    }

    override fun sendHealthAveragesToWatch() {
        val service = registry.getActiveHealthService()
        if (service == null) {
            logger.w { "No active watch available to send health averages to" }
            return
        }

        try {
            service.sendHealthAveragesToWatch()
            logger.i { "Requested manual health averages push" }
        } catch (e: Exception) {
            logger.e(e) { "Failed to send health averages to watch" }
        }
    }

    override fun forceHealthDataOverwrite() {
        val service = registry.getActiveHealthService()
        if (service == null) {
            logger.w { "No active watch available to force overwrite health data on" }
            return
        }

        try {
            service.forceHealthDataOverwrite()
            logger.i { "Forced health data overwrite on watch" }
        } catch (e: Exception) {
            logger.e(e) { "Failed to force health data overwrite on watch" }
        }
    }
}

/**
 * Registry to track active HealthService instances across connection scopes
 */
class HealthServiceRegistry {
    private val services = mutableListOf<io.rebble.libpebblecommon.services.HealthService>()
    private var activeService: io.rebble.libpebblecommon.services.HealthService? = null
    private val lock = Any()

    fun register(service: io.rebble.libpebblecommon.services.HealthService) {
        synchronized(lock) {
            services.remove(service)
            services.add(service)
            activeService = service
        }
    }

    fun unregister(service: io.rebble.libpebblecommon.services.HealthService) {
        synchronized(lock) {
            services.remove(service)
            if (activeService == service) {
                activeService = services.lastOrNull()
            }
        }
    }

    fun getAllHealthServices(): List<io.rebble.libpebblecommon.services.HealthService> {
        return synchronized(lock) {
            services.toList()
        }
    }

    fun getActiveHealthService(): io.rebble.libpebblecommon.services.HealthService? =
        synchronized(lock) { activeService }

    fun isActive(service: io.rebble.libpebblecommon.services.HealthService): Boolean =
        synchronized(lock) { activeService == service }
}

class Health(
    private val watchSettingsDao: WatchSettingsDao,
    private val libPebbleCoroutineScope: LibPebbleCoroutineScope,
    private val healthDao: HealthDao,
    private val healthServiceAccessor: HealthServiceAccessor,
): HealthApi {
    override val healthSettings: Flow<HealthSettings> = watchSettingsDao.getWatchSettings()

    override fun updateHealthSettings(healthSettings: HealthSettings) {
        libPebbleCoroutineScope.launch {
            watchSettingsDao.setWatchSettings(healthSettings)
        }
    }

    override suspend fun getHealthDebugStats(): HealthDebugStats {
        // This function operates on the shared database, so it doesn't need a connection
        val timeZone = TimeZone.currentSystemDefault()
        val today = kotlin.time.Clock.System.now().toLocalDateTime(timeZone).date
        val startDate = today.minus(DatePeriod(days = 30))

        val todayStart = today.atStartOfDayIn(timeZone).epochSeconds
        val todayEnd = today.plus(DatePeriod(days = 1)).atStartOfDayIn(timeZone).epochSeconds

        val averages = calculateHealthAverages(healthDao, startDate, today, timeZone)
        val todaySteps = healthDao.getTotalStepsExclusiveEnd(todayStart, todayEnd) ?: 0L
        val latestTimestamp = healthDao.getLatestTimestamp()

        val daysOfData = maxOf(averages.stepDaysWithData, averages.sleepDaysWithData)

        return HealthDebugStats(
            totalSteps30Days = averages.totalSteps,
            averageStepsPerDay = averages.averageStepsPerDay,
            totalSleepSeconds30Days = averages.totalSleepSeconds,
            averageSleepSecondsPerDay = averages.averageSleepSecondsPerDay,
            todaySteps = todaySteps,
            latestDataTimestamp = latestTimestamp,
            daysOfData = daysOfData
        )
    }

    override fun requestHealthData(fullSync: Boolean) {
        libPebbleCoroutineScope.launch {
            healthServiceAccessor.requestHealthData(fullSync)
        }
    }

    override fun sendHealthAveragesToWatch() {
        libPebbleCoroutineScope.launch {
            healthServiceAccessor.sendHealthAveragesToWatch()
        }
    }

    override fun forceHealthDataOverwrite() {
        libPebbleCoroutineScope.launch {
            healthServiceAccessor.forceHealthDataOverwrite()
        }
    }
}

data class HealthSettings(
    val heightMm: Short = 165,
    val weightDag: Short = 6500,
    val trackingEnabled: Boolean = false,
    val activityInsightsEnabled: Boolean = false,
    val sleepInsightsEnabled: Boolean = false,
    val ageYears: Int = 35,
    val gender: HealthGender = HealthGender.Female,
)

/**
 * Time range for displaying health data
 */
enum class HealthTimeRange {
    Daily, Weekly, Monthly
}

/**
 * Data structure for stacked sleep charts (weekly/monthly views).
 */
data class StackedSleepData(
    val label: String,
    val lightSleepHours: Float,
    val deepSleepHours: Float
)

/**
 * Data structure for weekly aggregated data (for monthly charts broken into weeks).
 */
data class WeeklyAggregatedData(
    val label: String,  // e.g., "Mar 27 - Apr 4"
    val value: Float?,  // null when there's no data for this week
    val weekIndex: Int  // Position in the overall sequence
)

/**
 * Represents a segment of sleep in the daily view.
 */
data class SleepSegment(
    val startHour: Float,      // Hour of day (0-24)
    val durationHours: Float,
    val type: OverlayType      // Sleep or DeepSleep
)

/**
 * Daily sleep data with all segments and timing information.
 */
data class DailySleepData(
    val segments: List<SleepSegment>,
    val bedtime: Float,        // Start hour
    val wakeTime: Float,       // End hour
    val totalSleepHours: Float
)
