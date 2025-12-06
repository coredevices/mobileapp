package io.rebble.libpebblecommon.database.dao

import co.touchlab.kermit.Logger
import io.rebble.libpebblecommon.database.entity.HealthDataEntity

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
            logger.i {
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
    logger.i { summary }
}
