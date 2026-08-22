package coredevices.ring.external.indexlocal

import com.russhwolf.settings.MapSettings
import coredevices.ring.external.indexwebhook.IndexWebhookPayloadMode
import coredevices.ring.service.button.GestureDestination
import coredevices.ring.service.button.RingGesture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IndexLocalAppPreferencesTest {

    @Test
    fun recordingGesturesAreHoldAndClickHold() {
        assertEquals(
            listOf(RingGesture.Hold, RingGesture.ClickHold),
            IndexLocalAppPreferences.gestures,
        )
    }

    @Test
    fun defaultConfigIsInactive() {
        val prefs = IndexLocalAppPreferences(MapSettings())
        assertEquals(IndexLocalAppConfig(), prefs.configFor(RingGesture.Hold))
        assertFalse(prefs.configFor(RingGesture.Hold).isActive)
    }

    @Test
    fun enablingNotesnookPersistsPerGesture() {
        val settings = MapSettings()
        val prefs = IndexLocalAppPreferences(settings)
        prefs.setConfig(
            RingGesture.Hold,
            IndexLocalAppConfig(
                notesnookEnabled = true,
                payloadMode = IndexWebhookPayloadMode.Both,
            ),
        )
        val reloaded = IndexLocalAppPreferences(settings)
        assertTrue(reloaded.configFor(RingGesture.Hold).isActive)
        assertEquals(IndexWebhookPayloadMode.Both, reloaded.configFor(RingGesture.Hold).payloadMode)
        assertFalse(reloaded.configFor(RingGesture.ClickHold).isActive)
    }

    @Test
    fun anyEnabledIsTrueWhenAnyGestureIsOn() {
        val prefs = IndexLocalAppPreferences(MapSettings())
        assertFalse(prefs.anyEnabled())
        prefs.setNotesnookEnabled(RingGesture.ClickHold, true)
        assertTrue(prefs.anyEnabled())
    }

    @Test
    fun doesNotSendWhenRoutedToNothing() {
        val config = IndexLocalAppConfig(notesnookEnabled = true)
        assertTrue(config.sendsFor(GestureDestination.IndexAgent))
        assertTrue(config.sendsFor(GestureDestination.WebhookOnly))
        assertFalse(config.sendsFor(GestureDestination.Nothing))
    }

    @Test
    fun triggerWireNamesAreStable() {
        assertEquals("single-click-hold", RingGesture.Hold.localCaptureTriggerValue())
        assertEquals("double-click-hold", RingGesture.ClickHold.localCaptureTriggerValue())
        assertEquals("both", IndexWebhookPayloadMode.Both.wireName())
        assertEquals("recording", IndexWebhookPayloadMode.RecordingOnly.wireName())
        assertEquals("transcription", IndexWebhookPayloadMode.TranscriptionOnly.wireName())
    }
}
