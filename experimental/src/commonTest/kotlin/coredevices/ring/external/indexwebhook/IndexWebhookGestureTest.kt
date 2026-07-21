package coredevices.ring.external.indexwebhook

import kotlin.test.Test
import kotlin.test.assertEquals

class IndexWebhookGestureTest {
    @Test
    fun triggerHeaderValuesAreStable() {
        assertEquals(
            "single-click-hold",
            IndexWebhookGesture.SingleClickHold.headerValue,
        )
        assertEquals(
            "double-click-hold",
            IndexWebhookGesture.DoubleClickHold.headerValue,
        )
    }
}
