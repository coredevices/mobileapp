package io.rebble.libpebblecommon.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "health_data")
data class HealthDataEntity(
    @PrimaryKey
    val timestamp: Long,
    val steps: Int,
    val orientation: Int,
    val intensity: Int,
    val lightIntensity: Int,
    val activeMinutes: Int,
    val restingGramCalories: Int,
    val activeGramCalories: Int,
    val distanceCm: Int,
    val heartRate: Int = 0,
    val heartRateZone: Int = 0,
    val heartRateWeight: Int = 0
)

@Entity(
    tableName = "overlay_data",
    primaryKeys = ["startTime", "type"]
)
data class OverlayDataEntity(
    val startTime: Long,
    val duration: Long,
    val type: Int,
    val steps: Int,
    val restingKiloCalories: Int,
    val activeKiloCalories: Int,
    val distanceCm: Int,
    val offsetUTC: Int
)
