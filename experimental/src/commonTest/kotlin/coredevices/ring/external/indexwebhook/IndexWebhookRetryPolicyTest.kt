package coredevices.ring.external.indexwebhook

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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
}
