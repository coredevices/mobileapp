package coredevices.ring.external.indexwebhook

import kotlin.test.Test
import kotlin.test.assertEquals

class IndexWebhookRecordingTriggerTest {
    @Test
    fun triggerHeaderValuesAreStable() {
        assertEquals(
            "single-click-hold",
            IndexWebhookRecordingTrigger.SingleClickHold.headerValue,
        )
        assertEquals(
            "double-click-hold",
            IndexWebhookRecordingTrigger.DoubleClickHold.headerValue,
        )
    }
}
