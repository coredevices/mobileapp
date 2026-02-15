package io.rebble.libpebblecommon.database.dao

import co.touchlab.kermit.Logger
import io.rebble.libpebblecommon.database.entity.HealthDataEntity
import io.rebble.libpebblecommon.database.entity.OverlayDataEntity

private val logger = Logger.withTag("HealthDaoExt")

/**
 * Inserts health data with priority based on step count. If data already exists for a timestamp,
 * only replaces it if the new data has more steps.
 */
suspend fun HealthDao.insertHealthDataWithPriority(data: List<HealthDataEntity>) {
    var inserted = 0
    var skipped = 0
    var replaced = 0

    data.forEach { newData ->
        val existing = getDataAtTimestamp(newData.timestamp)
        if (existing == null) {
            insertHealthData(listOf(newData))
            inserted++
            logger.d {
                "Inserted new data at timestamp ${newData.timestamp}: ${newData.steps} steps"
            }
        } else if (newData.steps > existing.steps) {
            logger.d {
                "Replacing data at timestamp ${newData.timestamp}: ${existing.steps} steps -> ${newData.steps} steps (gained ${newData.steps - existing.steps} steps)"
            }
            insertHealthData(listOf(newData))
            replaced++
        } else if (newData.steps < existing.steps) {
            logger.d {
                "Skipping data at timestamp ${newData.timestamp}: existing ${existing.steps} steps > new ${newData.steps} steps"
            }
            skipped++
        } else {
            logger.d {
                "Skipping duplicate data at timestamp ${newData.timestamp}: both have ${newData.steps} steps"
            }
            skipped++
        }
    }

    val summary = buildString {
        append("Health data insert complete: ")
        if (inserted > 0) append("$inserted new, ")
        if (replaced > 0) append("$replaced replaced (higher steps), ")
        if (skipped > 0) append("$skipped skipped (lower/equal steps)")
    }
    logger.d { summary }
}

/**
 * Inserts overlay data (sleep, activities) while preventing duplicates.
 * An overlay entry is considered duplicate if it has the same startTime and type.
 * When duplicates exist, keeps the entry with the longer duration to prevent data loss.
 */
suspend fun HealthDao.insertOverlayDataWithDeduplication(data: List<OverlayDataEntity>) {
    if (data.isEmpty()) return

    var inserted = 0
    var skipped = 0
    var replaced = 0

    data.forEach { newData ->
        val existing = getOverlayAtStartTimeAndType(newData.startTime, newData.type)
        if (existing == null) {
            insertOverlayData(listOf(newData))
            inserted++
        } else if (newData.duration > existing.duration) {
            logger.d {
                "Replacing overlay at startTime ${newData.startTime}, type ${newData.type}: ${existing.duration}s -> ${newData.duration}s (gained ${newData.duration - existing.duration}s)"
            }
            insertOverlayData(listOf(newData))
            replaced++
        } else if (newData.duration < existing.duration) {
            logger.d {
                "Skipping overlay at startTime ${newData.startTime}, type ${newData.type}: existing ${existing.duration}s > new ${newData.duration}s"
            }
            skipped++
        } else {
            // Same duration - skip as duplicate
            skipped++
        }
    }

    val summary = buildString {
        append("Overlay data insert complete: ")
        if (inserted > 0) append("$inserted new, ")
        if (replaced > 0) append("$replaced replaced (longer duration), ")
        if (skipped > 0) append("$skipped skipped (shorter/equal duration)")
    }
    logger.d { summary }
}
