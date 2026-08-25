package coredevices.ring.external.indexwebhook

import coredevices.ring.service.button.GestureDestination
import coredevices.ring.service.button.RingGesture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IndexWebhookSendDecisionTest {

    private val configured = IndexWebhookConfig(url = "https://example.com/hook", saved = true)

    @Test
    fun webhookOnlyRouteSendsWhenConfigured() {
        assertTrue(configured.sendsFor(GestureDestination.WebhookOnly))
    }

    @Test
    fun webhookOnlyRouteSendsNothingWithoutAConfig() {
        assertFalse(IndexWebhookConfig().sendsFor(GestureDestination.WebhookOnly))
    }

    @Test
    fun otherRoutesSendAlongsideTheirMainDestination() {
        assertTrue(configured.sendsFor(GestureDestination.IndexAgent))
        assertTrue(configured.sendsFor(GestureDestination.WebSearch))
        assertTrue(configured.sendsFor(GestureDestination.McpSandbox(7L)))
    }

    @Test
    fun otherRoutesDoNotSendWithoutAConfig() {
        assertFalse(IndexWebhookConfig().sendsFor(GestureDestination.IndexAgent))
    }

    @Test
    fun aGestureRoutedToNothingNeverSends() {
        assertFalse(configured.sendsFor(GestureDestination.Nothing))
    }

    @Test
    fun anUnsavedOrUrllessDraftNeverSends() {
        assertFalse(configured.copy(saved = false).sendsFor(GestureDestination.IndexAgent))
        assertFalse(configured.copy(url = null).sendsFor(GestureDestination.IndexAgent))
        assertFalse(configured.copy(url = "  ").sendsFor(GestureDestination.IndexAgent))
    }

    @Test
    fun aRecordingOnlyWebhookDoesNotWaitForProcessing() {
        assertTrue(IndexWebhookPayloadMode.RecordingOnly.sendsBeforeProcessing(hasAudio = true))
    }

    @Test
    fun anythingCarryingTheTranscriptionWaitsForIt() {
        assertFalse(IndexWebhookPayloadMode.TranscriptionOnly.sendsBeforeProcessing(hasAudio = true))
        assertFalse(IndexWebhookPayloadMode.Both.sendsBeforeProcessing(hasAudio = true))
    }

    @Test
    fun typedInputHasNoAudioToSendEarly() {
        IndexWebhookPayloadMode.entries.forEach {
            assertFalse(it.sendsBeforeProcessing(hasAudio = false), "$it sent early with no audio")
        }
    }

    @Test
    fun triggerHeaderValuesAreStable() {
        assertEquals("single-click-hold", RingGesture.Hold.webhookTriggerValue)
        assertEquals("double-click-hold", RingGesture.ClickHold.webhookTriggerValue)
    }
}
