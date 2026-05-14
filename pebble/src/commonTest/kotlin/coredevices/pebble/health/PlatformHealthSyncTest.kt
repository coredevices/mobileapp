package coredevices.pebble.health

import io.rebble.libpebblecommon.database.entity.OverlayDataEntity
import io.rebble.libpebblecommon.health.OverlayType
import kotlin.test.Test
import kotlin.test.assertEquals

class PlatformHealthSyncTest {

    @Test
    fun sleepContainerWithDeepSubintervals_emitsAlternatingLightAndDeep() {
        // 8h Sleep container with two DeepSleep periods nested inside (the reporter scenario).
        val intervals = computeSleepStageIntervals(
            listOf(
                overlay(start = 0, duration = 28800, type = OverlayType.Sleep),
                overlay(start = 3600, duration = 1800, type = OverlayType.DeepSleep),
                overlay(start = 14400, duration = 3600, type = OverlayType.DeepSleep),
            )
        )
        assertEquals(
            listOf(
                light(0, 3600),
                deep(3600, 5400),
                light(5400, 14400),
                deep(14400, 18000),
                light(18000, 28800),
            ),
            intervals,
        )
    }

    @Test
    fun sleepContainerWithNoDeeps_emitsSingleLight() {
        val intervals = computeSleepStageIntervals(
            listOf(overlay(start = 100, duration = 3600, type = OverlayType.Sleep))
        )
        assertEquals(listOf(light(100, 3700)), intervals)
    }

    @Test
    fun deepFlushWithContainerStart_noEmptyLeadingLight() {
        val intervals = computeSleepStageIntervals(
            listOf(
                overlay(start = 0, duration = 3600, type = OverlayType.Sleep),
                overlay(start = 0, duration = 600, type = OverlayType.DeepSleep),
            )
        )
        assertEquals(listOf(deep(0, 600), light(600, 3600)), intervals)
    }

    @Test
    fun deepFlushWithContainerEnd_noEmptyTrailingLight() {
        val intervals = computeSleepStageIntervals(
            listOf(
                overlay(start = 0, duration = 3600, type = OverlayType.Sleep),
                overlay(start = 3000, duration = 600, type = OverlayType.DeepSleep),
            )
        )
        assertEquals(listOf(light(0, 3000), deep(3000, 3600)), intervals)
    }

    @Test
    fun splitSleep_twoContainers_eachCarvesItsOwnDeep() {
        val intervals = computeSleepStageIntervals(
            listOf(
                overlay(start = 0, duration = 3600, type = OverlayType.Sleep),
                overlay(start = 1000, duration = 500, type = OverlayType.DeepSleep),
                overlay(start = 7200, duration = 3600, type = OverlayType.Sleep),
                overlay(start = 8000, duration = 600, type = OverlayType.DeepSleep),
            )
        )
        assertEquals(
            listOf(
                light(0, 1000),
                deep(1000, 1500),
                light(1500, 3600),
                light(7200, 8000),
                deep(8000, 8600),
                light(8600, 10800),
            ),
            intervals,
        )
    }

    @Test
    fun napContainerWithDeepNap_carvesOutDeep() {
        val intervals = computeSleepStageIntervals(
            listOf(
                overlay(start = 0, duration = 1800, type = OverlayType.Nap),
                overlay(start = 600, duration = 300, type = OverlayType.DeepNap),
            )
        )
        assertEquals(
            listOf(light(0, 600), deep(600, 900), light(900, 1800)),
            intervals,
        )
    }
}

private fun overlay(start: Long, duration: Long, type: OverlayType) = OverlayDataEntity(
    startTime = start,
    duration = duration,
    type = type.value,
    steps = 0,
    restingKiloCalories = 0,
    activeKiloCalories = 0,
    distanceCm = 0,
    offsetUTC = 0,
)

private fun light(start: Long, end: Long) = SleepStageInterval(start, end, isDeep = false)
private fun deep(start: Long, end: Long) = SleepStageInterval(start, end, isDeep = true)
