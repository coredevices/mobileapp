package coredevices.ring.service.button

import com.russhwolf.settings.Settings
import coredevices.ring.database.Preferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Per-gesture routing: every [RingGesture] has its own [GestureDestination].
 * Gestures with no stored route fall back to the equivalent of the legacy
 * MusicControlMode / SecondaryMode preferences.
 */
class GestureRoutingPreferences(
    private val settings: Settings,
    prefs: Preferences,
) {
    companion object {
        private const val KEY_PREFIX = "ring_gesture_route_"
        private const val COMMAND_KEY_PREFIX = "ring_gesture_command_"
        private const val LEGACY_LLM_MODE_KEY = "llm_mode"
        private const val LEGACY_LLM_MODE_LOCAL_ONLY = 1
        private const val LEGACY_LOCAL_LLM_SWITCH_KEY = "use_cactus_agent"
    }

    private val _routes = MutableStateFlow(
        load(
            migratedRoutes(
                musicControlMode = prefs.musicControlMode.value,
                secondaryMode = prefs.secondaryMode.value,
            )
        )
    )
    val routes = _routes.asStateFlow()

    private val _commands = MutableStateFlow(
        RingGesture.entries.associateWith { settings.getString(COMMAND_KEY_PREFIX + it.name, "") }
    )
    /** Text a gesture routed to [GestureDestination.AgentCommand] sends to the agent. */
    val commands = _commands.asStateFlow()

    fun commandFor(gesture: RingGesture): String = _commands.value[gesture].orEmpty()

    fun setCommand(gesture: RingGesture, command: String) {
        settings.putString(COMMAND_KEY_PREFIX + gesture.name, command)
        _commands.value = _commands.value + (gesture to command)
    }

    fun destinationFor(gesture: RingGesture): GestureDestination =
        _routes.value[gesture] ?: GestureDestination.Nothing

    /** Destination for a recording produced by [gesture]. Unknown sequences and gestures that
     *  don't record (null included) fall back to the Index agent, as they always have. */
    fun recordingDestinationFor(gesture: RingGesture?): GestureDestination.Recording =
        gesture?.takeIf { it.kind == GestureKind.Recording }
            ?.let { destinationFor(it) } as? GestureDestination.Recording
            ?: GestureDestination.IndexAgent

    fun setRoute(gesture: RingGesture, destination: GestureDestination) =
        setRoutes(mapOf(gesture to destination))

    fun setRoutes(routes: Map<RingGesture, GestureDestination>) {
        routes.forEach { (gesture, destination) ->
            require(gesture.accepts(destination)) { "$destination is not valid for $gesture" }
            settings.putString(KEY_PREFIX + gesture.name, destination.encode())
        }
        _routes.value = _routes.value + routes
    }

    /** Re-points every gesture routed to [from] at [to]; false when none was. */
    fun rerouteAll(from: GestureDestination, to: GestureDestination): Boolean {
        val affected = _routes.value.filterValues { it == from }.keys
        if (affected.isEmpty()) return false
        setRoutes(affected.associateWith { to })
        return true
    }

    private fun load(defaults: Map<RingGesture, GestureDestination>): Map<RingGesture, GestureDestination> {
        val routes = RingGesture.entries.associateWith { gesture ->
            settings.getStringOrNull(KEY_PREFIX + gesture.name)
                ?.let { decodeDestination(it) }
                ?.takeIf { gesture.accepts(it) }
                ?: defaults.getValue(gesture)
        }
        return migrateLegacyLlmMode(routes)
    }

    /** The removed app-wide LLM mode becomes a route on every gesture that ran the Index agent:
     *  local-only to the local agent, anything else stays on the Index agent. One-shot: the
     *  legacy keys are cleared afterwards. */
    private fun migrateLegacyLlmMode(
        routes: Map<RingGesture, GestureDestination>,
    ): Map<RingGesture, GestureDestination> {
        val legacyMode = settings.getIntOrNull(LEGACY_LLM_MODE_KEY)
            ?: LEGACY_LLM_MODE_LOCAL_ONLY.takeIf { settings.getBoolean(LEGACY_LOCAL_LLM_SWITCH_KEY, false) }
        settings.remove(LEGACY_LLM_MODE_KEY)
        settings.remove(LEGACY_LOCAL_LLM_SWITCH_KEY)
        if (legacyMode != LEGACY_LLM_MODE_LOCAL_ONLY) return routes
        return routes.mapValues { (gesture, destination) ->
            if (destination != GestureDestination.IndexAgent) return@mapValues destination
            settings.putString(KEY_PREFIX + gesture.name, GestureDestination.LocalAgent.encode())
            GestureDestination.LocalAgent
        }
    }
}

/** Routes to the removed MCP sandbox destination; they run the Index agent now. */
private const val LEGACY_SANDBOX_PREFIX = "mcp_sandbox:"
/** The removed cloud-only agent; the Index agent is cloud-first anyway. */
private const val LEGACY_REMOTE_AGENT = "remote_agent"
private const val AGENT_PLUGIN_PREFIX = "agent_plugin:"

private fun GestureDestination.encode(): String = when (this) {
    GestureDestination.PlayPause -> "play_pause"
    GestureDestination.NextTrack -> "next_track"
    GestureDestination.PreviousTrack -> "previous_track"
    GestureDestination.IndexAgent -> "index_agent"
    GestureDestination.LocalAgent -> "local_agent"
    GestureDestination.WebSearch -> "web_search"
    GestureDestination.WebhookOnly -> "webhook_only"
    is GestureDestination.AgentPlugin -> AGENT_PLUGIN_PREFIX + providerId
    GestureDestination.Webhook -> "webhook"
    GestureDestination.AgentCommand -> "agent_command"
    GestureDestination.Nothing -> "nothing"
}

private fun decodeDestination(raw: String): GestureDestination? = when {
    raw.startsWith(LEGACY_SANDBOX_PREFIX) || raw == LEGACY_REMOTE_AGENT -> GestureDestination.IndexAgent
    raw == "play_pause" -> GestureDestination.PlayPause
    raw == "next_track" -> GestureDestination.NextTrack
    raw == "previous_track" -> GestureDestination.PreviousTrack
    raw == "index_agent" -> GestureDestination.IndexAgent
    raw == "local_agent" -> GestureDestination.LocalAgent
    raw == "web_search" -> GestureDestination.WebSearch
    raw == "webhook_only" -> GestureDestination.WebhookOnly
    raw.startsWith(AGENT_PLUGIN_PREFIX) ->
        raw.removePrefix(AGENT_PLUGIN_PREFIX).takeIf { it.isNotBlank() }?.let { GestureDestination.AgentPlugin(it) }
    raw == "webhook" -> GestureDestination.Webhook
    raw == "agent_command" -> GestureDestination.AgentCommand
    raw == "nothing" -> GestureDestination.Nothing
    else -> null
}
