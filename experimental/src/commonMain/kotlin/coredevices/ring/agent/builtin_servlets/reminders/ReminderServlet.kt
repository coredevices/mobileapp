package coredevices.ring.agent.builtin_servlets.reminders

import co.touchlab.kermit.Logger
import coredevices.mcp.SessionContext
import coredevices.ring.agent.pebble_plugin.BuiltInServletPlugin
import coredevices.ring.agent.builtin_servlets.destinationHints
import coredevices.ring.agent.builtin_servlets.notes.CreateNoteTool
import coredevices.ring.agent.builtin_servlets.userMessageIfReady
import coredevices.ring.agent.integrations.providers.IntegrationProviderRepository
import coredevices.ring.agent.integrations.providers.IntegrationSupport
import coredevices.ring.database.room.repository.GeofenceLocationRepository
import coredevices.ring.database.room.repository.ListRepository
import io.rebble.libpebblecommon.plugin.PluginPermissions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

class ReminderServlet(
    private val listsRepository: ListRepository,
    private val providers: IntegrationProviderRepository,
    private val geofenceLocationRepository: GeofenceLocationRepository,
) : BuiltInServletPlugin(
    name = NAME,
    tools = listOf(
        ReminderTool(),
        LocationReminderTool(geofenceLocationRepository),
        ListTool(),
        CreateListTool(),
    ),
    pluginUuid = PLUGIN_UUID,
    pluginName = "Reminders (Index)",
    description = "Create reminders and add items to the user's lists.",
    callerPermission = PluginPermissions.WRITE_INDEX_REMINDERS,
) {
    companion object {
        const val NAME = "builtin_reminder"
        val PLUGIN_UUID: Uuid = Uuid.parse("00000000-0000-0000-0002-000000000004")
        private val logger = Logger.withTag("ReminderServlet")
    }

    // create_list and location reminders only work with the app's own reminders, so hide them
    // when an external reminder provider is selected.
    override suspend fun getDisabledTools(): List<String> = buildList {
        val builtIn = providers.selectedReminderProvider.value.storesItemsLocally
        if (!builtIn) add(CreateListTool.TOOL_NAME)
        if (!builtIn || geofenceLocationRepository.getAllFlow().first().isEmpty()) {
            add(LocationReminderTool.TOOL_NAME)
        }
    }

    override suspend fun getExtraContext(sessionContext: SessionContext?): String? {
        val reminderIntegration = providers.reminderIntegration()
        val lists = try {
            withContext(Dispatchers.IO) {
                withTimeout(5.seconds) {
                    reminderIntegration.getAllLists()
                }
            }
        } catch (e: Exception) {
            logger.w { "Failed to fetch lists from reminder integration: ${e.message}" }
            emptyList()
        }
        logger.d { "Fetched ${lists.size} lists from reminder integration" }
        val message = sessionContext.userMessageIfReady()
        val words = message?.split(" ")?.map { it.lowercase() } ?: emptyList()
        val hints = providers.destinationHints(
            message, IntegrationSupport.Reminder, ReminderTool.TOOL_NAME, ListTool.TOOL_NAME,
        )
        return buildString {
            super.getExtraContext(sessionContext)?.let { appendLine(it) }
            hints.forEach { appendLine(it) }
            if (lists.isEmpty()) {
                return@buildString
            }
            appendLine("Top available lists for ${ListTool.TOOL_NAME}:")
            lists.sortedWith { a, b ->
                val aMatch = words.any { it in a.title.lowercase() }
                val bMatch = words.any { it in b.title.lowercase() }
                when {
                    aMatch && !bMatch -> -1
                    !aMatch && bMatch -> 1
                    else -> a.title.compareTo(b.title)
                }
            }.take(30).forEach { list ->
                appendLine("- ${list.title}")
            }
            appendLine(
                "If the user's message starts with or names one of these lists, use " +
                    "${ListTool.TOOL_NAME} with that list, not ${CreateNoteTool.TOOL_NAME}. " +
                    "For example 'Work list review budget' means add 'review budget' to the " +
                    "'Work list' list."
            )
        }
    }
}