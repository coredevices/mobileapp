package io.rebble.libpebblecommon.health

import io.rebble.libpebblecommon.database.dao.HealthDao
import kotlin.time.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.hours

/**
 * Repository for fetching and processing health data.
 * Separates data access logic from UI layer.
 */

/**
 * Fetches steps data for the given time range.
 * @return Triple of (labels, values, total)
 */
suspend fun fetchStepsData(
    healthDao: HealthDao,
    timeRange: HealthTimeRange
): Triple<List<String>, List<Float>, Long> {
    val timeZone = TimeZone.currentSystemDefault()
    val today = Clock.System.now().toLocalDateTime(timeZone).date

    return when (timeRange) {
        HealthTimeRange.Daily -> fetchDailyStepsData(healthDao, today, timeZone)
        HealthTimeRange.Weekly -> fetchWeeklyStepsData(healthDao, today, timeZone)
        HealthTimeRange.Monthly -> fetchMonthlyStepsData(healthDao, today, timeZone)
    }
}

private suspend fun fetchDailyStepsData(
    healthDao: HealthDao,
    today: LocalDate,
    timeZone: TimeZone
): Triple<List<String>, List<Float>, Long> {
    val nowInstant = Clock.System.now()
    val todayStart = today.atStartOfDayIn(timeZone).epochSeconds

    // Get wakeup time from sleep data (if available)
    val searchStart = today.minus(DatePeriod(days = 1)).atStartOfDayIn(timeZone).epochSeconds + (18 * 3600)
    val searchEnd = today.atStartOfDayIn(timeZone).epochSeconds + (14 * 3600)
    val sleepTypes = listOf(OverlayType.Sleep.value, OverlayType.DeepSleep.value)
    val sleepEntries = healthDao.getOverlayEntries(searchStart, searchEnd, sleepTypes)
        .sortedBy { it.startTime }

    val wakeupInstant = sleepEntries.maxOfOrNull { it.startTime + it.duration }
        ?.let { Instant.fromEpochSeconds(it) }
    val fallbackWakeup = today.atStartOfDayIn(timeZone) + 6.hours
    val wakeCandidate = wakeupInstant ?: fallbackWakeup
    val dayStartInstant = today.atStartOfDayIn(timeZone)
    var startInstant = wakeCandidate
    if (startInstant > nowInstant) startInstant = nowInstant
    if (startInstant < dayStartInstant) startInstant = dayStartInstant
    startInstant = roundToNearestHour(startInstant, timeZone)

    // Sample once per hour from wakeup to "now", plus an initial point at wakeup and a final point at current time.
    val labels = mutableListOf<String>()
    val values = mutableListOf<Float>()
    val sampleTimes = generateSequence(startInstant) { it + 1.hours }
        .takeWhile { it < nowInstant }
        .toMutableList()
    sampleTimes += nowInstant

    sampleTimes.forEach { instant ->
        val label = formatTimeLabel(instant, timeZone)
        val steps = healthDao.getTotalStepsExclusiveEnd(todayStart, instant.epochSeconds) ?: 0L
        labels.add(label)
        values.add(steps.toFloat())
    }

    val todayEnd = today.plus(DatePeriod(days = 1)).atStartOfDayIn(timeZone).epochSeconds
    val total = healthDao.getTotalStepsExclusiveEnd(todayStart, todayEnd) ?: 0L
    return Triple(labels, values, total)
}

private suspend fun fetchWeeklyStepsData(
    healthDao: HealthDao,
    today: LocalDate,
    timeZone: TimeZone
): Triple<List<String>, List<Float>, Long> {
    val labels = mutableListOf<String>()
    val values = mutableListOf<Float>()
    var total = 0L

    // Get the most recent Sunday (start of current/most recent week)
    val weekStartSunday = getPreviousSunday(today)

    repeat(7) { offset ->
        val day = weekStartSunday.plus(DatePeriod(days = offset))
        labels.add(day.dayOfWeek.name.take(3))

        val start = day.atStartOfDayIn(timeZone).epochSeconds
        val end = day.plus(DatePeriod(days = 1)).atStartOfDayIn(timeZone).epochSeconds
        val steps = healthDao.getTotalStepsExclusiveEnd(start, end) ?: 0L
        values.add(steps.toFloat())
        total += steps
    }
    return Triple(labels, values, total)
}

