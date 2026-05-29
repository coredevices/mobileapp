package coredevices.util.models

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DownloadJobLivenessTest {

    @Test
    fun nullHeartbeatIsStale() {
        assertTrue(DownloadJobLiveness.isStale(lastHeartbeatMillis = null, nowMillis = 1_000L))
    }

    @Test
    fun recentHeartbeatIsAlive() {
        assertFalse(
            DownloadJobLiveness.isStale(
                lastHeartbeatMillis = 100_000L,
                nowMillis = 100_000L + 30_000L,
                staleAfterMillis = 90_000L,
            )
        )
    }

    @Test
    fun oldHeartbeatIsStale() {
        assertTrue(
            DownloadJobLiveness.isStale(
                lastHeartbeatMillis = 100_000L,
                nowMillis = 100_000L + 90_000L,
                staleAfterMillis = 90_000L,
            )
        )
    }

    @Test
    fun boundaryIsStale() {
        // Exactly at the threshold counts as stale (>=).
        assertTrue(
            DownloadJobLiveness.isStale(
                lastHeartbeatMillis = 0L,
                nowMillis = 90_000L,
                staleAfterMillis = 90_000L,
            )
        )
        assertFalse(
            DownloadJobLiveness.isStale(
                lastHeartbeatMillis = 0L,
                nowMillis = 89_999L,
                staleAfterMillis = 90_000L,
            )
        )
    }

    @Test
    fun clockMovedBackwardsIsStale() {
        assertTrue(
            DownloadJobLiveness.isStale(
                lastHeartbeatMillis = 100_000L,
                nowMillis = 50_000L,
            )
        )
    }
}