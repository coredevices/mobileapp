package coredevices.ring.agent.builtin_servlets.reminders

import co.touchlab.kermit.Logger
import coredevices.mcp.SessionContext
import coredevices.ring.agent.builtin_servlets.destinationHints
import coredevices.ring.agent.builtin_servlets.notes.CreateNoteTool
import coredevices.ring.agent.builtin_servlets.userMessageIfReady
import coredevices.ring.agent.integrations.providers.IntegrationProviderRepository
import coredevices.ring.agent.integrations.providers.IntegrationSupport
import coredevices.ring.agent.pebble_plugin.BuiltInServletPlugin
import io.rebble.libpebblecommon.plugin.PluginPermissions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

class ListServlet(
    private val providers: IntegrationProviderRepository,
) : BuiltInServletPlugin(
    name = NAME,
    tools = listOf(ListTool(), CreateListTool()),
    pluginUuid = PLUGIN_UUID,
    pluginName = "Lists (Index)",
    description = "Add items to the user's lists and create new lists.",
    callerPermission = PluginPermissions.WRITE_INDEX_REMINDERS,
) {
    companion object {
        const val NAME = "builtin_list"
        val PLUGIN_UUID: Uuid = Uuid.parse("00000000-0000-0000-0002-000000000005")
        private val logger = Logger.withTag("ListServlet")
    }

    // create_list only works with the app's own lists.
    override suspend fun getDisabledTools(): List<String> = buildList {
        if (!providers.selectedListProvider.value.storesItemsLocally) add(CreateListTool.TOOL_NAME)
    }

    override suspend fun getExtraContext(sessionContext: SessionContext?): String? {
        val listIntegration = providers.listIntegration()
        val lists = try {
            withContext(Dispatchers.IO) {
                withTimeout(5.seconds) {
                    listIntegration.getAllLists()
                }
            }
        } catch (e: Exception) {
            logger.w { "Failed to fetch lists from list integration: ${e.message}" }
            emptyList()
        }
        logger.d { "Fetched ${lists.size} lists from list integration" }
        val message = sessionContext.userMessageIfReady()
        val words = message?.split(" ")?.map { it.lowercase() } ?: emptyList()
        val hints = providers.destinationHints(message, IntegrationSupport.List, ListTool.TOOL_NAME)
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
