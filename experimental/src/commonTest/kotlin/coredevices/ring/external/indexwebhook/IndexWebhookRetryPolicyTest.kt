package coredevices.ring.external.indexwebhook

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class IndexWebhookRetryPolicyTest {
    @Test
    fun retriesTemporaryHttpFailures() {
        listOf(408, 425, 429, 500, 503, 599).forEach {
            assertTrue(it.isRetryableWebhookStatus(), "$it should be retried")
        }
    }

    @Test
    fun doesNotRetryPermanentHttpFailures() {
        listOf(400, 401, 403, 404, 409, 422).forEach {
            assertFalse(it.isRetryableWebhookStatus(), "$it should require manual retry")
        }
    }

    @Test
    fun exponentialBackoffCapsAtOneHourAndHonorsRetryAfter() {
        assertEquals(1.minutes, webhookRetryDelay(0, null, Duration.ZERO))
        assertEquals(8.minutes, webhookRetryDelay(3, null, Duration.ZERO))
        assertEquals(1.hours, webhookRetryDelay(20, null, Duration.ZERO))
        assertEquals(2.hours, webhookRetryDelay(0, 2.hours, Duration.ZERO))
    }

    @Test
    fun parsesRetryAfterSecondsAndHttpDate() {
        val now = Instant.parse("2015-10-21T07:27:00Z")

        assertEquals(120.seconds, "120".toRetryAfterDuration(now))
        assertEquals(1.minutes, "Wed, 21 Oct 2015 07:28:00 GMT".toRetryAfterDuration(now))
        assertEquals(null, "later".toRetryAfterDuration(now))
    }
}
