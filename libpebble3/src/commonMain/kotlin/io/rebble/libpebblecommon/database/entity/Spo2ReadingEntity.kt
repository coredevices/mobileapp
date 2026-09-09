package io.rebble.libpebblecommon.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Per-minute SpO2 (blood oxygen saturation) reading, parsed from the ActivityMinuteData
 * data-logging stream (tag 81) at record version 14+. Only samples where spo2_percent > 0
 * are persisted; the watch produces a real reading only ~once per measurement interval.
 *
 * Keyed/deduped by the minute timestamp (the sample's UTC) via INSERT OR REPLACE.
 */
@Entity(tableName = "spo2_readings")
data class Spo2ReadingEntity(
    @PrimaryKey
    val timestamp: Long,
    /** Blood oxygen saturation percent (0–100). Never persisted as 0. */
    val spo2Percent: Int,
    /** Reading quality, 0–7 (mirrors the firmware HeartRateQuality scale). */
    val quality: Int,
)
