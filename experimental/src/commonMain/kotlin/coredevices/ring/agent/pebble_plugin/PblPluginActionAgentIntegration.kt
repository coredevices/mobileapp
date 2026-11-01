package coredevices.ring.agent.pebble_plugin

import coredevices.ring.agent.integrations.AgentChatEntry
import coredevices.ring.agent.integrations.AgentIntegration
import coredevices.ring.agent.integrations.ItemSource
import io.rebble.libpebblecommon.connection.Plugins
import io.rebble.libpebblecommon.plugin.PluginCaller
import io.rebble.libpebblecommon.plugin.PluginMetadata
import io.rebble.libpebblecommon.plugin.SourceInstance
import io.rebble.libpebblecommon.plugin.SourceShapeNames
import io.rebble.libpebblecommon.plugin.SourceUpdate
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.time.Duration.Companion.seconds

/** Sends messages through a plugin's `send_to_agent` action. Built by [PblPluginIntegrationProvider]. */
class PblPluginActionAgentIntegration(
    plugin: PluginMetadata,
    plugins: Plugins,
) : AgentIntegration, PblPluginActionIntegration(plugin, plugins, CATEGORY) {
    companion object {
        const val CATEGORY = "agent"
        const val ACTION_NAME = "send_to_agent"
        private const val DEFAULT_CHATS_SOURCE = "$CATEGORY/chats"
        private val CHATS_TIMEOUT = 3.seconds
    }

    override suspend fun sendToAgent(message: String, chatId: String?, source: ItemSource?): String {
        val args = buildJsonObject {
            put("message", message)
            chatId?.let { put("chat_id", it) }
        }
        val result = plugins.invoke(PluginCaller.Host(), plugin, ACTION_NAME, args)
        if (!result.ok) error(result.message ?: result.code ?: "$ACTION_NAME failed")
        return result.text ?: ""
    }

    /** Chats come from the source the action targets; a plugin with no such source only has its default chat. */
    override suspend fun getAllChats(): List<AgentChatEntry> {
        val target = plugin.action(ACTION_NAME)?.targets?.firstOrNull() ?: DEFAULT_CHATS_SOURCE
        val (category, item) = target.split("/").takeIf { it.size >= 2 } ?: return emptyList()
        if (!plugin.serves(category, item)) return emptyList()
        val update = plugins.read(
            PluginCaller.Host(),
            plugin,
            category,
            item,
            properties = listOf(SourceInstance.PROPERTY_NAME),
            timeout = CHATS_TIMEOUT,
        )
        val envelope = (update as? SourceUpdate.Data)?.envelope ?: return emptyList()
        return envelope.instances.mapNotNull { instance ->
            instance.properties[SourceInstance.PROPERTY_NAME]
                ?.get(SourceShapeNames.SHORT_TEXT)
                ?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull
                ?.let { AgentChatEntry(instance.instanceId, it) }
        }
    }
}
