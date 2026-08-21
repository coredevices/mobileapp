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
    }

    private val _routes = MutableStateFlow(
        load(
            migratedRoutes(
                musicControlMode = prefs.musicControlMode.value,
                secondaryMode = prefs.secondaryMode.value,
                secondaryModeMcpGroupId = prefs.secondaryModeMcpGroupId.value,
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

    private fun load(defaults: Map<RingGesture, GestureDestination>): Map<RingGesture, GestureDestination> =
        RingGesture.entries.associateWith { gesture ->
            settings.getStringOrNull(KEY_PREFIX + gesture.name)
                ?.let { decodeDestination(it) }
                ?.takeIf { gesture.accepts(it) }
                ?: defaults.getValue(gesture)
        }
}

private const val SANDBOX_PREFIX = "mcp_sandbox:"

private fun GestureDestination.encode(): String = when (this) {
    GestureDestination.PlayPause -> "play_pause"
    GestureDestination.NextTrack -> "next_track"
    GestureDestination.IndexAgent -> "index_agent"
    GestureDestination.WebSearch -> "web_search"
    GestureDestination.WebhookOnly -> "webhook_only"
    GestureDestination.Nothing -> "nothing"
    is GestureDestination.McpSandbox -> SANDBOX_PREFIX + (groupId?.toString() ?: "")
}

private fun decodeDestination(raw: String): GestureDestination? = when {
    raw.startsWith(SANDBOX_PREFIX) ->
        GestureDestination.McpSandbox(raw.removePrefix(SANDBOX_PREFIX).toLongOrNull())
    raw == "play_pause" -> GestureDestination.PlayPause
    raw == "next_track" -> GestureDestination.NextTrack
    raw == "index_agent" -> GestureDestination.IndexAgent
    raw == "web_search" -> GestureDestination.WebSearch
    raw == "webhook_only" -> GestureDestination.WebhookOnly
    raw == "nothing" -> GestureDestination.Nothing
    else -> null
}
