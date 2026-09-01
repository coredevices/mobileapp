package coredevices.ring.agent.builtin_servlets.reminders

import coredevices.ring.agent.integrations.ReminderIntegration
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * Tasker is Android-only. The Android implementation routes reminders to Tasker via an
 * `ACTION_SEND` intent; iOS returns a disabled stub so the provider never surfaces there.
 */
expect fun createTaskerReminderIntegration(): ReminderIntegration

/**
 * Intent extras describing a Tasker reminder. Each key surfaces as a Tasker variable on the
 * "Intent Received" side (`%deadline`, `%notify_before_seconds`, `%list`). The due date is a UTC
 * ISO-8601 instant; the lead time is whole seconds and only travels alongside a due date.
 */
internal fun taskerReminderExtras(
    deadline: Instant?,
    listId: String?,
    notifyBefore: Duration?,
): Map<String, String> = buildMap {
    deadline?.let {
        put("deadline", it.toString())
        notifyBefore?.let { lead -> put("notify_before_seconds", lead.inWholeSeconds.toString()) }
    }
    listId?.let { put("list", it) }
}
