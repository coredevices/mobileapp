package coredevices.analytics

import coredevices.database.HeartbeatStateEntity
import io.rebble.libpebblecommon.metadata.WatchHardwarePlatform
import kotlin.test.Test
import kotlin.test.assertEquals

class CoreAnalyticsTest {
    @Test
    fun testStates() {
        val name = heartbeatWatchConnectedName(WatchHardwarePlatform.CORE_OBELIX_DVT)
        val input = listOf(
            HeartbeatStateEntity(name = name, state = true, timestamp = 1000),
            HeartbeatStateEntity(name = name, state = false, timestamp = 2000),
            HeartbeatStateEntity(name = name, state = true, timestamp = 3000),
            HeartbeatStateEntity(name = name, state = false, timestamp = 4000),
            HeartbeatStateEntity(name = name, state = false, timestamp = 5000),
        )
        assertEquals(
            HeartbeatMetric(name = name, value = 2000L),
            processHeartbeatState(input, name)
        )
    }

    @Test
    fun testPercentages() {
        val input = listOf(
            HeartbeatMetric(name = heartbeatWatchConnectedName(WatchHardwarePlatform.CORE_OBELIX_DVT), value = 2000L),
            HeartbeatMetric(name = heartbeatWatchConnectGoalName(WatchHardwarePlatform.CORE_OBELIX_DVT), value = 4000L),
            HeartbeatMetric(name = heartbeatWatchConnectedName(WatchHardwarePlatform.CORE_ASTERIX), value = 0L),
            HeartbeatMetric(name = heartbeatWatchConnectGoalName(WatchHardwarePlatform.CORE_ASTERIX), value = 0L),
        )
        assertEquals(
            listOf(
                HeartbeatMetric(name = heartbeatWatchConnectPercentName(WatchHardwarePlatform.CORE_OBELIX_DVT), value = 50.0),
            ),
            deriveConnectedPercentMetrics(input)
        )
    }
}