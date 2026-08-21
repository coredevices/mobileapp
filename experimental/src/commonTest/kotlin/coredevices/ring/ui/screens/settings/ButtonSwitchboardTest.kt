package coredevices.ring.ui.screens.settings

import coredevices.ring.external.indexwebhook.IndexWebhookConfig
import coredevices.ring.service.button.GestureDestination
import coredevices.ring.service.button.GestureKind
import coredevices.ring.service.button.RingGesture
import coredevices.ring.service.button.accepts
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ButtonSwitchboardTest {

    @Test
    fun everyOfferedDestinationIsValidForTheGesture() {
        RingGesture.entries.forEach { gesture ->
            destinationsFor(gesture.kind, hasSandboxGroups = true).forEach { destination ->
                assertTrue(gesture.accepts(destination), "$gesture rejects $destination")
            }
        }
    }

    @Test
    fun musicGesturesOfferPlayPauseNextTrackAndNothing() {
        assertEquals(
            listOf(
                GestureDestination.PlayPause,
                GestureDestination.NextTrack,
                GestureDestination.Nothing,
            ),
            destinationsFor(GestureKind.Music, hasSandboxGroups = true),
        )
    }

    @Test
    fun sandboxIsOnlyOfferedWhenAGroupExists() {
        assertEquals(
            listOf(
                GestureDestination.IndexAgent,
                GestureDestination.WebSearch,
                GestureDestination.WebhookOnly,
                GestureDestination.McpSandbox(null),
                GestureDestination.Nothing,
            ),
            destinationsFor(GestureKind.Recording, hasSandboxGroups = true),
        )
        assertFalse(
            destinationsFor(GestureKind.Recording, hasSandboxGroups = false)
                .any { it is GestureDestination.McpSandbox }
        )
    }

    @Test
    fun copyRowFollowsAnActiveWebhookConfig() {
        assertFalse(IndexWebhookConfig().isActive)
        assertFalse(IndexWebhookConfig(url = "https://example.com/hook").isActive)
        assertFalse(IndexWebhookConfig(url = "", saved = true).isActive)
        assertTrue(IndexWebhookConfig(url = "https://example.com/hook", saved = true).isActive)
    }

    @Test
    fun rowOrderAndGlyphsMatchThePressPatterns() {
        assertEquals(
            listOf("Click", "Double click", "Triple click", "Hold & Talk", "Double click & hold"),
            RingGesture.entries.map { it.gestureLabel },
        )
        assertEquals(
            RingGesture.entries.map { it.sequence.size },
            RingGesture.entries.map { it.glyph.size },
        )
    }

    @Test
    fun everyRecordingRouteButNothingLeavesAWebhookRowToTap() {
        RingGesture.entries.filter { it.kind == GestureKind.Recording }.forEach { gesture ->
            destinationsFor(GestureKind.Recording, hasSandboxGroups = true)
                .filter { it != GestureDestination.Nothing }
                .forEach {
                    assertTrue(gestureSheetStaysOpenFor(gesture, it), "$gesture / $it closes early")
                }
            assertFalse(gestureSheetStaysOpenFor(gesture, GestureDestination.Nothing))
        }
    }

    @Test
    fun aMusicChoiceIsTheWholeInteraction() {
        RingGesture.entries.filter { it.kind == GestureKind.Music }.forEach { gesture ->
            destinationsFor(GestureKind.Music, hasSandboxGroups = true).forEach {
                assertFalse(gestureSheetStaysOpenFor(gesture, it), "$gesture / $it stays open")
            }
        }
    }

    @Test
    fun everyDestinationHasATileLabel() {
        GestureKind.entries
            .flatMap { destinationsFor(it, hasSandboxGroups = true) }
            .forEach { assertTrue(it.tileLabel.isNotBlank(), "$it has no tile label") }
    }
}
