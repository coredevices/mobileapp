package coredevices.ring.agent.integrations

import coredevices.util.integrations.IntegrationAuthException
import coredevices.ring.api.GoogleTasksApi
import coredevices.ring.data.IntegrationDefinition
import coredevices.ring.agent.builtin_servlets.reminders.ReminderProvider
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration
import kotlin.time.Instant

class GTasksIntegration(
    private val googleTasksApi: GoogleTasksApi,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
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
        listId: String?,
        // The Google Tasks API exposes only a due date — there is no per-task notification lead
        // time — so [notifyBefore] cannot be honoured.
        notifyBefore: Duration?,
        source: ItemSource?,
    ): String {
        val token = tokenForScopes() ?: throw IntegrationAuthException("Google Tasks not authorized")
        return googleTasksApi.createTask(token, title, deadline.toDueDate(timeZone), listId).id
            ?: throw Exception("Failed to create reminder in Google Tasks")
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

    override suspend fun getAllLists(): List<ReminderListEntry> {
        val token = tokenForScopes() ?: throw IntegrationAuthException("Google Tasks not authorized")
        return googleTasksApi.getTaskLists(token).mapNotNull {
            if (it.id != null && it.title != null) {
                ReminderListEntry(it.id, it.title)
            } else {
                null
            }
        }
    }
}

/**
 * Google Tasks discards the time from `due` and keeps the date as seen in UTC, so the deadline has
 * to be resolved to the user's local date first: 11pm PDT is already tomorrow in UTC, and the task
 * would otherwise land a day late.
 */
internal fun Instant?.toDueDate(timeZone: TimeZone): LocalDate? =
    this?.toLocalDateTime(timeZone)?.date