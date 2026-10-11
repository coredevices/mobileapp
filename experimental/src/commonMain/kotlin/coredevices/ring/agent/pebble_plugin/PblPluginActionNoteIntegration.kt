package coredevices.ring.agent.pebble_plugin

import coredevices.ring.agent.integrations.ItemSource
import coredevices.ring.agent.integrations.NoteIntegration
import io.rebble.libpebblecommon.plugin.Plugin
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Creates notes through a plugin's `create_note` action. Built by [PblPluginIntegrationProvider]. */
class PblPluginActionNoteIntegration(plugin: Plugin) : NoteIntegration, PblPluginActionIntegration(plugin, CATEGORY) {
    companion object {
        const val CATEGORY = "notes"
        const val ACTION_NAME = "create_note"
    }

    override suspend fun createNote(
        content: String,
        source: ItemSource?
    ): String? {
        val result = plugin.invoke(ACTION_NAME, JsonObject(
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
