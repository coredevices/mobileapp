package coredevices.ring.agent.integrations.providers

import coredevices.ring.agent.builtin_servlets.notes.LocalNoteClient
import coredevices.ring.agent.builtin_servlets.notes.createTaskerNoteClient
import coredevices.ring.agent.builtin_servlets.reminders.BuiltInReminderIntegration
import coredevices.ring.agent.builtin_servlets.reminders.createRemindersAppIntegration
import coredevices.ring.agent.builtin_servlets.reminders.createTaskerReminderIntegration
import coredevices.ring.agent.integrations.GTasksIntegration
import coredevices.ring.agent.integrations.NoteIntegration
import coredevices.ring.agent.integrations.NotionIntegration
import coredevices.ring.agent.integrations.ReminderIntegration
import coredevices.ring.agent.integrations.obsidian.ObsidianIntegration
import coredevices.ring.agent.integrations.providers.IntegrationSupport.Note
import coredevices.ring.agent.integrations.providers.IntegrationSupport.Reminder
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

/**
 * The providers that ship with the app. To add one, implement [IntegrationProvider] here and add
 * it to [all]; plugin providers are discovered at runtime by [IntegrationProviderRepository].
 */
object BuiltInIntegrationProviders {
    val all: List<IntegrationProvider>
        get() = listOf(Index, Notion, Obsidian, GoogleTasks, IosReminders, Tasker)

    object Index : IntegrationProvider, KoinComponent {
        override val id = "index"
        override val title = "Built-in"
        override val notesTitle = "Index Notes"
        override val remindersTitle = "Index Reminders"
        override val supports = setOf(Note, Reminder)
        override val storesItemsLocally = true
        override suspend fun isAuthorized() = true
        override fun notes(): NoteIntegration = get<LocalNoteClient>()
        override fun reminders(): ReminderIntegration = get<BuiltInReminderIntegration>()
    }

    object Notion : IntegrationProvider, KoinComponent {
        override val id = "notion"
        override val title = "Notion"
        override val supports = setOf(Note)
        override fun notes(): NoteIntegration = get<NotionIntegration>()
    }

    object Obsidian : IntegrationProvider, KoinComponent {
        override val id = "obsidian"
        override val title = "Obsidian"
        override val supports = setOf(Note)
        override fun notes(): NoteIntegration = get<ObsidianIntegration>()
    }

    object GoogleTasks : IntegrationProvider, KoinComponent {
        override val id = "google_tasks"
        override val title = "Google Tasks"
        override val supports = setOf(Reminder)
        override fun reminders(): ReminderIntegration = get<GTasksIntegration>()
    }

    object IosReminders : IntegrationProvider {
        override val id = "ios_reminders"
        override val title = "iPhone Reminders"
        override val supports = setOf(Reminder)
        override val platforms = setOf(SupportedPlatform.Ios)
        override suspend fun isAuthorized() = true
        override fun reminders(): ReminderIntegration = createRemindersAppIntegration()
    }

    object Tasker : IntegrationProvider {
        override val id = "tasker"
        override val title = "Tasker"
        override val supports = setOf(Note, Reminder)
        override val platforms = setOf(SupportedPlatform.Android)
        override fun notes(): NoteIntegration = createTaskerNoteClient()
        override fun reminders(): ReminderIntegration = createTaskerReminderIntegration()
    }
}
