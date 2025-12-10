package coredevices.analytics

import co.touchlab.kermit.Logger
import coredevices.database.HeartbeatStateDao
import coredevices.database.HeartbeatStateEntity
import coredevices.util.emailOrNull
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import io.rebble.libpebblecommon.metadata.WatchHardwarePlatform
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

interface CoreAnalytics {
    fun logEvent(name: String, parameters: Map<String, Any>? = null)
    suspend fun logHeartbeatState(name: String, value: Boolean, timestamp: Instant)
    suspend fun processHeartbeat()
    fun updateLastConnectedSerial(serial: String?)
}

class RealCoreAnalytics(
    private val analyticsBackend: AnalyticsBackend,
    private val heartbeatStateDao: HeartbeatStateDao,
    private val clock: Clock,
) : CoreAnalytics {
    private val logger = Logger.withTag("RealCoreAnalytics")
    // We can't see libpebble from util right now, so it has to be this way around
    private val lastConnectedSerial = MutableStateFlow<String?>(null)

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
                HeartbeatMetric("last_connected_serial", lastConnectedSerial.value ?: "<none>")
                HeartbeatMetric("core_user_id", Firebase.auth.currentUser?.emailOrNull ?: "<none>")
        logger.d { "processHeartbeat: $heartbeatMetrics" }
        logEvent("heartbeat", heartbeatMetrics.associate { it.name to it.value })
    }

    override fun updateLastConnectedSerial(serial: String?) {
        lastConnectedSerial.value = serial
    }

    private suspend fun heartbeatDuration(): Duration {
        val earliestTimestamp = heartbeatStateDao.getEarliestTimestamp()
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