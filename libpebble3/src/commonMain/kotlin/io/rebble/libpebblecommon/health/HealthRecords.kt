package io.rebble.libpebblecommon.health

import io.rebble.libpebblecommon.structmapper.SUByte
import io.rebble.libpebblecommon.structmapper.SUInt
import io.rebble.libpebblecommon.structmapper.SUShort
import io.rebble.libpebblecommon.structmapper.StructMappable
import io.rebble.libpebblecommon.util.Endian

public enum class OverlayType(public val value: Int) {
    Sleep(1),
    DeepSleep(2),
    Nap(3),
    DeepNap(4),
    Walk(5),
    Run(6),
    OpenWorkout(7);

    public companion object {
        public fun fromValue(value: Int): OverlayType? = values().firstOrNull { it.value == value }
    }
}

internal data class StepsRecord(
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

internal data class OverlayRecord(
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

internal class RawStepsRecord : StructMappable() {
    val steps: SUByte = SUByte(m)
    val orientation: SUByte = SUByte(m)
    val intensity: SUShort = SUShort(m, endianness = Endian.Little)
    val lightIntensity: SUByte = SUByte(m)
    val flags: SUByte = SUByte(m)
    val restingGramCalories: SUShort = SUShort(m, endianness = Endian.Little)
    val activeGramCalories: SUShort = SUShort(m, endianness = Endian.Little)
    val distanceCm: SUShort = SUShort(m, endianness = Endian.Little)
    val heartRate: SUByte = SUByte(m)
    val heartRateWeight: SUShort = SUShort(m, endianness = Endian.Little)
    val heartRateZone: SUByte = SUByte(m)
}

internal class RawOverlayRecord : StructMappable() {
    val version: SUShort = SUShort(m, endianness = Endian.Little)
    val unused: SUShort = SUShort(m, endianness = Endian.Little)
    val type: SUShort = SUShort(m, endianness = Endian.Little)
    val offsetUTC: SUInt = SUInt(m, endianness = Endian.Little)
    val startTime: SUInt = SUInt(m, endianness = Endian.Little)
    val duration: SUInt = SUInt(m, endianness = Endian.Little)
    val steps: SUShort = SUShort(m, endianness = Endian.Little)
    val restingKiloCalories: SUShort = SUShort(m, endianness = Endian.Little)
    val activeKiloCalories: SUShort = SUShort(m, endianness = Endian.Little)
    val distanceCm: SUShort = SUShort(m, endianness = Endian.Little)
}

/**
 * Debug statistics for health data, used for diagnostics and testing.
 *
 * [weekdayTypicalSteps] carries the per-weekday typical-step totals computed for the
 * `<weekday>_steps` BlobDB rows; absent keys are weekdays where the user has insufficient
 * same-weekday history (we don't write a row for them, so the watch shows no comparison).
 *
 * [weekdayTypicalSleep] carries the four typical sleep values per weekday written into the
 * tail of each `<weekday>_sleepData` BlobDB row; absent keys are weekdays below the same
 * minimum-history threshold.
 */
public data class HealthDebugStats internal constructor(
    val totalSteps30Days: Long,
    val averageStepsPerDay: Int,
    val totalSleepSeconds30Days: Long,
    val averageSleepSecondsPerDay: Int,
    val todaySteps: Long,
    val lastNightSleepHours: Float?,
    val latestDataTimestamp: Long?,
    val daysOfData: Int,
    val weekdayTypicalSteps: Map<kotlinx.datetime.DayOfWeek, Int> = emptyMap(),
    val weekdayTypicalSleep: Map<kotlinx.datetime.DayOfWeek, io.rebble.libpebblecommon.services.WeekdaySleepTypicals> = emptyMap(),
)