private suspend fun fetchMonthlyStepsData(
    healthDao: HealthDao,
    today: LocalDate,
    timeZone: TimeZone
): Triple<List<String>, List<Float>, Long> {
    val labels = mutableListOf<String>()
    val values = mutableListOf<Float>()
    var total = 0L

    // Get the first day of the current month
    val monthStart = LocalDate(today.year, today.month, 1)

    // Get the last Sunday on or before the first day of the month
    val firstWeekStart = getPreviousSunday(monthStart)

    // Calculate how many weeks we need to cover the entire month
    val daysInMonth = getDaysInMonth(today.month, today.year)
    val monthEnd = monthStart.plus(DatePeriod(days = daysInMonth - 1))

    // Calculate the number of weeks needed
    var daysFromFirstSunday = 0
    var currentDay = firstWeekStart
    while (currentDay <= monthEnd) {
        daysFromFirstSunday++
        currentDay = currentDay.plus(DatePeriod(days = 1))
    }
    val weeksNeeded = ((daysFromFirstSunday - 1) / 7) + 1

    // Process all weeks that cover the month
    repeat(weeksNeeded) { weekIndex ->
        val weekStart = firstWeekStart.plus(DatePeriod(days = weekIndex * 7))
        val weekEnd = weekStart.plus(DatePeriod(days = 6))

        // Create label for this week (e.g., "Mar 27 - Apr 4")
        val label = formatDateRangeLabel(weekStart, weekEnd)

        // Calculate total steps for this week
        var weekSteps = 0L
        var daysWithData = 0

        repeat(7) { dayOffset ->
            val day = weekStart.plus(DatePeriod(days = dayOffset))
            val start = day.atStartOfDayIn(timeZone).epochSeconds
            val end = day.plus(DatePeriod(days = 1)).atStartOfDayIn(timeZone).epochSeconds
            val steps = healthDao.getTotalStepsExclusiveEnd(start, end) ?: 0L

            if (steps > 0) {
                weekSteps += steps
                daysWithData++
            }
        }

        // Only add the week if there's data
        if (daysWithData > 0) {
            labels.add(label)
            values.add(weekSteps.toFloat() / daysWithData)
            total += weekSteps
        }
    }
    return Triple(labels, values, total)
}

/**
 * Fetches heart rate data for the given time range.
 * @return Triple of (labels, values, average)
 */
suspend fun fetchHeartRateData(
    healthDao: HealthDao,
    timeRange: HealthTimeRange
): Triple<List<String>, List<Float>, Int> {
    val timeZone = TimeZone.currentSystemDefault()
    val today = Clock.System.now().toLocalDateTime(timeZone).date

    return when (timeRange) {
        HealthTimeRange.Daily -> {
            val labels = (0..23).map { hour -> String.format("%02d:00", hour) }
            val values = List(24) { 0f }
            Triple(labels, values, 0)
        }

        HealthTimeRange.Weekly -> fetchWeeklyHeartRateData(healthDao, today, timeZone)
        HealthTimeRange.Monthly -> fetchMonthlyHeartRateData(healthDao, today, timeZone)
    }
}

private suspend fun fetchWeeklyHeartRateData(
    healthDao: HealthDao,
    today: LocalDate,
    timeZone: TimeZone
): Triple<List<String>, List<Float>, Int> {
    val labels = mutableListOf<String>()
    val values = mutableListOf<Float>()
    var count = 0
    var sum = 0

    // Get the most recent Sunday (start of current/most recent week)
    val weekStartSunday = getPreviousSunday(today)

    repeat(7) { offset ->
        val day = weekStartSunday.plus(DatePeriod(days = offset))
        labels.add(day.dayOfWeek.name.take(3))

        val start = day.atStartOfDayIn(timeZone).epochSeconds
        val end = day.plus(DatePeriod(days = 1)).atStartOfDayIn(timeZone).epochSeconds

        // Get average HR for this day from health_data table
        val avgHR = healthDao.getAverageSteps(start, end)?.toInt() ?: 0
        values.add(avgHR.toFloat())
        if (avgHR > 0) {
            sum += avgHR
            count++
        }
    }
    val avg = if (count > 0) sum / count else 0
    return Triple(labels, values, avg)
}

