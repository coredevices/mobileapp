package coredevices.pebble.ui.health

import io.rebble.libpebblecommon.database.dao.HealthAggregates
import io.rebble.libpebblecommon.database.dao.HealthDao
import io.rebble.libpebblecommon.health.HealthTimeRange
import kotlinx.datetime.*
import kotlin.time.Duration.Companion.hours

/**
 * Data class containing steps metrics for display
 */
data class StepsMetrics(
    val distance: String,
    val calories: String,
    val activeTime: String
)

/**
 * Fetches and formats steps metrics for the given time range
 */
suspend fun fetchStepsMetrics(
    healthDao: HealthDao,
    timeRange: HealthTimeRange,
    useImperialUnits: Boolean
): StepsMetrics {
    return try {
        val timeZone = TimeZone.currentSystemDefault()
        val today = Clock.System.now().toLocalDateTime(timeZone).date

        val (aggregates, start, end) = when (timeRange) {
            HealthTimeRange.Daily -> {
                val s = today.atStartOfDayIn(timeZone).epochSeconds
                val e = today.plus(DatePeriod(days = 1)).atStartOfDayIn(timeZone).epochSeconds
                Triple(fetchDailyMetrics(healthDao, today, timeZone), s, e)
            }
            HealthTimeRange.Weekly -> {
                val weekStartSunday = getPreviousSunday(today)
                val s = weekStartSunday.atStartOfDayIn(timeZone).epochSeconds
                val e = weekStartSunday.plus(DatePeriod(days = 7)).atStartOfDayIn(timeZone).epochSeconds
                Triple(fetchWeeklyMetrics(healthDao, today, timeZone), s, e)
            }
            HealthTimeRange.Monthly -> {
                val monthStart = LocalDate(today.year, today.month, 1)
                val s = monthStart.atStartOfDayIn(timeZone).epochSeconds
                val e = monthStart.plus(DatePeriod(months = 1)).atStartOfDayIn(timeZone).epochSeconds
                Triple(fetchMonthlyMetrics(healthDao, today, timeZone), s, e)
            }
        }

        formatMetrics(aggregates, useImperialUnits, timeRange, healthDao, start, end)
    } catch (e: Exception) {
        println("Error fetching steps metrics: ${e.message}")
        e.printStackTrace()
        // Return zero metrics on error
        StepsMetrics("0", "0", "0")
    }
}

/**
 * Fetches metrics for today
 */
private suspend fun fetchDailyMetrics(
    healthDao: HealthDao,
    today: LocalDate,
    timeZone: TimeZone
): HealthAggregates? {
    val start = today.atStartOfDayIn(timeZone).epochSeconds
    val end = today.plus(DatePeriod(days = 1)).atStartOfDayIn(timeZone).epochSeconds
    return healthDao.getAggregatedHealthData(start, end)
}

/**
 * Fetches metrics for the current week
 */
private suspend fun fetchWeeklyMetrics(
    healthDao: HealthDao,
    today: LocalDate,
    timeZone: TimeZone
): HealthAggregates? {
    val weekStartSunday = getPreviousSunday(today)
    val start = weekStartSunday.atStartOfDayIn(timeZone).epochSeconds
    val end = weekStartSunday.plus(DatePeriod(days = 7)).atStartOfDayIn(timeZone).epochSeconds
    return healthDao.getAggregatedHealthData(start, end)
}

/**
 * Fetches metrics for the current month
 */
private suspend fun fetchMonthlyMetrics(
    healthDao: HealthDao,
    today: LocalDate,
    timeZone: TimeZone
): HealthAggregates? {
    val monthStart = LocalDate(today.year, today.month, 1)
    val start = monthStart.atStartOfDayIn(timeZone).epochSeconds
    val end = monthStart.plus(DatePeriod(months = 1)).atStartOfDayIn(timeZone).epochSeconds
    return healthDao.getAggregatedHealthData(start, end)
}

/**
 * Formats the aggregated health data into display strings
 */
private suspend fun formatMetrics(
    aggregates: HealthAggregates?,
    useImperialUnits: Boolean,
    timeRange: HealthTimeRange,
    healthDao: HealthDao,
    start: Long,
    end: Long
): StepsMetrics {
    if (aggregates == null) {
        return StepsMetrics("0", "0", "0")
    }

    // Get actual days with data for averaging
    val daysWithData = when (timeRange) {
        HealthTimeRange.Daily -> 1
        else -> healthDao.getDaysWithStepsData(start, end).coerceAtLeast(1)
    }

    // Distance formatting
    val distanceCm = aggregates.distanceCm ?: 0L
    val distance = formatDistance(distanceCm, useImperialUnits, daysWithData)

    // Calories formatting (convert from gram-calories to kilocalories)
    val totalGramCalories = (aggregates.activeGramCalories ?: 0L) + (aggregates.restingGramCalories ?: 0L)
    val calories = formatCalories(totalGramCalories, daysWithData)

    // Active time formatting
    val activeMinutes = aggregates.activeMinutes ?: 0L
    val activeTime = formatActiveTime(activeMinutes, daysWithData)

    return StepsMetrics(distance, calories, activeTime)
}

/**
 * Formats distance with appropriate units
 */
private fun formatDistance(distanceCm: Long, useImperialUnits: Boolean, daysWithData: Int): String {
    val avgDistanceCm = if (daysWithData > 1) distanceCm / daysWithData else distanceCm

    return if (useImperialUnits) {
        // Convert cm to miles (1 mile = 160934.4 cm)
        val miles = avgDistanceCm / 160934.4
        String.format("%.1fmi", miles)
    } else {
        // Convert cm to km (1 km = 100000 cm)
        val km = avgDistanceCm / 100000.0
        String.format("%.1fkm", km)
    }
}

/**
 * Formats calories (gram-calories to kilocalories)
 */
private fun formatCalories(gramCalories: Long, daysWithData: Int): String {
    val avgGramCalories = if (daysWithData > 1) gramCalories / daysWithData else gramCalories

    // Convert gram-calories to kilocalories (divide by 1000)
    val kcal = avgGramCalories / 1000
    return "$kcal"
}

/**
 * Formats active time as hours and minutes
 */
private fun formatActiveTime(activeMinutes: Long, daysWithData: Int): String {
    val avgActiveMinutes = if (daysWithData > 1) activeMinutes / daysWithData else activeMinutes

    val hours = avgActiveMinutes / 60
    val minutes = avgActiveMinutes % 60

    return if (hours > 0) {
        "${hours}h ${minutes}m"
    } else {
        "${minutes}m"
    }
}

/**
 * Gets the previous Sunday from the given date (or the date itself if it's Sunday)
 */
private fun getPreviousSunday(date: LocalDate): LocalDate {
    val dayOfWeek = date.dayOfWeek
    val daysToSubtract = when (dayOfWeek) {
        DayOfWeek.SUNDAY -> 0
        DayOfWeek.MONDAY -> 1
        DayOfWeek.TUESDAY -> 2
        DayOfWeek.WEDNESDAY -> 3
        DayOfWeek.THURSDAY -> 4
        DayOfWeek.FRIDAY -> 5
        DayOfWeek.SATURDAY -> 6
        else -> 0
    }
    return date.minus(DatePeriod(days = daysToSubtract))
}
