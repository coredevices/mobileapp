package coredevices.ring.agent.builtin_servlets.reminders

import co.touchlab.kermit.Logger
import coredevices.ring.agent.integrations.DelegatingReminderIntegration
import coredevices.ring.agent.integrations.GTasksIntegration
import coredevices.ring.agent.integrations.ReminderIntegration
import coredevices.ring.database.Preferences
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

class ReminderIntegrationFactory(
    private val preferences: Preferences,
) : KoinComponent {
    companion object {
        private val logger = Logger.withTag("ReminderIntegrationFactory")
    }

    fun createReminderIntegration(
        provider: ReminderProvider = preferences.reminderProvider.value,
    ): ReminderIntegration {
        logger.i { "Creating reminder integration for provider: $provider" }
        return when (provider) {
            ReminderProvider.BuiltIn -> get<BuiltInReminderIntegration>()
            ReminderProvider.GoogleTasks -> delegated(get<GTasksIntegration>(), provider)
            ReminderProvider.IOSReminders -> delegated(createRemindersAppIntegration(), provider)
            ReminderProvider.Tasker -> delegated(createTaskerReminderIntegration(), provider)
        }
    }

    // External integrations own the reminder remotely; the wrapper records a "Sent to X" feed item.
    private fun delegated(delegate: ReminderIntegration, provider: ReminderProvider): ReminderIntegration =
        DelegatingReminderIntegration(delegate, provider.title, get())
}