private suspend fun fetchMonthlyHeartRateData(
    healthDao: HealthDao,
    today: LocalDate,
    timeZone: TimeZone
): Triple<List<String>, List<Float>, Int> {
    val labels = mutableListOf<String>()
    val values = mutableListOf<Float>()
    var totalCount = 0
    var totalSum = 0

    // Get the first day of the current month
    val monthStart = LocalDate(today.year, today.month, 1)

    // Get the last Sunday on or before the first day of the month
    val firstWeekStart = getPreviousSunday(monthStart)

    // Calculate how many weeks we need to cover the entire month
    val daysInMonth = getDaysInMonth(today.month, today.year)
    val monthEnd = monthStart.plus(DatePeriod(days = daysInMonth - 1))

    // Calculate the number of weeks needed
    var daysFromFirstSunday = 0
    var currentDay = firstWeekStart
    while (currentDay <= monthEnd) {
        daysFromFirstSunday++
        currentDay = currentDay.plus(DatePeriod(days = 1))
    }
    val weeksNeeded = ((daysFromFirstSunday - 1) / 7) + 1

    // Process all weeks that cover the month
    repeat(weeksNeeded) { weekIndex ->
        val weekStart = firstWeekStart.plus(DatePeriod(days = weekIndex * 7))
        val weekEnd = weekStart.plus(DatePeriod(days = 6))

        // Create label for this week
        val label = formatDateRangeLabel(weekStart, weekEnd)

        // Calculate average HR for this week
        var weekSum = 0
        var weekCount = 0

        repeat(7) { dayOffset ->
            val day = weekStart.plus(DatePeriod(days = dayOffset))
            val start = day.atStartOfDayIn(timeZone).epochSeconds
            val end = day.plus(DatePeriod(days = 1)).atStartOfDayIn(timeZone).epochSeconds
            val avgHR = healthDao.getAverageSteps(start, end)?.toInt() ?: 0

            if (avgHR > 0) {
                weekSum += avgHR
                weekCount++
            }
        }

        // Only add the week if there's data
        if (weekCount > 0) {
            labels.add(label)
            values.add((weekSum / weekCount).toFloat())
            totalSum += weekSum
            totalCount += weekCount
        }
    }
    val avg = if (totalCount > 0) totalSum / totalCount else 0
    return Triple(labels, values, avg)
}

/**
 * Fetches daily sleep data.
 * @return Pair of (DailySleepData, averageHours)
 */
suspend fun fetchDailySleepData(
    healthDao: HealthDao
): Pair<DailySleepData?, Float> {
    val timeZone = TimeZone.currentSystemDefault()
    val today = Clock.System.now().toLocalDateTime(timeZone).date

    // Search from 6 PM yesterday to 2 PM today
    val searchStart = today.minus(DatePeriod(days = 1)).atStartOfDayIn(timeZone).epochSeconds + (18 * 3600)
    val searchEnd = today.atStartOfDayIn(timeZone).epochSeconds + (14 * 3600)

    val sleepTypes = listOf(OverlayType.Sleep.value, OverlayType.DeepSleep.value)
    val sleepEntries = healthDao.getOverlayEntries(searchStart, searchEnd, sleepTypes)
        .sortedBy { it.startTime }

    if (sleepEntries.isEmpty()) {
        return Pair(null, 0f)
    }

    val segments = mutableListOf<SleepSegment>()
    var bedtime = Float.MAX_VALUE
    var wakeTime = 0f
    var totalSleepSeconds = 0L

    sleepEntries.forEach { entry ->
        val type = OverlayType.fromValue(entry.type)
        val startHour = ((entry.startTime - searchStart) / 3600f) + 18f // Offset to 6 PM = hour 18
        val durationHours = entry.duration / 3600f

        segments.add(SleepSegment(startHour, durationHours, type ?: OverlayType.Sleep))
        bedtime = minOf(bedtime, startHour)
        wakeTime = maxOf(wakeTime, startHour + durationHours)

        if (type == OverlayType.Sleep || type == OverlayType.DeepSleep) {
            totalSleepSeconds += entry.duration
        }
    }

    val totalSleepHours = totalSleepSeconds / 3600f
    val dailyData = DailySleepData(segments, bedtime, wakeTime, totalSleepHours)

    return Pair(dailyData, totalSleepHours)
}

/**
 * Fetches stacked sleep data for weekly or monthly view.
 * @return Pair of (List<StackedSleepData>, averageHours)
 */
suspend fun fetchStackedSleepData(
    healthDao: HealthDao,
    timeRange: HealthTimeRange
): Pair<List<StackedSleepData>, Float> {
    val timeZone = TimeZone.currentSystemDefault()
    val today = Clock.System.now().toLocalDateTime(timeZone).date

    return when (timeRange) {
        HealthTimeRange.Weekly -> fetchWeeklySleepData(healthDao, today, timeZone)
        HealthTimeRange.Monthly -> fetchMonthlySleepData(healthDao, today, timeZone)
        else -> Pair(emptyList(), 0f)
    }
}

