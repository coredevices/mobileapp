package coredevices.ring.agent.pebble_plugin

import io.rebble.libpebblecommon.connection.Plugins
import coredevices.ring.agent.integrations.ItemSource
import coredevices.ring.agent.integrations.NoteIntegration
import io.rebble.libpebblecommon.plugin.PluginCaller
import io.rebble.libpebblecommon.plugin.PluginMetadata
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Creates notes through a plugin's `create_note` action. Built by [PblPluginIntegrationProvider]. */
class PblPluginActionNoteIntegration(
    plugin: PluginMetadata,
    plugins: Plugins,
) : NoteIntegration, PblPluginActionIntegration(plugin, plugins, CATEGORY) {
    companion object {
        const val CATEGORY = "notes"
        const val ACTION_NAME = "create_note"
    }

    override suspend fun createNote(
        content: String,
        source: ItemSource?
    ): String? {
        val result = plugins.invoke(PluginCaller.Host(), plugin, ACTION_NAME, JsonObject(
            mapOf(
                "text" to JsonPrimitive(content)
            )
        ))
        return if (result.ok) {
            result.text ?: ""
        } else {
            error(result.message ?: result.code ?: "$ACTION_NAME failed")
        }
    }
}
