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
        private const val LEGACY_LLM_MODE_KEY = "llm_mode"
        private const val LEGACY_LLM_MODE_REMOTE_ONLY = 0
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
     *  local-only to the local agent, an explicit cloud-only to the remote agent. One-shot: the
     *  legacy keys are cleared afterwards. */
    private fun migrateLegacyLlmMode(
        routes: Map<RingGesture, GestureDestination>,
    ): Map<RingGesture, GestureDestination> {
        val legacyMode = settings.getIntOrNull(LEGACY_LLM_MODE_KEY)
            ?: LEGACY_LLM_MODE_LOCAL_ONLY.takeIf { settings.getBoolean(LEGACY_LOCAL_LLM_SWITCH_KEY, false) }
        settings.remove(LEGACY_LLM_MODE_KEY)
        settings.remove(LEGACY_LOCAL_LLM_SWITCH_KEY)
        val replacement = when (legacyMode) {
            LEGACY_LLM_MODE_LOCAL_ONLY -> GestureDestination.LocalAgent
            LEGACY_LLM_MODE_REMOTE_ONLY -> GestureDestination.RemoteAgent
            else -> return routes
        }
        return routes.mapValues { (gesture, destination) ->
            if (destination != GestureDestination.IndexAgent) return@mapValues destination
            settings.putString(KEY_PREFIX + gesture.name, replacement.encode())
            replacement
        }
    }
}

/** Routes to the removed MCP sandbox destination; they run the Index agent now. */
private const val LEGACY_SANDBOX_PREFIX = "mcp_sandbox:"

private fun GestureDestination.encode(): String = when (this) {
    GestureDestination.PlayPause -> "play_pause"
    GestureDestination.NextTrack -> "next_track"
    GestureDestination.IndexAgent -> "index_agent"
    GestureDestination.LocalAgent -> "local_agent"
    GestureDestination.RemoteAgent -> "remote_agent"
    GestureDestination.WebSearch -> "web_search"
    GestureDestination.WebhookOnly -> "webhook_only"
    GestureDestination.CopyToClipboard -> "copy_to_clipboard"
    GestureDestination.Nothing -> "nothing"
}

private fun decodeDestination(raw: String): GestureDestination? = when {
    raw.startsWith(LEGACY_SANDBOX_PREFIX) -> GestureDestination.IndexAgent
    raw == "play_pause" -> GestureDestination.PlayPause
    raw == "next_track" -> GestureDestination.NextTrack
    raw == "index_agent" -> GestureDestination.IndexAgent
    raw == "local_agent" -> GestureDestination.LocalAgent
    raw == "remote_agent" -> GestureDestination.RemoteAgent
    raw == "web_search" -> GestureDestination.WebSearch
    raw == "webhook_only" -> GestureDestination.WebhookOnly
    raw == "copy_to_clipboard" -> GestureDestination.CopyToClipboard
    raw == "nothing" -> GestureDestination.Nothing
    else -> null
}
