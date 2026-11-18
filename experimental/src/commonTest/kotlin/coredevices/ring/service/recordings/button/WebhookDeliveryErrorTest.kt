package coredevices.ring.service.recordings.button

import coredevices.ring.external.indexwebhook.IndexWebhookDeliveryOutcome
import coredevices.ring.external.indexwebhook.IndexWebhookRunResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WebhookDeliveryErrorTest {

    private fun outcome(ok: Boolean, status: String = "200 OK", detail: String = "", willRetry: Boolean = false) =
        IndexWebhookDeliveryOutcome(
            deliveryId = "rec-1",
            result = IndexWebhookRunResult(ok = ok, status = status, detail = detail, byteSize = 0, durationMs = 0),
            willRetry = willRetry,
        )

    @Test
    fun successfulDeliveryCountsAsSent() {
        assertNull(webhookDeliveryError(outcome(ok = true)))
    }

    @Test
    fun httpErrorReportsStatusOnly() {
        assertEquals(
            "Webhook failed (500 ERROR)",
            webhookDeliveryError(outcome(ok = false, status = "500 ERROR", detail = "boom")),
        )
    }

    @Test
    fun connectionFailureHidesTheRawError() {
        assertEquals(
            "Couldn't reach the webhook",
            webhookDeliveryError(outcome(ok = false, status = "FAILED", detail = "Error Domain=NSURLErrorDomain")),
        )
    }

    @Test
    fun stillRetryingAtTheDeadlineReportsTheLastAttempt() {
        assertEquals(
            "Webhook failed (503 ERROR)",
            webhookDeliveryError(outcome(ok = false, status = "503 ERROR", willRetry = true)),
        )
    }

    @Test
    fun noAttemptIsAnError() {
        assertEquals("Webhook did not respond", webhookDeliveryError(null))
    }

    @Test
    fun webhookTurnedOffBeforeSendingCountsAsSent() {
        assertNull(webhookDeliveryError(IndexWebhookDeliveryOutcome("rec-1", result = null, willRetry = false)))
    }
}
