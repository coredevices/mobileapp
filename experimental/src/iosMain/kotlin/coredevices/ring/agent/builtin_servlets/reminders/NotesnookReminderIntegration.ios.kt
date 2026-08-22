package coredevices.ring.agent.builtin_servlets.reminders

import PlatformUiContext
import coredevices.ring.agent.integrations.ItemSource
import coredevices.ring.agent.integrations.ReminderIntegration
import coredevices.ring.agent.integrations.ReminderListEntry
import kotlin.time.Duration
import kotlin.time.Instant

actual fun createNotesnookReminderIntegration(): ReminderIntegration =
    DisabledNotesnookReminderIntegration()

private class DisabledNotesnookReminderIntegration : ReminderIntegration {
    override suspend fun createReminder(
        title: String,
        deadline: Instant?,
        listId: String?,
        notifyBefore: Duration?,
        source: ItemSource?,
    ): String = error("Notesnook is Android-only")

    override suspend fun searchForList(listName: String): List<ReminderListEntry> =
        error("Notesnook is Android-only")

    override suspend fun signIn(uiContext: PlatformUiContext): Boolean = false
    override suspend fun unlink() {}
    override suspend fun isAuthorized(): Boolean = false
}
