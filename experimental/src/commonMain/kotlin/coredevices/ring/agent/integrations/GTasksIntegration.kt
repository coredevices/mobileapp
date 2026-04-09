package coredevices.ring.agent.integrations

import coredevices.util.integrations.IntegrationAuthException
import coredevices.ring.api.GoogleTasksApi
import coredevices.ring.data.IntegrationDefinition
import coredevices.ring.agent.builtin_servlets.reminders.ReminderProvider
import kotlin.time.Instant

class GTasksIntegration(
    private val googleTasksApi: GoogleTasksApi,
): GoogleAPIIntegration(
    scopes = GoogleTasksApi.SCOPES
), ReminderIntegration {
    companion object {
        val DEFINITION = IntegrationDefinition(
            title = "Google Tasks",
            reminder = ReminderProvider.GoogleTasks,
            notes = null
        )
    }

    override suspend fun createReminder(
        title: String,
        deadline: Instant?,
        listId: String?
    ): String? {
        val token = tokenForScopes() ?: throw IntegrationAuthException("Google Tasks not authorized")
        return googleTasksApi.createTask(token, title, deadline, listId).id
    }

    override suspend fun searchForList(listName: String): List<ReminderListEntry> {
        val token = tokenForScopes() ?: throw IntegrationAuthException("Google Tasks not authorized")
        return googleTasksApi.getTaskLists(token).filter { it.title?.contains(listName, ignoreCase = true) == true }.mapNotNull {
            if (it.id != null && it.title != null) {
                ReminderListEntry(it.id, it.title)
            } else {
                null
            }
        }
    }
}