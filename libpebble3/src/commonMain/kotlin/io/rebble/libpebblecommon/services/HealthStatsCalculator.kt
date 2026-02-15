package io.rebble.libpebblecommon.services

import io.rebble.libpebblecommon.database.dao.HealthDao
import io.rebble.libpebblecommon.database.entity.OverlayDataEntity
import io.rebble.libpebblecommon.health.HealthConstants
import io.rebble.libpebblecommon.health.OverlayType
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

internal data class CalculatedHealthAverages(
    val totalSteps: Long,
    val totalSleepSeconds: Long,
    val averageStepsPerDay: Int,
    val averageSleepSecondsPerDay: Int,
    val stepDaysWithData: Int,
    val sleepDaysWithData: Int,
    val rangeDays: Int,
)

internal suspend fun calculateHealthAverages(
    healthDao: HealthDao,
    startDate: LocalDate,
    endDateExclusive: LocalDate,
    timeZone: TimeZone,
): CalculatedHealthAverages {
    val startEpoch = startDate.atStartOfDayIn(timeZone).epochSeconds
    val endEpoch = endDateExclusive.atStartOfDayIn(timeZone).epochSeconds

    val totalSteps = healthDao.getTotalStepsExclusiveEnd(startEpoch, endEpoch) ?: 0L
    val sleepOverlays = healthDao.getOverlayEntries(
        startEpoch,
        endEpoch,
        HealthConstants.SLEEP_TYPES
    )

    val mergedSleepIntervals = mergeIntervalsSeconds(sleepOverlays, timeZone, endEpoch)
    val totalSleepSeconds = mergedSleepIntervals.totalSeconds

    var stepDaysWithData = 0
    var day = startDate
    while (day < endDateExclusive) {
        val dayStart = day.atStartOfDayIn(timeZone).epochSeconds
        val dayEnd = day.plus(DatePeriod(days = 1)).atStartOfDayIn(timeZone).epochSeconds

        if (healthDao.getTotalStepsExclusiveEnd(dayStart, dayEnd) != null) {
            stepDaysWithData++
        }

        day = day.plus(DatePeriod(days = 1))
    }

    val sleepDaysWithData = mergedSleepIntervals.daysWithData.size

    val averageStepsPerDay = if (stepDaysWithData > 0) {
        (totalSteps / stepDaysWithData).toInt()
    } else {
        0
    }
    val averageSleepSecondsPerDay = if (sleepDaysWithData > 0) {
        (totalSleepSeconds / sleepDaysWithData).toInt()
    } else {
        0
    }

    val rangeDays = startDate.daysUntil(endDateExclusive).coerceAtLeast(0)

    return CalculatedHealthAverages(
        totalSteps = totalSteps,
        totalSleepSeconds = totalSleepSeconds,
        averageStepsPerDay = averageStepsPerDay,
        averageSleepSecondsPerDay = averageSleepSecondsPerDay,
        stepDaysWithData = stepDaysWithData,
        sleepDaysWithData = sleepDaysWithData,
        rangeDays = rangeDays,
    )
}

private data class MergedIntervals(
    val totalSeconds: Long,
    val daysWithData: Set<LocalDate>,
)

private fun mergeIntervalsSeconds(records: List<OverlayDataEntity>, timeZone: TimeZone, endLimitEpoch: Long): MergedIntervals {
    if (records.isEmpty()) return MergedIntervals(0, emptySet())

    val ranges = records
        .map { record ->
            val start = record.startTime
            val end = minOf(record.startTime + record.duration, endLimitEpoch)
            start to end
        }
        .filter { (start, end) -> end > start }
        .sortedBy { it.first }

    if (ranges.isEmpty()) return MergedIntervals(0, emptySet())

    var total = 0L
    var currentStart = ranges.first().first
    var currentEnd = ranges.first().second
    val days = mutableSetOf<LocalDate>()

    fun recordDays(start: Long, end: Long) {
        var cursor = start
        while (cursor < end) {
            val date = Instant.fromEpochSeconds(cursor).toLocalDateTime(timeZone).date
            days.add(date)
            val nextDayStart = date.plus(DatePeriod(days = 1)).atStartOfDayIn(timeZone).epochSeconds
            cursor = if (nextDayStart <= cursor) {
                end
            } else {
                minOf(nextDayStart, end)
            }
        }
    }

    for (i in 1 until ranges.size) {
        val (start, end) = ranges[i]
        if (start <= currentEnd) {
            currentEnd = maxOf(currentEnd, end)
        } else {
            total += currentEnd - currentStart
            recordDays(currentStart, currentEnd)
            currentStart = start
            currentEnd = end
        }
    }

    total += currentEnd - currentStart
    recordDays(currentStart, currentEnd)

    return MergedIntervals(total, days)
}
