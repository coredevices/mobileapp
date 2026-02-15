package coredevices.analytics

import co.touchlab.kermit.Logger
import com.russhwolf.settings.Settings
import com.russhwolf.settings.set
import coredevices.database.HeartbeatStateDao
import coredevices.database.HeartbeatStateEntity
import coredevices.util.emailOrNull
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import io.rebble.libpebblecommon.metadata.WatchHardwarePlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

interface CoreAnalytics {
    fun logEvent(name: String, parameters: Map<String, Any>? = null)
    suspend fun logHeartbeatState(name: String, value: Boolean, timestamp: Instant)
    suspend fun processHeartbeat()
    fun updateLastConnectedSerial(serial: String?)
    fun updateRingTransferDurationMetric(duration: Duration)
    fun updateRingLifetimeCollectionCount(serial: String, count: Int)
}

expect fun createAnalyticsCache(): Settings

private const val KEY_RING_TRANSFER_DURATION_TOTAL_MS = "coreanalytics_ring_transfer_duration_ms"
private const val KEY_RING_TRANSFER_DURATION_START_TIMESTAMP = "coreanalytics_ring_transfer_start_timestamp"
private const val KEY_RING_LIFETIME_COLLECTION_COUNT = "coreanalytics_ring_lifetime_collection_count"
private const val KEY_RING_LIFETIME_COLLECTION_COUNT_SERIAL = "coreanalytics_ring_lifetime_collection_count_serial"

class RealCoreAnalytics(
    private val analyticsBackend: AnalyticsBackend,
    private val heartbeatStateDao: HeartbeatStateDao,
    private val clock: Clock,
) : CoreAnalytics {
    private val logger = Logger.withTag("RealCoreAnalytics")
    // We can't see libpebble from util right now, so it has to be this way around
    private val lastConnectedSerial = MutableStateFlow<String?>(null)
    private val cache = createAnalyticsCache()

    override fun logEvent(
        name: String,
        parameters: Map<String, Any>?,
    ) {
        analyticsBackend.logEvent(name, parameters)
    }

    override suspend fun logHeartbeatState(name: String, value: Boolean, timestamp: Instant) {
        heartbeatStateDao.updateState(HeartbeatStateEntity(name = name, state = value, timestamp = timestamp.toEpochMilliseconds()))
    }

    override suspend fun processHeartbeat() {
        val duration = heartbeatDuration()
        if (duration < 22.hours) {
            logger.d { "Not processing heartbeat; duration is only $duration" }
            return
        }
        val heartbeatMetrics = processHeartbeatStates() +
                HeartbeatMetric("heartbeat_duration_ms", duration.inWholeMilliseconds) +
                HeartbeatMetric("last_connected_serial", lastConnectedSerial.value ?: "<none>") +
                HeartbeatMetric("core_user_id", Firebase.auth.currentUser?.emailOrNull ?: "<none>") +
                HeartbeatMetric(
                    "ring.transfer_duration_total_ms",
                    withContext(Dispatchers.IO) {
                        cache.getLong(KEY_RING_TRANSFER_DURATION_TOTAL_MS, 0L)
                    }
                ) +
                HeartbeatMetric(
                    "ring.lifetime_collection_count",
                    withContext(Dispatchers.IO) {
                        cache.getInt(KEY_RING_LIFETIME_COLLECTION_COUNT, 0)
                    }
                ) +
                HeartbeatMetric(
                    "ring.lifetime_collection_count_serial",
                    withContext(Dispatchers.IO) {
                        cache.getStringOrNull(KEY_RING_LIFETIME_COLLECTION_COUNT_SERIAL) ?: "<none>"
                    }
                )
        logger.d { "processHeartbeat: $heartbeatMetrics" }
        withContext(Dispatchers.IO) {
            cache.remove(KEY_RING_TRANSFER_DURATION_TOTAL_MS)
            cache.remove(KEY_RING_TRANSFER_DURATION_START_TIMESTAMP)
            cache.remove(KEY_RING_LIFETIME_COLLECTION_COUNT)
            cache.remove(KEY_RING_LIFETIME_COLLECTION_COUNT_SERIAL)
        }
        logEvent("heartbeat", heartbeatMetrics.associate { it.name to it.value })
    }

    override fun updateLastConnectedSerial(serial: String?) {
        lastConnectedSerial.value = serial
    }

    override fun updateRingTransferDurationMetric(duration: Duration) {
        val ts = clock.now().toEpochMilliseconds()
        if (cache.getLong(KEY_RING_TRANSFER_DURATION_START_TIMESTAMP, -1L) == -1L) {
            cache[KEY_RING_TRANSFER_DURATION_START_TIMESTAMP] = ts
        }
        val current = cache.getLong(KEY_RING_TRANSFER_DURATION_TOTAL_MS, 0L)
        val newDurationMs = duration.inWholeMilliseconds
        cache[KEY_RING_TRANSFER_DURATION_TOTAL_MS] = current + newDurationMs
    }

    override fun updateRingLifetimeCollectionCount(serial: String, count: Int) {
        val existingSerial = cache.getStringOrNull(KEY_RING_LIFETIME_COLLECTION_COUNT_SERIAL)
        if (existingSerial != serial) {
            cache[KEY_RING_LIFETIME_COLLECTION_COUNT_SERIAL] = serial
            cache[KEY_RING_LIFETIME_COLLECTION_COUNT] = count
        } else {
            val existingCount = cache.getInt(KEY_RING_LIFETIME_COLLECTION_COUNT, 0)
            if (count > existingCount) {
                cache[KEY_RING_LIFETIME_COLLECTION_COUNT] = count
            }
        }
    }

    private suspend fun heartbeatDuration(): Duration {
        val ringTimestamp = withContext(Dispatchers.IO) {
            cache.getLong(KEY_RING_TRANSFER_DURATION_START_TIMESTAMP, -1L).takeIf { it != -1L }
        }?.let { Instant.fromEpochMilliseconds(it) }
        val earliestTimestamp = listOfNotNull(
            heartbeatStateDao.getEarliestTimestamp(),
            ringTimestamp?.toEpochMilliseconds(),
        ).minOrNull()
        if (earliestTimestamp == null) {
            return Duration.ZERO
        }
        return clock.now() - Instant.fromEpochMilliseconds(earliestTimestamp)
    }

    private suspend fun processHeartbeatStates(): List<HeartbeatMetric> {
        val now = clock.now()
        val heartbeatMetrics = mutableListOf<HeartbeatMetric>()
        heartbeatStateDao.getNames().forEach { name ->
            val values = heartbeatStateDao.getValuesAndClear(name, now.toEpochMilliseconds())
            val metric = processHeartbeatState(values, name)
            if (metric != null) {
                heartbeatMetrics.add(metric)
            }
        }
        heartbeatMetrics.addAll(deriveConnectedPercentMetrics(heartbeatMetrics))
        return heartbeatMetrics
    }
}

