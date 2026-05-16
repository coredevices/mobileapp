package io.rebble.libpebblecommon.services

import io.rebble.libpebblecommon.database.entity.HealthDataEntity
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertTrue

class HealthStatsSyncTest {
    @Test
    fun buildWeekdayTypicalsFromData_emptyInput_returnsEmptyMap() {
        val result = buildWeekdayTypicalsFromData(emptyList(), TimeZone.UTC)
        assertTrue(result.isEmpty(), "empty input should produce empty map, got keys=${result.keys}")
    }
}

private fun row(timestamp: Long, steps: Int) = HealthDataEntity(
    timestamp = timestamp,
    steps = steps,
    orientation = 0,
    intensity = 0,
    lightIntensity = 0,
    activeMinutes = 0,
    restingGramCalories = 0,
    activeGramCalories = 0,
    distanceCm = 0,
)
