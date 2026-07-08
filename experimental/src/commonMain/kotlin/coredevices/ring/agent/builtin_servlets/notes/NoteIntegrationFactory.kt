package coredevices.ring.agent.builtin_servlets.notes

import co.touchlab.kermit.Logger
import coredevices.ring.agent.integrations.DelegatingNoteIntegration
import coredevices.ring.agent.integrations.NoteIntegration
import coredevices.ring.agent.integrations.NotionIntegration
import coredevices.ring.agent.integrations.obsidian.ObsidianIntegration
import coredevices.ring.database.Preferences
import coredevices.firestore.UsersDao
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

class NoteIntegrationFactory(
    private val usersDao: UsersDao,
    private val prefs: Preferences
): KoinComponent {
    companion object {
        private val logger = Logger.withTag("NoteIntegrationFactory")
    }
    suspend fun createNoteClient(integration: NoteProvider = prefs.noteProvider.value): NoteIntegration {
        logger.i { "Creating note integration for provider: $integration" }
        return when (integration) {
            NoteProvider.Builtin -> LocalNoteClient(get(), get())
            NoteProvider.Notion -> delegated(get<NotionIntegration>(), integration)
            NoteProvider.Obsidian -> delegated(get<ObsidianIntegration>(), integration)
            NoteProvider.Tasker -> delegated(createTaskerNoteClient(), integration)
        }
    }

    // External integrations own the note remotely; the wrapper records a "Sent to X" feed item.
    private fun delegated(delegate: NoteIntegration, provider: NoteProvider): NoteIntegration =
        DelegatingNoteIntegration(delegate, provider.title, get())
}