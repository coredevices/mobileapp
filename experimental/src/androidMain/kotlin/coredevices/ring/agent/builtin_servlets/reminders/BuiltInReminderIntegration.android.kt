package coredevices.ring.agent.builtin_servlets.reminders

import PlatformUiContext
import coredevices.ring.agent.integrations.ItemSource
import coredevices.ring.agent.integrations.ReminderIntegration
import coredevices.ring.agent.integrations.ReminderListEntry
import kotlin.time.Duration
import kotlin.time.Instant

actual fun createBuiltInReminderIntegration(): BuiltInReminderIntegration =
    AndroidBuiltInReminderIntegration()

actual fun createRemindersAppIntegration(): ReminderIntegration = DisabledRemindersAppIntegration()

/**
 * The Reminders app is iOS-only and is never offered as a provider on Android. Should it ever be
 * reached (e.g. a persisted or deep-linked `ReminderProvider.IOSReminders`), the stub reports
 * unauthorized and throws an `Exception` from its operations so callers' `catch (Exception)`
 * failure handling applies.
 */
private class DisabledRemindersAppIntegration : ReminderIntegration {
    override suspend fun createReminder(
        title: String,
        deadline: Instant?,
        listId: String?,
        notifyBefore: Duration?,
        source: ItemSource?,
    ): String = error("iPhone Reminders is iOS-only")

    override suspend fun searchForList(listName: String): List<ReminderListEntry> =
        error("iPhone Reminders is iOS-only")

    override suspend fun signIn(uiContext: PlatformUiContext): Boolean = false
    override suspend fun unlink() {}
    override suspend fun isAuthorized(): Boolean = false
}
