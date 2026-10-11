package coredevices.ring.service.button

import com.russhwolf.settings.MapSettings
import coredevices.ring.database.MusicControlMode
import coredevices.ring.database.PreferencesImpl
import coredevices.ring.database.SecondaryMode
import coredevices.ring.service.ButtonPress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GestureRoutingTest {

    private fun routing(
        musicControlMode: MusicControlMode? = null,
        secondaryMode: SecondaryMode? = null,
        stored: MapSettings = MapSettings(),
    ): GestureRoutingPreferences {
        val legacy = MapSettings()
        musicControlMode?.let { legacy.putInt("music_control_mode", it.id) }
        secondaryMode?.let { legacy.putInt("ring_secondary_mode", it.id) }
        return GestureRoutingPreferences(stored, PreferencesImpl(legacy))
    }

    @Test
    fun sequencesResolveToGestures() {
        assertEquals(RingGesture.Click, RingGesture.forSequence(listOf(ButtonPress.Short)))
        assertEquals(
            RingGesture.DoubleClick,
            RingGesture.forSequence(listOf(ButtonPress.Short, ButtonPress.Short))
        )
        assertEquals(
            RingGesture.TripleClick,
            RingGesture.forSequence(listOf(ButtonPress.Short, ButtonPress.Short, ButtonPress.Short))
        )
        assertEquals(RingGesture.Hold, RingGesture.forSequence(listOf(ButtonPress.Long)))
        assertEquals(
            RingGesture.ClickHold,
            RingGesture.forSequence(listOf(ButtonPress.Short, ButtonPress.Long))
        )
        assertNull(RingGesture.forSequence(emptyList()))
        assertNull(RingGesture.forSequence(listOf(ButtonPress.Long, ButtonPress.Long)))
        assertNull(RingGesture.forSequence(listOf(ButtonPress.Long, ButtonPress.Short)))
    }

    @Test
    fun musicControlModeDisabledMigratesToNothing() {
        val routes = routing(MusicControlMode.Disabled).routes.value

        assertEquals(GestureDestination.Nothing, routes[RingGesture.Click])
        assertEquals(GestureDestination.Nothing, routes[RingGesture.DoubleClick])
        assertEquals(GestureDestination.Nothing, routes[RingGesture.TripleClick])
    }

    @Test
    fun musicControlModeSingleClickMigratesToClickPlayPause() {
        val routes = routing(MusicControlMode.SingleClick).routes.value

        assertEquals(GestureDestination.PlayPause, routes[RingGesture.Click])
        assertEquals(GestureDestination.NextTrack, routes[RingGesture.DoubleClick])
        assertEquals(GestureDestination.Nothing, routes[RingGesture.TripleClick])
    }

    @Test
    fun musicControlModeDoubleClickMigratesToDoubleClickPlayPause() {
        val routes = routing(MusicControlMode.DoubleClick).routes.value

        assertEquals(GestureDestination.Nothing, routes[RingGesture.Click])
        assertEquals(GestureDestination.PlayPause, routes[RingGesture.DoubleClick])
        assertEquals(GestureDestination.NextTrack, routes[RingGesture.TripleClick])
    }

    @Test
    fun secondaryModeMigratesToClickHoldRoute() {
        assertEquals(
            GestureDestination.IndexAgent,
            routing(secondaryMode = SecondaryMode.Disabled).destinationFor(RingGesture.ClickHold)
        )
        assertEquals(
            GestureDestination.WebSearch,
            routing(secondaryMode = SecondaryMode.Search).destinationFor(RingGesture.ClickHold)
        )
    }

    /** The removed sandbox mode ran a chosen group's agent over the ring's recordings; the Index
     *  agent is the nearest thing left, whether the route came from the legacy pref or was stored. */
    @Test
    fun legacySandboxRoutesMigrateToTheIndexAgent() {
        assertEquals(
            GestureDestination.IndexAgent,
            routing(secondaryMode = SecondaryMode.McpSandbox).destinationFor(RingGesture.ClickHold)
        )
        val stored = MapSettings(
            "ring_gesture_route_Hold" to "mcp_sandbox:7",
            "ring_gesture_route_ClickHold" to "mcp_sandbox:",
        )
        val routing = routing(secondaryMode = SecondaryMode.Search, stored = stored)
        assertEquals(GestureDestination.IndexAgent, routing.destinationFor(RingGesture.Hold))
        assertEquals(GestureDestination.IndexAgent, routing.destinationFor(RingGesture.ClickHold))
    }

    /** The removed webhook mode kept sending to the webhook and ran no agent; a webhook-only
     *  route is the same behaviour, including when no url was ever configured. */
    @Test
    fun legacyWebhookSecondaryModeMigratesToWebhookOnly() {
        assertEquals(
            GestureDestination.WebhookOnly,
            routing(secondaryMode = SecondaryMode.IndexWebhook).destinationFor(RingGesture.ClickHold)
        )
        assertEquals(
            GestureDestination.IndexAgent,
            routing(secondaryMode = SecondaryMode.IndexWebhook).destinationFor(RingGesture.Hold)
        )
    }

    @Test
    fun storedSecondaryModeIdsDecodeToTheirModes() {
        assertEquals(SecondaryMode.Disabled, SecondaryMode.fromId(0))
        assertEquals(SecondaryMode.Search, SecondaryMode.fromId(1))
        assertEquals(SecondaryMode.IndexWebhook, SecondaryMode.fromId(2))
        assertEquals(SecondaryMode.McpSandbox, SecondaryMode.fromId(3))
        assertEquals(SecondaryMode.Disabled, SecondaryMode.fromId(99))
    }

    @Test
    fun holdMigratesToIndexAgent() {
        MusicControlMode.entries.forEach { music ->
            SecondaryMode.entries.forEach { secondary ->
                assertEquals(
                    GestureDestination.IndexAgent,
                    routing(music, secondary).destinationFor(RingGesture.Hold),
                    "hold route for $music/$secondary"
                )
            }
        }
    }

    @Test
    fun defaultsMatchLegacyDefaults() {
        val routes = routing().routes.value

        assertEquals(
            migratedRoutes(MusicControlMode.DoubleClick, SecondaryMode.Search),
            routes
        )
    }

    @Test
    fun onlyMatchingDestinationKindsAreAccepted() {
        val music = listOf(RingGesture.Click, RingGesture.DoubleClick, RingGesture.TripleClick)
        val recording = listOf(RingGesture.Hold, RingGesture.ClickHold)

        music.forEach {
            assertTrue(it.accepts(GestureDestination.PlayPause))
            assertTrue(it.accepts(GestureDestination.NextTrack))
            assertTrue(it.accepts(GestureDestination.Nothing))
            assertFalse(it.accepts(GestureDestination.IndexAgent))
            assertFalse(it.accepts(GestureDestination.LocalAgent))
            assertFalse(it.accepts(GestureDestination.RemoteAgent))
            assertFalse(it.accepts(GestureDestination.WebSearch))
            assertFalse(it.accepts(GestureDestination.WebhookOnly))
        }
        recording.forEach {
            assertTrue(it.accepts(GestureDestination.IndexAgent))
            assertTrue(it.accepts(GestureDestination.LocalAgent))
            assertTrue(it.accepts(GestureDestination.RemoteAgent))
            assertTrue(it.accepts(GestureDestination.WebSearch))
            assertTrue(it.accepts(GestureDestination.WebhookOnly))
            assertTrue(it.accepts(GestureDestination.Nothing))
            assertFalse(it.accepts(GestureDestination.PlayPause))
            assertFalse(it.accepts(GestureDestination.NextTrack))
        }
    }

    @Test
    fun settingAnInvalidDestinationFails() {
        val routing = routing()

        assertFailsWith<IllegalArgumentException> {
            routing.setRoute(RingGesture.Click, GestureDestination.IndexAgent)
        }
        assertFailsWith<IllegalArgumentException> {
            routing.setRoute(RingGesture.Hold, GestureDestination.PlayPause)
        }
    }

    @Test
    fun routesRoundTripThroughSettings() {
        val stored = MapSettings()
        routing(stored = stored).apply {
            setRoute(RingGesture.Click, GestureDestination.PlayPause)
            setRoute(RingGesture.DoubleClick, GestureDestination.NextTrack)
            setRoute(RingGesture.Hold, GestureDestination.RemoteAgent)
            setRoute(RingGesture.ClickHold, GestureDestination.LocalAgent)
        }

        // A fresh instance ignores the legacy prefs for gestures with a stored route.
        val reloaded = routing(MusicControlMode.Disabled, SecondaryMode.Search, stored = stored)

        assertEquals(GestureDestination.PlayPause, reloaded.destinationFor(RingGesture.Click))
        assertEquals(GestureDestination.RemoteAgent, reloaded.destinationFor(RingGesture.Hold))
        assertEquals(GestureDestination.LocalAgent, reloaded.destinationFor(RingGesture.ClickHold))
        assertEquals(GestureDestination.NextTrack, reloaded.destinationFor(RingGesture.DoubleClick))
        assertEquals(GestureDestination.Nothing, reloaded.destinationFor(RingGesture.TripleClick))
    }

    /** The app-wide "Local LLM" mode this replaced: id 1 was local-only, 0 cloud, 2 cloud-first. */
    @Test
    fun legacyLocalOnlyLlmModeMigratesIndexAgentRoutesToTheLocalAgent() {
        val stored = MapSettings("llm_mode" to 1, "ring_gesture_route_ClickHold" to "index_agent")

        val routing = routing(secondaryMode = SecondaryMode.Search, stored = stored)

        assertEquals(GestureDestination.LocalAgent, routing.destinationFor(RingGesture.Hold))
        assertEquals(GestureDestination.LocalAgent, routing.destinationFor(RingGesture.ClickHold))
        assertEquals("local_agent", stored.getStringOrNull("ring_gesture_route_Hold"))
        assertEquals("local_agent", stored.getStringOrNull("ring_gesture_route_ClickHold"))
        assertFalse(stored.hasKey("llm_mode"))
    }

    @Test
    fun legacyLocalOnlyLlmModeLeavesOtherRoutesAlone() {
        val stored = MapSettings("llm_mode" to 1)

        val routing = routing(secondaryMode = SecondaryMode.Search, stored = stored)

        assertEquals(GestureDestination.WebSearch, routing.destinationFor(RingGesture.ClickHold))
    }

    @Test
    fun legacyCloudOnlyLlmModeMigratesToTheRemoteAgent() {
        val stored = MapSettings("llm_mode" to 0)

        val routing = routing(stored = stored)

        assertEquals(GestureDestination.RemoteAgent, routing.destinationFor(RingGesture.Hold))
        assertEquals("remote_agent", stored.getStringOrNull("ring_gesture_route_Hold"))
        assertFalse(stored.hasKey("llm_mode"))
    }

    @Test
    fun legacyCloudFirstLlmModeAndNoModeStayOnTheHybridAgent() {
        listOf(MapSettings("llm_mode" to 2), MapSettings()).forEach { stored ->
            val routing = routing(stored = stored)

            assertEquals(GestureDestination.IndexAgent, routing.destinationFor(RingGesture.Hold))
            assertFalse(stored.hasKey("llm_mode"))
        }
    }

    @Test
    fun legacyLocalLlmSwitchMigratesLikeLocalOnlyMode() {
        val stored = MapSettings("use_cactus_agent" to true)

        val routing = routing(stored = stored)

        assertEquals(GestureDestination.LocalAgent, routing.destinationFor(RingGesture.Hold))
        assertFalse(stored.hasKey("use_cactus_agent"))
    }

    @Test
    fun migrationRunsOnceSoALaterHybridChoiceSticks() {
        val stored = MapSettings("llm_mode" to 1)
        routing(stored = stored).setRoute(RingGesture.Hold, GestureDestination.IndexAgent)

        assertEquals(GestureDestination.IndexAgent, routing(stored = stored).destinationFor(RingGesture.Hold))
    }

    @Test
    fun rerouteAllMovesEveryMatchingGesture() {
        val routing = routing(secondaryMode = SecondaryMode.Disabled)
        routing.setRoutes(
            mapOf(
                RingGesture.Hold to GestureDestination.LocalAgent,
                RingGesture.ClickHold to GestureDestination.LocalAgent,
            )
        )

        assertTrue(routing.rerouteAll(GestureDestination.LocalAgent, GestureDestination.IndexAgent))

        assertEquals(GestureDestination.IndexAgent, routing.destinationFor(RingGesture.Hold))
        assertEquals(GestureDestination.IndexAgent, routing.destinationFor(RingGesture.ClickHold))
        assertFalse(routing.rerouteAll(GestureDestination.LocalAgent, GestureDestination.IndexAgent))
    }

    @Test
    fun onlyLocalAgentIsRoutedWhenNoGestureReachesTheHybridAgent() {
        assertTrue(
            mapOf(
                RingGesture.Hold to GestureDestination.LocalAgent,
                RingGesture.ClickHold to GestureDestination.WebSearch,
            ).routesOnlyToLocalAgent()
        )
        assertFalse(
            mapOf(
                RingGesture.Hold to GestureDestination.LocalAgent,
                RingGesture.ClickHold to GestureDestination.IndexAgent,
            ).routesOnlyToLocalAgent()
        )
        assertFalse(
            mapOf(
                RingGesture.Hold to GestureDestination.LocalAgent,
                RingGesture.ClickHold to GestureDestination.RemoteAgent,
            ).routesOnlyToLocalAgent()
        )
        assertFalse(
            mapOf<RingGesture, GestureDestination>(
                RingGesture.Hold to GestureDestination.WebSearch,
            ).routesOnlyToLocalAgent()
        )
    }

    @Test
    fun storedRouteOfTheWrongKindFallsBackToMigration() {
        val stored = MapSettings("ring_gesture_route_Hold" to "play_pause")

        assertEquals(
            GestureDestination.IndexAgent,
            routing(stored = stored).destinationFor(RingGesture.Hold)
        )
    }

    @Test
    fun recordingDestinationFallsBackToIndexAgentForNonRecordingGestures() {
        val routing = routing(MusicControlMode.SingleClick)

        assertEquals(GestureDestination.IndexAgent, routing.recordingDestinationFor(null))
        assertEquals(GestureDestination.IndexAgent, routing.recordingDestinationFor(RingGesture.Click))
        assertEquals(
            GestureDestination.IndexAgent,
            routing.recordingDestinationFor(RingGesture.DoubleClick)
        )
    }

    @Test
    fun holdRoutedToNothingRunsNoAgent() {
        val routing = routing()
        routing.setRoute(RingGesture.Hold, GestureDestination.Nothing)

        assertEquals(GestureDestination.Nothing, routing.recordingDestinationFor(RingGesture.Hold))
        assertEquals(
            GestureDestination.WebSearch,
            routing.recordingDestinationFor(RingGesture.ClickHold)
        )
    }
}