private suspend fun fetchWeeklySleepData(
    healthDao: HealthDao,
    today: LocalDate,
    timeZone: TimeZone
): Pair<List<StackedSleepData>, Float> {
    val stackedData = mutableListOf<StackedSleepData>()
    var totalHours = 0f

    // Get the most recent Sunday (start of current/most recent week)
    val weekStartSunday = getPreviousSunday(today)

    repeat(7) { offset ->
        val day = weekStartSunday.plus(DatePeriod(days = offset))
        val label = day.dayOfWeek.name.take(3)

        val searchStart = day.minus(DatePeriod(days = 1)).atStartOfDayIn(timeZone).epochSeconds + (18 * 3600)
        val searchEnd = day.atStartOfDayIn(timeZone).epochSeconds + (14 * 3600)

        val sleepTypes = listOf(OverlayType.Sleep.value, OverlayType.DeepSleep.value)
        val sleepEntries = healthDao.getOverlayEntries(searchStart, searchEnd, sleepTypes)

        val lightSleepSeconds = sleepEntries.filter { OverlayType.fromValue(it.type) == OverlayType.Sleep }
            .sumOf { it.duration }
        val deepSleepSeconds = sleepEntries.filter { OverlayType.fromValue(it.type) == OverlayType.DeepSleep }
            .sumOf { it.duration }

        val lightSleepHours = lightSleepSeconds / 3600f
        val deepSleepHours = deepSleepSeconds / 3600f

        stackedData.add(StackedSleepData(label, lightSleepHours, deepSleepHours))
        totalHours += (lightSleepHours + deepSleepHours)
    }

    // If there's no sleep data at all, return empty list
    if (totalHours == 0f) {
        return Pair(emptyList(), 0f)
    }

    val avg = totalHours / 7f
    return Pair(stackedData, avg)
}

private suspend fun fetchMonthlySleepData(
    healthDao: HealthDao,
    today: LocalDate,
    timeZone: TimeZone
): Pair<List<StackedSleepData>, Float> {
    val stackedData = mutableListOf<StackedSleepData>()
    var totalHours = 0f

    // Get the first day of the current month
    val monthStart = LocalDate(today.year, today.month, 1)

    // Get the last Sunday on or before the first day of the month
    val firstWeekStart = getPreviousSunday(monthStart)

    // Calculate how many weeks we need to cover the entire month
    val daysInMonth = getDaysInMonth(today.month, today.year)
    val monthEnd = monthStart.plus(DatePeriod(days = daysInMonth - 1))

    // Calculate the number of weeks needed
    var daysFromFirstSunday = 0
    var currentDay = firstWeekStart
    while (currentDay <= monthEnd) {
        daysFromFirstSunday++
        currentDay = currentDay.plus(DatePeriod(days = 1))
    }
    val weeksNeeded = ((daysFromFirstSunday - 1) / 7) + 1

    // Process all weeks that cover the month
    repeat(weeksNeeded) { weekIndex ->
        val weekStart = firstWeekStart.plus(DatePeriod(days = weekIndex * 7))
        val weekEnd = weekStart.plus(DatePeriod(days = 6))

        // Create label for this week
        val label = formatDateRangeLabel(weekStart, weekEnd)

        // Calculate total sleep for this week
        var weekLightSleepSeconds = 0L
        var weekDeepSleepSeconds = 0L

        repeat(7) { dayOffset ->
            val day = weekStart.plus(DatePeriod(days = dayOffset))
            val searchStart = day.minus(DatePeriod(days = 1)).atStartOfDayIn(timeZone).epochSeconds + (18 * 3600)
            val searchEnd = day.atStartOfDayIn(timeZone).epochSeconds + (14 * 3600)

            val sleepTypes = listOf(OverlayType.Sleep.value, OverlayType.DeepSleep.value)
            val sleepEntries = healthDao.getOverlayEntries(searchStart, searchEnd, sleepTypes)

            weekLightSleepSeconds += sleepEntries.filter { OverlayType.fromValue(it.type) == OverlayType.Sleep }
                .sumOf { it.duration }
            weekDeepSleepSeconds += sleepEntries.filter { OverlayType.fromValue(it.type) == OverlayType.DeepSleep }
                .sumOf { it.duration }
        }

        val lightSleepHours = weekLightSleepSeconds / 3600f
        val deepSleepHours = weekDeepSleepSeconds / 3600f

        // Only add the week if there's sleep data
        if (lightSleepHours > 0 || deepSleepHours > 0) {
            stackedData.add(StackedSleepData(label, lightSleepHours / 7f, deepSleepHours / 7f))
            totalHours += (lightSleepHours + deepSleepHours)
        }
    }

    // If there's no sleep data at all, return empty list
    if (totalHours == 0f) {
        return Pair(emptyList(), 0f)
    }

    val avg = if (daysInMonth > 0) totalHours / daysInMonth else 0f
    return Pair(stackedData, avg)
}
