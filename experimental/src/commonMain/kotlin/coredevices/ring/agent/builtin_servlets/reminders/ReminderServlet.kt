package coredevices.ring.agent.builtin_servlets.reminders

import coredevices.mcp.SessionContext
import coredevices.mcp.client.BuiltInMcpIntegration
import coredevices.ring.agent.builtin_servlets.notes.CreateNoteTool
import coredevices.ring.database.room.repository.ListRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class ReminderServlet(
    private val listsRepository: ListRepository
): BuiltInMcpIntegration(
    name = NAME,
    tools = listOf(
        ReminderTool(),
        ListTool(),
    )
) {
    companion object {
        const val NAME = "builtin_reminder"
    }

    override suspend fun getExtraContext(sessionContext: SessionContext?): String? {
        val lists = withContext(Dispatchers.IO) {
            listsRepository.getAllFlow().first()
        }
        val words = sessionContext?.userMessageText
            .takeIf { it?.isCompleted ?: false }
            ?.await()
            ?.split(" ")
            ?.map { it.lowercase() } ?: emptyList()
        return buildString {
            super.getExtraContext(sessionContext)?.let { appendLine(it) }
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