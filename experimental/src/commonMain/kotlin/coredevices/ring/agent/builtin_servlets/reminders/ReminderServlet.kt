package coredevices.ring.agent.builtin_servlets.reminders

import coredevices.mcp.SessionContext
import coredevices.ring.agent.pebble_plugin.BuiltInServletPlugin
import coredevices.ring.agent.builtin_servlets.destinationHints
import coredevices.ring.agent.builtin_servlets.userMessageIfReady
import coredevices.ring.agent.integrations.providers.IntegrationProviderRepository
import coredevices.ring.agent.integrations.providers.IntegrationSupport
import coredevices.ring.database.room.repository.GeofenceLocationRepository
import io.rebble.libpebblecommon.plugin.PluginPermissions
import kotlinx.coroutines.flow.first
import kotlin.uuid.Uuid

class ReminderServlet(
    private val providers: IntegrationProviderRepository,
    private val geofenceLocationRepository: GeofenceLocationRepository,
) : BuiltInServletPlugin(
    name = NAME,
    tools = listOf(
        ReminderTool(),
        LocationReminderTool(geofenceLocationRepository),
    ),
    pluginUuid = PLUGIN_UUID,
    pluginName = "Reminders (Index)",
    description = "Create reminders for the user.",
    callerPermission = PluginPermissions.WRITE_INDEX_REMINDERS,
) {
    companion object {
        const val NAME = "builtin_reminder"
        val PLUGIN_UUID: Uuid = Uuid.parse("00000000-0000-0000-0002-000000000004")
    }

    // Location reminders only work with the app's own reminders, so hide them when an external
    // reminder provider is selected.
    override suspend fun getDisabledTools(): List<String> = buildList {
        val builtIn = providers.selectedReminderProvider.value.storesItemsLocally
        if (!builtIn || geofenceLocationRepository.getAllFlow().first().isEmpty()) {
            add(LocationReminderTool.TOOL_NAME)
        }
    }

    override suspend fun getExtraContext(sessionContext: SessionContext?): String? {
        val hints = providers.destinationHints(
            sessionContext.userMessageIfReady(), IntegrationSupport.Reminder, ReminderTool.TOOL_NAME,
        )
        return (listOfNotNull(super.getExtraContext(sessionContext)) + hints).joinToString("\n")
    }
}
