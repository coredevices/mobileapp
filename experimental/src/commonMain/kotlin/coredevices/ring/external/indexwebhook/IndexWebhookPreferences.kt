package coredevices.ring.external.indexwebhook

import com.russhwolf.settings.Settings
import coredevices.ring.service.button.GestureDestination
import coredevices.ring.service.button.GestureKind
import coredevices.ring.service.button.RingGesture
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Payload mode controls what data is sent to the webhook endpoint.
 */
enum class IndexWebhookPayloadMode(val id: Int) {
    RecordingOnly(0),
    TranscriptionOnly(1),
    Both(2);

    companion object {
        fun fromId(id: Int): IndexWebhookPayloadMode =
            entries.firstOrNull { it.id == id } ?: RecordingOnly
    }
}

/** Webhook configuration for a single recording gesture. */
@Serializable
data class IndexWebhookConfig(
    val url: String? = null,
    val payloadMode: IndexWebhookPayloadMode = IndexWebhookPayloadMode.RecordingOnly,
    val headers: Map<String, String> = emptyMap(),
    val saved: Boolean = false,
) {
    val isActive: Boolean get() = saved && !url.isNullOrBlank()
}

/**
 * A recording gesture sends to its webhook when it is routed straight there
 * ([GestureDestination.WebhookOnly]) or when a saved config sits alongside another
 * destination. A gesture routed to [GestureDestination.Nothing] never sends.
 */
fun IndexWebhookConfig.sendsFor(destination: GestureDestination.Recording): Boolean =
    isActive && destination != GestureDestination.Nothing

/**
 * Stores one [IndexWebhookConfig] per recording gesture.
 */
class IndexWebhookPreferences(private val settings: Settings) {

    companion object {
        private const val CONFIG_KEY_PREFIX = "index_webhook_config_"

        // Legacy single-webhook keys, migrated into per-gesture configs on first load.
        private const val LEGACY_URL_KEY = "index_webhook_url"
        private const val LEGACY_TOKEN_KEY = "index_webhook_auth_token"
        private const val LEGACY_HEADERS_KEY = "index_webhook_headers"
        private const val LEGACY_PAYLOAD_MODE_KEY = "index_webhook_payload_mode"
        private const val LEGACY_TRIGGER_KEY = "index_webhook_trigger"

        // Header name the auth token used to be hardcoded to before headers were user-settable.
        private const val LEGACY_TOKEN_HEADER = "X-Widget-Token"

        internal val TRIGGER_KEYED_NAMES = mapOf(
            "SingleClickHold" to RingGesture.Hold,
            "DoubleClickHold" to RingGesture.ClickHold,
        )

        val gestures = RingGesture.entries.filter { it.kind == GestureKind.Recording }

        private val json = Json { ignoreUnknownKeys = true }
        private val legacyHeadersSerializer = MapSerializer(String.serializer(), String.serializer())
    }

    private val _configs = MutableStateFlow(migrateAndLoad())
    val configs = _configs.asStateFlow()

    fun configFor(gesture: RingGesture): IndexWebhookConfig =
        _configs.value[gesture] ?: IndexWebhookConfig()

    fun setConfig(gesture: RingGesture, config: IndexWebhookConfig) {
        settings.putString(configKey(gesture), json.encodeToString(config))
        _configs.value = _configs.value + (gesture to config)
    }

    fun clear(gesture: RingGesture) {
        settings.remove(configKey(gesture))
        _configs.value = _configs.value + (gesture to IndexWebhookConfig())
    }

    /** Toggles sending without losing the stored url/headers, so re-enabling restores them. */
    fun setEnabled(gesture: RingGesture, enabled: Boolean) {
        setConfig(gesture, configFor(gesture).copy(saved = enabled))
    }

    private fun configKey(gesture: RingGesture) = CONFIG_KEY_PREFIX + gesture.name

    private fun migrateAndLoad(): Map<RingGesture, IndexWebhookConfig> {
        migrateLegacySingleConfig()
        migrateTriggerKeyedConfigs()
        return gestures.associateWith { gesture ->
            settings.getStringOrNull(configKey(gesture))
                ?.let {
                    try {
                        json.decodeFromString<IndexWebhookConfig>(it)
                    } catch (_: Exception) {
                        null
                    }
                }
                ?: IndexWebhookConfig()
        }
    }

    /** Configs were briefly keyed by the webhook's own trigger enum; re-key them onto the gesture. */
    private fun migrateTriggerKeyedConfigs() {
        TRIGGER_KEYED_NAMES.forEach { (triggerName, gesture) ->
            val stored = settings.getStringOrNull(CONFIG_KEY_PREFIX + triggerName) ?: return@forEach
            if (settings.getStringOrNull(configKey(gesture)) == null) {
                settings.putString(configKey(gesture), stored)
            }
            settings.remove(CONFIG_KEY_PREFIX + triggerName)
        }
    }

    /** The single legacy webhook applies to every recording gesture, whatever its old trigger was. */
    private fun migrateLegacySingleConfig() {
        val url = settings.getStringOrNull(LEGACY_URL_KEY)
        if (url != null) {
            val config = IndexWebhookConfig(
                url = url,
                payloadMode = IndexWebhookPayloadMode.fromId(
                    settings.getInt(LEGACY_PAYLOAD_MODE_KEY, IndexWebhookPayloadMode.RecordingOnly.id)
                ),
                headers = legacyHeaders(),
                saved = true,
            )
            gestures.forEach { settings.putString(configKey(it), json.encodeToString(config)) }
        }
        listOf(
            LEGACY_URL_KEY,
            LEGACY_TOKEN_KEY,
            LEGACY_HEADERS_KEY,
            LEGACY_PAYLOAD_MODE_KEY,
            LEGACY_TRIGGER_KEY,
        ).forEach { settings.remove(it) }
    }

    private fun legacyHeaders(): Map<String, String> {
        val raw = settings.getStringOrNull(LEGACY_HEADERS_KEY)
        if (raw != null) {
            try {
                return json.decodeFromString(legacyHeadersSerializer, raw)
            } catch (_: Exception) {
                // fall through to the even older single-token key
            }
        }
        val token = settings.getStringOrNull(LEGACY_TOKEN_KEY)?.takeIf { it.isNotBlank() }
        return token?.let { mapOf(LEGACY_TOKEN_HEADER to it) } ?: emptyMap()
    }
}