private const val HEARTBEAT_STATE_WATCH_CONNECTED = "watch_connected_ms_"
private const val HEARTBEAT_STATE_WATCH_CONNECT_GOAL = "watch_connectgoal_ms_"
private const val HEARTBEAT_STATE_WATCH_CONNECT_PERCENT = "watch_connect_percent_"

fun heartbeatWatchConnectedName(type: WatchHardwarePlatform) = "${HEARTBEAT_STATE_WATCH_CONNECTED}${type.revision}"
fun heartbeatWatchConnectGoalName(type: WatchHardwarePlatform) = "${HEARTBEAT_STATE_WATCH_CONNECT_GOAL}${type.revision}"
fun heartbeatWatchConnectPercentName(type: WatchHardwarePlatform) = "${HEARTBEAT_STATE_WATCH_CONNECT_PERCENT}${type.revision}"

fun processHeartbeatState(values: List<HeartbeatStateEntity>, name: String): HeartbeatMetric? {
    if (values.isEmpty()) {
        return null
    }

    // Time in state true is the sum of all durations where state was true
    val timeInState = values.windowed(2).sumOf { (first, second) ->
        if (first.state) {
            second.timestamp - first.timestamp
        } else 0
    }
    return HeartbeatMetric(name, timeInState)
}

fun deriveConnectedPercentMetrics(metrics: List<HeartbeatMetric>): List<HeartbeatMetric> {
    return WatchHardwarePlatform.entries.mapNotNull { platform ->
        val connectedTime = metrics.find { it.name == heartbeatWatchConnectedName(platform) }?.value as? Long
        val connectGoalTime = metrics.find { it.name == heartbeatWatchConnectGoalName(platform) } ?.value as? Long
        if (connectedTime != null && connectGoalTime != null && connectGoalTime > 0L) {
            val connectedPercent = connectedTime.toDouble() / connectGoalTime.toDouble() * 100
            HeartbeatMetric(heartbeatWatchConnectPercentName(platform), connectedPercent)
        } else {
            null
        }
    }
}

data class HeartbeatMetric(
    val name: String,
    val value: Any,
)