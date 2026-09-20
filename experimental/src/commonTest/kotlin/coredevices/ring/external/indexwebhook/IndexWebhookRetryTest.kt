package coredevices.ring.external.indexwebhook

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IndexWebhookRetryTest {

    @Test
    fun networkFailuresAreRetryable() {
        assertTrue(isRetryableStatus(null))
    }

    @Test
    fun retryableHttpStatuses() {
        assertTrue(isRetryableStatus(408))
        assertTrue(isRetryableStatus(429))
        assertTrue(isRetryableStatus(500))
        assertTrue(isRetryableStatus(503))
    }

    @Test
    fun permanentHttpStatusesAreNotRetryable() {
        assertFalse(isRetryableStatus(200))
        assertFalse(isRetryableStatus(400))
        assertFalse(isRetryableStatus(401))
        assertFalse(isRetryableStatus(403))
        assertFalse(isRetryableStatus(404))
        assertFalse(isRetryableStatus(422))
    }

    @Test
    fun retriesAreCapped() {
        assertTrue(WEBHOOK_RETRY_BACKOFF.size >= 3)
        assertTrue(WEBHOOK_RETRY_BACKOFF.all { it.inWholeSeconds <= 5 })
    }
}
