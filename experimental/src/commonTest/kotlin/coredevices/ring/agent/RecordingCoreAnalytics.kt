package coredevices.ring.agent

import coredevices.analytics.CoreAnalytics
import kotlin.time.Duration
import kotlin.time.Instant

/** Records heartbeat counter increments; everything else is a no-op. */
internal class RecordingCoreAnalytics : CoreAnalytics {
    val counters = mutableMapOf<String, Int>()

    override fun logEvent(name: String, parameters: Map<String, Any>?) {}
    override suspend fun logHeartbeatState(name: String, value: Boolean, timestamp: Instant) {}
    override suspend fun processHeartbeat() {}
    override fun updateLastConnectedSerial(serial: String?) {}
    override fun updateRingTransferDurationMetric(duration: Duration) {}
    override fun updateRingLifetimeCollectionCount(serial: String, count: Int) {}
    override fun updateRingBatteryVoltage(voltageMilliV: Int) {}
    override fun incrementHeartbeatCounter(name: String) {
        counters[name] = (counters[name] ?: 0) + 1
    }
}
