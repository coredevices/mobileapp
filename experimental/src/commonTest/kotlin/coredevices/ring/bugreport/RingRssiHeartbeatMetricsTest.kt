package coredevices.ring.bugreport

import coredevices.analytics.HeartbeatMetric
import coredevices.ring.data.entity.room.RingRssiReading
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class RingRssiHeartbeatMetricsTest {
    private val t = Instant.fromEpochSeconds(1_000)
    private fun reading(ringRx: Float, phone: Float?) = RingRssiReading.of(t, "abcd1234", ringRx, phone)

    @Test
    fun emptyWindowReportsOnlyCount() {
        val stats = RingRssiStatistics.WindowStats("hb", 0, null, null, null, null, null, null, null)
        assertEquals(listOf(HeartbeatMetric("ring.rssi.sample_count", 0)), stats.toHeartbeatMetrics())
    }

    @Test
    fun mapsEverySampleToScalars() {
        val stats = RingRssiStatistics.WindowStats(
            label = "hb",
            sampleCount = 3,
            latest = reading(-92f, -61f),
            maxPhoneRssi = reading(-90f, -58f),
            maxAsymmetry = reading(-95f, -60f),
            minAsymmetry = reading(-80f, -70f),
            medianAsymmetry = reading(-88f, -66f),
            upperTailProportion = 0.5f,
            lowerTailProportion = 0.25f,
        )
        val expected = mapOf(
            "ring.rssi.sample_count" to 3,
            "ring.rssi.latest_ring_rx_dbm" to -92f,
            "ring.rssi.latest_phone_dbm" to -61f,
            "ring.rssi.latest_asymmetry_db" to 31f,
            "ring.rssi.max_phone_dbm" to -58f,
            "ring.rssi.max_phone_ring_rx_dbm" to -90f,
            "ring.rssi.asymmetry_max_db" to 35f,
            "ring.rssi.asymmetry_min_db" to 10f,
            "ring.rssi.asymmetry_median_db" to 22f,
            "ring.rssi.upper_tail_percent" to 50f,
            "ring.rssi.lower_tail_percent" to 25f,
        )
        assertEquals(expected, stats.toHeartbeatMetrics().associate { it.name to it.value })
    }

    @Test
    fun omitsMetricsWithoutPhoneRssi() {
        val stats = RingRssiStatistics.WindowStats(
            label = "hb",
            sampleCount = 1,
            latest = reading(-92f, null),
            maxPhoneRssi = null,
            maxAsymmetry = null,
            minAsymmetry = null,
            medianAsymmetry = null,
            upperTailProportion = null,
            lowerTailProportion = null,
        )
        assertEquals(
            setOf("ring.rssi.sample_count", "ring.rssi.latest_ring_rx_dbm"),
            stats.toHeartbeatMetrics().map { it.name }.toSet()
        )
    }
}
