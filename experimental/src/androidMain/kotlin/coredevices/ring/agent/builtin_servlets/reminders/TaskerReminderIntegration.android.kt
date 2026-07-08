package coredevices.ring.agent.builtin_servlets.reminders

import PlatformUiContext
import coredevices.ring.agent.integrations.ItemSource
import coredevices.ring.agent.integrations.ReminderIntegration
import coredevices.ring.agent.integrations.ReminderListEntry
import coredevices.ring.tasker.TaskerEndpoint
import coredevices.util.integrations.IntegrationTokenStorage
import kotlin.time.Duration
import kotlin.time.Instant
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

actual fun createTaskerReminderIntegration(): ReminderIntegration = TaskerReminderIntegration()

/**
 * Routes reminders to Tasker via [TaskerEndpoint]. The reminder text is sent as the payload with a
 * `messageType=reminder` extra, plus the optional `deadline`, `notify_before_seconds` and `list`
 * extras so the Tasker side can branch on them. Tasker has no native list concept, so
 * [searchForList] echoes the requested name back and it is forwarded verbatim as the `list` extra;
 * honouring the lead time is up to the user's Tasker profile.
 */
class TaskerReminderIntegration : ReminderIntegration, KoinComponent {
    private val tokenStorage: IntegrationTokenStorage by inject()

    override suspend fun createReminder(
        title: String,
        deadline: Instant?,
        listId: String?,
        notifyBefore: Duration?,
        source: ItemSource?,
    ): String {
        // The due date goes to Tasker as a UTC timestamp: kotlin.time.Instant.toString() renders
        // the absolute due-time in ISO-8601 UTC (e.g. 2026-06-18T16:00:00Z), regardless of local
        // timezone. The lead time (when set alongside a due date) is passed as whole seconds.
        val extras = buildMap {
            deadline?.let {
                put("deadline", it.toString())
                notifyBefore?.let { lead -> put("notify_before_seconds", lead.inWholeSeconds.toString()) }
            }
            listId?.let { put("list", it) }
        }
        return TaskerEndpoint.send(title, messageType = "reminder", extras = extras)
    }

    override suspend fun searchForList(listName: String): List<ReminderListEntry> =
        listOf(ReminderListEntry(id = listName, title = listName))

    // Mirrors TaskerNoteClient: one shared opt-in flag in token storage covers notes and reminders.
    override suspend fun signIn(uiContext: PlatformUiContext): Boolean {
        if (!TaskerEndpoint.isInstalled()) return false
        tokenStorage.saveToken(TaskerEndpoint.TOKEN_STORAGE_KEY, "enabled")
        return true
    }

    override suspend fun unlink() {
        tokenStorage.deleteToken(TaskerEndpoint.TOKEN_STORAGE_KEY)
    }

    override suspend fun isAuthorized(): Boolean =
        TaskerEndpoint.isInstalled() && tokenStorage.getToken(TaskerEndpoint.TOKEN_STORAGE_KEY) != null
}
