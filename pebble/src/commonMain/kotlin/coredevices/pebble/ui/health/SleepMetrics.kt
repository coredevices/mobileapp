package coredevices.pebble.ui.health

import io.rebble.libpebblecommon.database.dao.HealthDao
import io.rebble.libpebblecommon.health.HealthTimeRange
import kotlinx.datetime.*

/**
 * Data class containing sleep metrics for display
 */
data class SleepMetrics(
    val hoursSlept: String,
    val deepSleep: String
)

/**
 * Fetches and formats sleep metrics for the given time range
 * @param offset Number of periods to go back (0 = current period, 1 = previous period, etc.)
 */
suspend fun fetchSleepMetrics(
    healthDao: HealthDao,
    timeRange: HealthTimeRange,
    offset: Int = 0
): SleepMetrics {
    return try {
        val timeZone = TimeZone.currentSystemDefault()
        val today = Clock.System.now().toLocalDateTime(timeZone).date

        val targetDate = when (timeRange) {
            HealthTimeRange.Daily -> today.minus(DatePeriod(days = offset))
            HealthTimeRange.Weekly -> today.minus(DatePeriod(days = offset * 7))
            HealthTimeRange.Monthly -> today.minus(DatePeriod(months = offset))
        }

        val (totalMinutes, deepMinutes) = when (timeRange) {
            HealthTimeRange.Daily -> fetchDailySleepMetrics(healthDao, targetDate, timeZone)
            HealthTimeRange.Weekly -> fetchWeeklySleepMetrics(healthDao, targetDate, timeZone)
            HealthTimeRange.Monthly -> fetchMonthlySleepMetrics(healthDao, targetDate, timeZone)
        }

        formatSleepMetrics(totalMinutes, deepMinutes, timeRange)
    } catch (e: Exception) {
        println("Error fetching sleep metrics: ${e.message}")
        e.printStackTrace()
        SleepMetrics("0h", "0h")
    }
}

/**
 * Fetches sleep metrics for today
 */
private suspend fun fetchDailySleepMetrics(
    healthDao: HealthDao,
    today: LocalDate,
    timeZone: TimeZone
): Pair<Long, Long> {
    val start = today.atStartOfDayIn(timeZone).epochSeconds
    val end = today.plus(DatePeriod(days = 1)).atStartOfDayIn(timeZone).epochSeconds

    val totalMinutes = healthDao.getTotalSleepMinutes(start, end) ?: 0L
    val deepMinutes = healthDao.getDeepSleepMinutes(start, end) ?: 0L

    return Pair(totalMinutes, deepMinutes)
}

/**
 * Fetches sleep metrics for the current week
 */
private suspend fun fetchWeeklySleepMetrics(
    healthDao: HealthDao,
    today: LocalDate,
    timeZone: TimeZone
): Pair<Long, Long> {
    val weekStartSunday = getPreviousSunday(today)
    val start = weekStartSunday.atStartOfDayIn(timeZone).epochSeconds
    val end = weekStartSunday.plus(DatePeriod(days = 7)).atStartOfDayIn(timeZone).epochSeconds

    val totalMinutes = healthDao.getTotalSleepMinutes(start, end) ?: 0L
    val deepMinutes = healthDao.getDeepSleepMinutes(start, end) ?: 0L
    val daysWithData = healthDao.getDaysWithSleepData(start, end)

    // Return average per day with data (avoid division by zero)
    return if (daysWithData > 0) {
        Pair(totalMinutes / daysWithData, deepMinutes / daysWithData)
    } else {
        Pair(0L, 0L)
    }
}

/**
 * Fetches sleep metrics for the current month
 */
private suspend fun fetchMonthlySleepMetrics(
    healthDao: HealthDao,
    today: LocalDate,
    timeZone: TimeZone
): Pair<Long, Long> {
    val monthStart = LocalDate(today.year, today.month, 1)
    val start = monthStart.atStartOfDayIn(timeZone).epochSeconds
    val end = monthStart.plus(DatePeriod(months = 1)).atStartOfDayIn(timeZone).epochSeconds

    val totalMinutes = healthDao.getTotalSleepMinutes(start, end) ?: 0L
    val deepMinutes = healthDao.getDeepSleepMinutes(start, end) ?: 0L
    val daysWithData = healthDao.getDaysWithSleepData(start, end)

    // Return average per day with data (avoid division by zero)
    return if (daysWithData > 0) {
        Pair(totalMinutes / daysWithData, deepMinutes / daysWithData)
    } else {
        Pair(0L, 0L)
    }
}

/**
 * Formats sleep metrics into display strings
 */
private fun formatSleepMetrics(
    totalMinutes: Long,
    deepMinutes: Long,
    timeRange: HealthTimeRange
): SleepMetrics {
    val hoursSlept = formatSleepTime(totalMinutes)
    val deepSleep = formatSleepTime(deepMinutes)

    return SleepMetrics(hoursSlept, deepSleep)
}

/**
 * Formats sleep time as hours and minutes
 */
private fun formatSleepTime(minutes: Long): String {
    val hours = minutes / 60
    val mins = minutes % 60

    return if (hours > 0 && mins > 0) {
        "${hours}h ${mins}m"
    } else if (hours > 0) {
        "${hours}h"
    } else {
        "${mins}m"
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
