package coredevices.ring.agent.pebble_plugin

import PlatformUiContext
import coredevices.util.integrations.Integration
import io.rebble.libpebblecommon.plugin.Plugin
import io.rebble.libpebblecommon.plugin.SourceShapeNames
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Duration.Companion.seconds

/**
 * Writes through a plugin's action for [category]. The plugin reports whether it is signed in and
 * set up for that category through its `<category>/configured` source.
 */
abstract class PblPluginActionIntegration(
    protected val plugin: Plugin,
    private val category: String,
) : Integration {
    override suspend fun signIn(uiContext: PlatformUiContext): Boolean {
        TODO("Not yet implemented")
    }

    override suspend fun unlink() {
        TODO("Not yet implemented")
    }

    override suspend fun isAuthorized(): Boolean {
        val envelope = withTimeoutOrNull(CONFIGURED_TIMEOUT) {
            plugin.observe(category, CONFIGURED_ITEM, properties = listOf(CONFIGURED_PROPERTY), iconPixelSize = null)
                .firstOrNull()
        } ?: return false
        return envelope.instances.any { instance ->
            instance.properties[CONFIGURED_PROPERTY]
                ?.get(SourceShapeNames.BOOLEAN)
                ?.jsonObject?.get("value")?.jsonPrimitive?.booleanOrNull == true
        }
    }

    companion object {
        const val CONFIGURED_ITEM = "configured"
        const val CONFIGURED_PROPERTY = "configured"
        private val CONFIGURED_TIMEOUT = 3.seconds
    }
}
