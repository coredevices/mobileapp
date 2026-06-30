package coredevices.ring.agent.builtin_servlets.reminders

import coredevices.ring.tasker.TaskerEndpoint
import kotlin.time.Instant

actual fun createTaskerReminder(time: Instant?, message: String): ListAssignableReminder =
    AndroidTaskerReminder(time, message)

/**
 * Routes a reminder to Tasker via [TaskerEndpoint]. The reminder text is sent as the payload with a
 * `messageType=reminder` extra, plus the optional `deadline` and (when assigned) `list` so the
 * Tasker side can branch on them. Tasker has no native list concept, so [scheduleToList] simply
 * forwards the list name as an extra.
 */
class AndroidTaskerReminder(
    override val time: Instant?,
    override val message: String,
) : ListAssignableReminder {
    private var _listTitle: String? = null
    override val listTitle: String?
        get() = _listTitle

    // The due date goes to Tasker as a UTC timestamp: kotlin.time.Instant.toString() renders the
    // absolute due-time in ISO-8601 UTC (e.g. 2026-06-18T16:00:00Z), regardless of local timezone.
    private fun deadlineExtras(): Map<String, String> =
        time?.let { mapOf("deadline" to it.toString()) } ?: emptyMap()

    override suspend fun schedule(): String =
        TaskerEndpoint.send(message, messageType = "reminder", extras = deadlineExtras())

    override suspend fun scheduleToList(listName: String): String {
        _listTitle = listName
        return TaskerEndpoint.send(
            message,
            messageType = "reminder",
            extras = deadlineExtras() + ("list" to listName),
        )
    }

    override suspend fun cancel() {
        // Fire-and-forget intent; nothing to cancel on our side.
    }
}
