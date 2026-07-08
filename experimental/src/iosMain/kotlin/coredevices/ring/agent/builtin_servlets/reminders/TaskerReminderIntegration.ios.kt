package coredevices.ring.agent.builtin_servlets.reminders

import PlatformUiContext
import coredevices.ring.agent.integrations.ItemSource
import coredevices.ring.agent.integrations.ReminderIntegration
import coredevices.ring.agent.integrations.ReminderListEntry
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * Tasker is Android-only and is never offered as a provider on iOS. Should it ever be reached
 * (e.g. a persisted or deep-linked `ReminderProvider.Tasker`), the stub reports unauthorized and
 * throws an `Exception` (not an `Error`) from its operations so callers' `catch (Exception)`
 * failure handling applies.
 */
actual fun createTaskerReminderIntegration(): ReminderIntegration = DisabledTaskerReminderIntegration()

private class DisabledTaskerReminderIntegration : ReminderIntegration {
    override suspend fun createReminder(
        title: String,
        deadline: Instant?,
        listId: String?,
        notifyBefore: Duration?,
        source: ItemSource?,
    ): String = error("Tasker is Android-only")

    override suspend fun searchForList(listName: String): List<ReminderListEntry> =
        error("Tasker is Android-only")

    override suspend fun signIn(uiContext: PlatformUiContext): Boolean = false
    override suspend fun unlink() {}
    override suspend fun isAuthorized(): Boolean = false
}
