package coredevices.ring.external.indexlocal

import com.russhwolf.settings.Settings
import coredevices.ring.external.indexwebhook.IndexWebhookPayloadMode
import coredevices.ring.service.button.GestureDestination
import coredevices.ring.service.button.GestureKind
import coredevices.ring.service.button.RingGesture
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Per-gesture config for on-device app delivery (Notesnook, etc.). */
@Serializable
data class IndexLocalAppConfig(
    val notesnookEnabled: Boolean = false,
    val payloadMode: IndexWebhookPayloadMode = IndexWebhookPayloadMode.Both,
) {
    val isActive: Boolean get() = notesnookEnabled
}

/**
 * A recording gesture sends to local apps when the config is active and the
 * gesture is not routed to [GestureDestination.Nothing].
 */
fun IndexLocalAppConfig.sendsFor(destination: GestureDestination.Recording): Boolean =
    isActive && destination != GestureDestination.Nothing

/**
 * Stores one [IndexLocalAppConfig] per recording gesture. Local capture is a
 * sidecar (like the webhook), not a gesture destination of its own.
 */
class IndexLocalAppPreferences(private val settings: Settings) {

    companion object {
        private const val CONFIG_KEY_PREFIX = "index_local_app_config_"
        val gestures = RingGesture.entries.filter { it.kind == GestureKind.Recording }
        private val json = Json { ignoreUnknownKeys = true }
    }

    private val _configs = MutableStateFlow(load())
    val configs = _configs.asStateFlow()

    fun configFor(gesture: RingGesture): IndexLocalAppConfig =
        _configs.value[gesture] ?: IndexLocalAppConfig()

    fun setConfig(gesture: RingGesture, config: IndexLocalAppConfig) {
        settings.putString(configKey(gesture), json.encodeToString(config))
        _configs.value = _configs.value + (gesture to config)
    }

    fun setNotesnookEnabled(gesture: RingGesture, enabled: Boolean) {
        setConfig(gesture, configFor(gesture).copy(notesnookEnabled = enabled))
    }

    fun setPayloadMode(gesture: RingGesture, mode: IndexWebhookPayloadMode) {
        setConfig(gesture, configFor(gesture).copy(payloadMode = mode))
    }

    fun anyEnabled(): Boolean = _configs.value.values.any { it.isActive }

    fun setPayloadModeForAll(mode: IndexWebhookPayloadMode) {
        gestures.forEach { setPayloadMode(it, mode) }
    }

    private fun configKey(gesture: RingGesture) = CONFIG_KEY_PREFIX + gesture.name

    private fun load(): Map<RingGesture, IndexLocalAppConfig> =
        gestures.associateWith { gesture ->
            settings.getStringOrNull(configKey(gesture))
                ?.let {
                    try {
                        json.decodeFromString<IndexLocalAppConfig>(it)
                    } catch (_: Exception) {
                        null
                    }
                }
                ?: IndexLocalAppConfig()
        }
}
