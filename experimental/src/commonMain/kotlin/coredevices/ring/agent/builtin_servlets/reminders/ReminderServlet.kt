package coredevices.ring.agent.builtin_servlets.reminders

import co.touchlab.kermit.Logger
import coredevices.mcp.SessionContext
import coredevices.mcp.client.BuiltInMcpIntegration
import coredevices.ring.agent.builtin_servlets.notes.CreateNoteTool
import coredevices.ring.database.room.repository.ListRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.seconds

class ReminderServlet(
    private val listsRepository: ListRepository,
    private val reminderIntegrationFactory: ReminderIntegrationFactory,
): BuiltInMcpIntegration(
    name = NAME,
    tools = listOf(
        ReminderTool(),
        ListTool(),
    )
) {
    companion object {
        const val NAME = "builtin_reminder"
        private val logger = Logger.withTag("ReminderServlet")
    }

    override suspend fun getExtraContext(sessionContext: SessionContext?): String? {
        val reminderIntegration = reminderIntegrationFactory.createReminderIntegration()
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
        val words = sessionContext?.userMessageText
            .takeIf { it?.isCompleted ?: false }
            ?.await()
            ?.split(" ")
            ?.map { it.lowercase() } ?: emptyList()
        return buildString {
            super.getExtraContext(sessionContext)?.let { appendLine(it) }
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