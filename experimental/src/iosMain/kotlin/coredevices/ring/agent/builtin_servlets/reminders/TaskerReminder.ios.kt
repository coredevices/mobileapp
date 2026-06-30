package coredevices.ring.agent.builtin_servlets.reminders

import kotlin.time.Instant

/**
 * Tasker is Android-only and is never offered as a provider on iOS. Should it ever be reached
 * (e.g. a persisted or deep-linked `ReminderProvider.Tasker`), return a disabled reminder rather
 * than throwing at creation — `ReminderFactory.create()` is called outside callers' try/catch, and
 * an `Error` like `NotImplementedError` wouldn't be caught by their `catch (Exception)` anyway.
 */
actual fun createTaskerReminder(time: Instant?, message: String): ListAssignableReminder =
    DisabledTaskerReminder(time, message)

private class DisabledTaskerReminder(
    override val time: Instant?,
    override val message: String,
) : ListAssignableReminder {
    override val listTitle: String? = null
    override suspend fun schedule(): String = error("Tasker is Android-only")
    override suspend fun scheduleToList(listName: String): String = error("Tasker is Android-only")
    override suspend fun cancel() {}
}
