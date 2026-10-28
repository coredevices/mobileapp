package coredevices.ring.ui.screens.settings

import coredevices.ring.agent.integrations.providers.IntegrationProvider
import coredevices.ring.agent.integrations.providers.IntegrationSupport
import coredevices.ring.agent.pebble_plugin.StorePlugin
import coredevices.ring.external.indexwebhook.IndexWebhookConfig
import coredevices.ring.service.button.GestureDestination
import coredevices.ring.service.button.GestureKind
import coredevices.ring.service.button.RingGesture
import coredevices.ring.service.button.accepts
import coredevices.ring.ui.viewmodel.DestinationItem
import io.rebble.libpebblecommon.plugin.PluginMetadata
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

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
    fun musicGesturesOfferPlayPauseNextPreviousAgentCommandWebhookAndNothing() {
        assertEquals(
            listOf(
                GestureDestination.PlayPause,
                GestureDestination.NextTrack,
                GestureDestination.PreviousTrack,
                GestureDestination.AgentCommand,
                GestureDestination.Webhook,
                GestureDestination.Nothing,
            ),
            destinationsFor(GestureKind.Music),
        )
    }

    @Test
    fun clicksStillReachTheAgentAndWebhookWithoutMusicControl() {
        assertEquals(
            listOf(GestureDestination.AgentCommand, GestureDestination.Webhook, GestureDestination.Nothing),
            destinationsFor(GestureKind.Music, musicControl = false),
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
    fun agentPluginRoutesAreValidForRecordingGesturesAndKeepTheSheetOpen() {
        val claude = GestureDestination.AgentPlugin("plugin:claude")

        assertFalse(claude in destinationsFor(GestureKind.Recording))
        RingGesture.entries.filter { it.kind == GestureKind.Recording }.forEach {
            assertTrue(it.accepts(claude))
            assertTrue(gestureSheetStaysOpenFor(it, claude))
        }
    }

    @Test
    fun agentTitlesComeFromInstalledPluginsOnly() {
        val claude = installed("plugin:claude", "Claude", connected = true)
        val things = DestinationItem.InStore(storePlugin("Things"))

        assertEquals(mapOf("plugin:claude" to "Claude"), listOf<DestinationItem>(claude, things).agentTitles())
        assertEquals(null, null.agentTitles())
        assertEquals(GestureDestination.AgentPlugin("plugin:claude"), claude.route)
    }

    @Test
    fun aRouteToAnUninstalledAgentIsKeptSoItCanBeChanged() {
        val claude = installed("plugin:claude", "Claude", connected = true)
        val gone = GestureDestination.AgentPlugin("plugin:gone")

        assertEquals(gone, missingAgentRoute(gone, listOf(claude)))
        assertEquals(null, missingAgentRoute(gone, null))
        assertEquals(null, missingAgentRoute(claude.route, listOf(claude)))
        assertEquals(null, missingAgentRoute(GestureDestination.IndexAgent, emptyList()))
    }

    @Test
    fun onlyAConnectedRoutedPluginWithALockerPageOffersItsSettings() {
        val claude = installed("plugin:claude", "Claude", connected = true, hasLockerPage = true)
        val codex = installed("plugin:codex", "Codex", connected = true)
        val gone = installed("plugin:gone", "Gone", connected = false, hasLockerPage = true)
        val agents = listOf(claude, codex, gone)

        assertEquals(claude, agents.configurableAgentFor(claude.route))
        assertEquals(null, agents.configurableAgentFor(codex.route))
        assertEquals(null, agents.configurableAgentFor(gone.route))
        assertEquals(null, agents.configurableAgentFor(GestureDestination.IndexAgent))
    }

    @Test
    fun agentPluginTilesAreLabelledByTheirProvider() {
        val claude = GestureDestination.AgentPlugin("plugin:claude")

        assertEquals("Claude", claude.tileLabel(mapOf("plugin:claude" to "Claude")))
        assertEquals("Unavailable agent", claude.tileLabel(emptyMap()))
        assertEquals("Agent plugin", claude.tileLabel(null))
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
    fun aMusicChoiceIsTheWholeInteractionExceptWebhookAndAgentCommand() {
        RingGesture.entries.filter { it.kind == GestureKind.Music }.forEach { gesture ->
            destinationsFor(GestureKind.Music).forEach {
                assertEquals(
                    it == GestureDestination.Webhook || it == GestureDestination.AgentCommand,
                    gestureSheetStaysOpenFor(gesture, it),
                    "$gesture / $it",
                )
            }
        }
    }

    @Test
    fun everyDestinationHasATileLabel() {
        GestureKind.entries
            .flatMap { destinationsFor(it) }
            .forEach { assertTrue(it.tileLabel.isNotBlank(), "$it has no tile label") }
    }

    private fun installed(id: String, title: String, connected: Boolean, hasLockerPage: Boolean = false) =
        DestinationItem.Installed(FakeAgentProvider(id, title), title, connected, hasLockerPage)

    private fun storePlugin(name: String) = StorePlugin(
        metadata = PluginMetadata(
            pluginUuid = Uuid.parse("00000000-0000-0000-0002-000000000001"),
            name = name,
            description = "",
            sources = emptyList(),
            actions = emptyList(),
        ),
        configurable = false,
    )

    private class FakeAgentProvider(override val id: String, override val title: String) : IntegrationProvider {
        override val supports = setOf(IntegrationSupport.Agent)
    }
}
