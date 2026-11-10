package coredevices.ring.bugreport

import coredevices.analytics.HeartbeatMetric
import coredevices.analytics.HeartbeatMetricsSource
import coredevices.ring.data.entity.room.RingRssiReading
import coredevices.ring.database.room.dao.RingRssiReadingDao
import coredevices.util.sha256
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * Descriptive statistics over persisted ring Rx RSSI readings for the bug report.
 *
 * "Asymmetry" is `phone RSSI - ring Rx RSSI` in dB. A large positive value means the ring hears
 * the phone much worse than the phone hears the ring, which is the signature of a deaf ring.
 */
class RingRssiStatistics(
    private val dao: RingRssiReadingDao,
    private val clock: Clock = Clock.System,
) : HeartbeatMetricsSource {
    companion object {
        /** Readings this close to the extreme asymmetry count as part of that tail. */
        const val TAIL_WIDTH_DB = 6f

        /** First 8 hex chars of the SHA-256 of the serial; enough to tell rings apart without storing the serial. */
        @OptIn(ExperimentalStdlibApi::class)
        fun ringSerialHash(serial: String): String = sha256(serial.encodeToByteArray()).toHexString().take(8)
        val WINDOWS: List<Pair<String, Duration?>> = listOf(
            "Last 3h" to 3.hours,
            "Last 24h" to 24.hours,
            "Last 7d" to 7.days,
            "All time" to null,
        )
    }

    data class WindowStats(
        val label: String,
        val sampleCount: Int,
        val latest: RingRssiReading?,
        /** Sample at the maximum phone RSSI, i.e. the best case the ring has been given. */
        val maxPhoneRssi: RingRssiReading?,
        /** Sample at the maximum asymmetry (worst). */
        val maxAsymmetry: RingRssiReading?,
        /** Sample at the minimum asymmetry (best). */
        val minAsymmetry: RingRssiReading?,
        /** Sample at the lower median of asymmetry. */
        val medianAsymmetry: RingRssiReading?,
        /** Proportion of samples within [TAIL_WIDTH_DB] of the maximum asymmetry, 0..1. */
        val upperTailProportion: Float?,
        /** Proportion of samples within [TAIL_WIDTH_DB] of the minimum asymmetry, 0..1. */
        val lowerTailProportion: Float?,
    )

    /** Persists a reading for [ringSerial] and drops readings from any other ring. */
    suspend fun record(ringSerial: String, ringRxRssi: Float, phoneRssi: Float?, timestamp: Instant = clock.now()) {
        val hash = ringSerialHash(ringSerial)
        dao.deleteOtherRings(hash)
        dao.insert(RingRssiReading.of(timestamp, hash, ringRxRssi, phoneRssi))
    }

    suspend fun windowStats(label: String, window: Duration?): WindowStats =
        statsSince(label, window?.let { clock.now() - it } ?: Instant.DISTANT_PAST)

    suspend fun statsSince(label: String, since: Instant): WindowStats {
        val asymmetryCount = dao.asymmetryCount(since)
        val max = dao.maxAsymmetry(since)
        val min = dao.minAsymmetry(since)
        return WindowStats(
            label = label,
            sampleCount = dao.count(since),
            latest = dao.latest(since),
            maxPhoneRssi = dao.maxPhoneRssi(since),
            maxAsymmetry = max,
            minAsymmetry = min,
            medianAsymmetry = if (asymmetryCount == 0) null else dao.asymmetryAtRank(since, (asymmetryCount - 1) / 2),
            upperTailProportion = max?.asymmetryDb?.let {
                dao.countAsymmetryAtLeast(since, it - TAIL_WIDTH_DB).toFloat() / asymmetryCount
            },
            lowerTailProportion = min?.asymmetryDb?.let {
                dao.countAsymmetryAtMost(since, it + TAIL_WIDTH_DB).toFloat() / asymmetryCount
            },
        )
    }

    override suspend fun heartbeatMetrics(since: Instant, now: Instant): List<HeartbeatMetric> =
        statsSince("heartbeat", since).toHeartbeatMetrics()

    suspend fun summary(): String {
        val windows = WINDOWS.map { (label, window) -> windowStats(label, window) }
        fun row(name: String, cell: (WindowStats) -> String) = "$name: " + windows.joinToString(" / ") { cell(it) }
        return buildString {
            appendLine()
            appendLine("Ring Rx RSSI statistics")
            appendLine("Legend: windows ${WINDOWS.joinToString(" / ") { it.first }}")
            appendLine("sample = ring Rx dBm, phone dBm, asymmetry dB @ UTC")
            appendLine("asymmetry = phone - ring Rx (higher is worse); tails = share of samples within ${TAIL_WIDTH_DB.format()} dB of that asymmetry extreme")
            appendLine("Latest sample: ${windows.last().latest.describe()}")
            appendLine(row("n") { it.sampleCount.toString() })
            appendLine(row("Max phone RSSI") { it.maxPhoneRssi.describe() })
            appendLine(row("Max asymmetry (worst)") { it.maxAsymmetry.describe() })
            appendLine(row("Min asymmetry (best)") { it.minAsymmetry.describe() })
            appendLine(row("Median asymmetry") { it.medianAsymmetry.describe() })
            appendLine(row("Upper tail") { it.upperTailProportion.percent() })
            appendLine(row("Lower tail") { it.lowerTailProportion.percent() })
        }
    }

    private fun RingRssiReading?.describe(): String {
        if (this == null) return "n/a"
        val phone = phoneRssi?.format() ?: "n/a"
        val asym = asymmetryDb?.format(signed = true) ?: "n/a"
        return "${ringRxRssi.format()}, $phone, $asym @ $timestamp"
    }

    private fun Float?.percent(): String = this?.let { "${(it * 100).roundToInt()}%" } ?: "n/a"

    private fun Float.format(signed: Boolean = false): String {
        val tenths = (this * 10).roundToInt()
        val sign = if (tenths < 0) "-" else if (signed) "+" else ""
        val magnitude = abs(tenths)
        return "$sign${magnitude / 10}.${magnitude % 10}"
    }
}

private const val METRIC_PREFIX = "ring.rssi."

/** Scalar metrics for the analytics heartbeat; nulls are omitted rather than sent as placeholders. */
fun RingRssiStatistics.WindowStats.toHeartbeatMetrics(): List<HeartbeatMetric> = buildList {
    add(HeartbeatMetric("${METRIC_PREFIX}sample_count", sampleCount))
    if (sampleCount == 0) return@buildList
    fun metric(name: String, value: Any?) {
        value?.let { add(HeartbeatMetric(METRIC_PREFIX + name, it)) }
    }
    metric("latest_ring_rx_dbm", latest?.ringRxRssi)
    metric("latest_phone_dbm", latest?.phoneRssi)
    metric("latest_asymmetry_db", latest?.asymmetryDb)
    metric("max_phone_dbm", maxPhoneRssi?.phoneRssi)
    metric("max_phone_ring_rx_dbm", maxPhoneRssi?.ringRxRssi)
    metric("asymmetry_max_db", maxAsymmetry?.asymmetryDb)
    metric("asymmetry_min_db", minAsymmetry?.asymmetryDb)
    metric("asymmetry_median_db", medianAsymmetry?.asymmetryDb)
    metric("upper_tail_percent", upperTailProportion?.let { it * 100 })
    metric("lower_tail_percent", lowerTailProportion?.let { it * 100 })
}

