package coredevices.ring.ui.screens.settings

import coredevices.ring.agent.integrations.providers.IntegrationProvider
import coredevices.ring.agent.integrations.providers.IntegrationSupport
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
            destinationsFor(gesture.kind).forEach { destination ->
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
            destinationsFor(GestureKind.Music),
        )
    }

    @Test
    fun recordingGesturesOfferEveryAgentRoute() {
        assertEquals(
            listOf(
                GestureDestination.IndexAgent,
                GestureDestination.LocalAgent,
                GestureDestination.WebSearch,
                GestureDestination.WebhookOnly,
                GestureDestination.Nothing,
            ),
            destinationsFor(GestureKind.Recording),
        )
    }

    @Test
    fun agentPluginsAreOfferedAfterTheIndexAgents() {
        val claude = GestureDestination.AgentPlugin("plugin:claude")
        val codex = GestureDestination.AgentPlugin("plugin:codex")

        assertEquals(
            listOf(
                GestureDestination.IndexAgent,
                GestureDestination.LocalAgent,
                claude,
                codex,
                GestureDestination.WebSearch,
                GestureDestination.WebhookOnly,
                GestureDestination.Nothing,
            ),
            destinationsFor(GestureKind.Recording, listOf(claude, codex)),
        )
        assertEquals(destinationsFor(GestureKind.Music), destinationsFor(GestureKind.Music, listOf(claude)))
        RingGesture.entries.filter { it.kind == GestureKind.Recording }.forEach {
            assertTrue(it.accepts(claude))
            assertTrue(gestureSheetStaysOpenFor(it, claude))
        }
    }

    @Test
    fun agentPluginOptionsCarryTheirProviderTitles() {
        val claude = FakeAgentProvider("plugin:claude", "Claude")
        val options = listOf(AgentPluginOption(claude, connected = false))

        assertEquals(GestureDestination.AgentPlugin("plugin:claude"), options.single().destination)
        assertEquals(mapOf("plugin:claude" to "Claude"), options.titles())
    }

    @Test
    fun agentPluginTilesAreLabelledByTheirProvider() {
        val claude = GestureDestination.AgentPlugin("plugin:claude")

        assertEquals("Claude", claude.tileLabel(mapOf("plugin:claude" to "Claude")))
        assertEquals("Unavailable agent", claude.tileLabel(emptyMap()))
        assertEquals("Index agent", GestureDestination.IndexAgent.tileLabel(mapOf("plugin:claude" to "Claude")))
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
            destinationsFor(GestureKind.Recording)
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
            destinationsFor(GestureKind.Music).forEach {
                assertFalse(gestureSheetStaysOpenFor(gesture, it), "$gesture / $it stays open")
            }
        }
    }

    @Test
    fun everyDestinationHasATileLabel() {
        GestureKind.entries
            .flatMap { destinationsFor(it) }
            .forEach { assertTrue(it.tileLabel.isNotBlank(), "$it has no tile label") }
    }

    private class FakeAgentProvider(override val id: String, override val title: String) : IntegrationProvider {
        override val supports = setOf(IntegrationSupport.Agent)
    }
}
