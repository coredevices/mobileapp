package coredevices.analytics

import kotlin.time.Instant

/** Contributes heartbeat metrics covering only data recorded since the previous heartbeat. */
fun interface HeartbeatMetricsSource {
    suspend fun heartbeatMetrics(since: Instant, now: Instant): List<HeartbeatMetric>
}
