package coredevices.ring.agent.builtin_servlets.notes

import coredevices.mcp.SessionContext
import coredevices.ring.agent.pebble_plugin.BuiltInServletPlugin
import coredevices.ring.agent.builtin_servlets.destinationHints
import coredevices.ring.agent.builtin_servlets.userMessageIfReady
import coredevices.ring.agent.integrations.providers.IntegrationProviderRepository
import coredevices.ring.agent.integrations.providers.IntegrationSupport
import io.rebble.libpebblecommon.plugin.PluginPermissions
import kotlin.uuid.Uuid

class NoteServlet(
    createNoteTool: CreateNoteTool,
    private val providers: IntegrationProviderRepository,
) : BuiltInServletPlugin(
    name = NAME,
    tools = listOf(
        createNoteTool,
    ),
    pluginUuid = PLUGIN_UUID,
    pluginName = "Notes (Index)",
    description = "Save notes to the user's Index feed or their chosen notes app.",
    callerPermission = PluginPermissions.WRITE_INDEX_NOTES,
) {
    companion object {
        const val NAME = "builtin_note"
        val PLUGIN_UUID: Uuid = Uuid.parse("00000000-0000-0000-0002-000000000003")
    }

    override suspend fun getExtraContext(sessionContext: SessionContext?): String? {
        val hints = providers.destinationHints(
            sessionContext.userMessageIfReady(), IntegrationSupport.Note, CreateNoteTool.TOOL_NAME,
        )
        return listOfNotNull(super.getExtraContext(sessionContext))
            .plus(hints)
            .joinToString("\n")
            .takeIf { it.isNotEmpty() }
    }
}
