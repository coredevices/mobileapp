package io.rebble.libpebblecommon.health

import io.rebble.libpebblecommon.structmapper.SUByte
import io.rebble.libpebblecommon.structmapper.SUInt
import io.rebble.libpebblecommon.structmapper.SUShort
import io.rebble.libpebblecommon.structmapper.StructMappable
import io.rebble.libpebblecommon.util.Endian

enum class OverlayType(val value: Int) {
    Sleep(1),
    DeepSleep(2),
    Nap(3),
    DeepNap(4),
    Walk(5),
    Run(6);

    companion object {
        fun fromValue(value: Int): OverlayType? = values().firstOrNull { it.value == value }
    }
}

data class StepsRecord(
    val version: UShort,
    val timestamp: UInt,
    val steps: UByte,
    val orientation: UByte,
    val intensity: UShort,
    val lightIntensity: UByte,
    val flags: UByte,
    val restingGramCalories: UShort,
    val activeGramCalories: UShort,
    val distanceCm: UShort,
    val heartRate: UByte = 0u,
    val heartRateWeight: UShort = 0u,
    val heartRateZone: UByte = 0u
)

data class OverlayRecord(
    val version: UShort,
    val type: OverlayType,
    val offsetUTC: UInt,
    val startTime: UInt,
    val duration: UInt,
    val steps: UShort,
    val restingKiloCalories: UShort,
    val activeKiloCalories: UShort,
    val distanceCm: UShort
)

class RawStepsRecord : StructMappable() {
    val steps = SUByte(m)
    val orientation = SUByte(m)
    val intensity = SUShort(m, endianness = Endian.Little)
    val lightIntensity = SUByte(m)
    val flags = SUByte(m)
    val restingGramCalories = SUShort(m, endianness = Endian.Little)
    val activeGramCalories = SUShort(m, endianness = Endian.Little)
    val distanceCm = SUShort(m, endianness = Endian.Little)
    val heartRate = SUByte(m)
    val heartRateWeight = SUShort(m, endianness = Endian.Little)
    val heartRateZone = SUByte(m)
}

class RawOverlayRecord : StructMappable() {
    val version = SUShort(m, endianness = Endian.Little)
    val unused = SUShort(m, endianness = Endian.Little)
    val type = SUShort(m, endianness = Endian.Little)
    val offsetUTC = SUInt(m, endianness = Endian.Little)
    val startTime = SUInt(m, endianness = Endian.Little)
    val duration = SUInt(m, endianness = Endian.Little)
    val steps = SUShort(m, endianness = Endian.Little)
    val restingKiloCalories = SUShort(m, endianness = Endian.Little)
    val activeKiloCalories = SUShort(m, endianness = Endian.Little)
    val distanceCm = SUShort(m, endianness = Endian.Little)
}

/**
 * Debug statistics for health data, used for diagnostics and testing.
 */
data class HealthDebugStats(
    val totalSteps30Days: Long,
    val averageStepsPerDay: Int,
    val totalSleepSeconds30Days: Long,
    val averageSleepSecondsPerDay: Int,
    val todaySteps: Long,
    val lastNightSleepHours: Float?,
    val latestDataTimestamp: Long?,
    val daysOfData: Int
)
