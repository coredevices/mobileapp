package coredevices.ring.agent.builtin_servlets.notes

import co.touchlab.kermit.Logger
import coredevices.ring.agent.integrations.NoteIntegration
import coredevices.ring.agent.integrations.NotionIntegration
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
            NoteProvider.Builtin -> LocalNoteClient()
            NoteProvider.Notion -> get<NotionIntegration>()
        }
    }
}