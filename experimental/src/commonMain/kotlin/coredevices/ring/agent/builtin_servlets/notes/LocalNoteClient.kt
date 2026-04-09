package coredevices.ring.agent.builtin_servlets.notes

import PlatformUiContext
import coredevices.ring.agent.integrations.NoteIntegration

class LocalNoteClient: NoteIntegration {
    override suspend fun createNote(content: String): String? = "0"
    override suspend fun signIn(uiContext: PlatformUiContext): Boolean {
        TODO("Not yet implemented")
    }

    override suspend fun unlink() {
        TODO("Not yet implemented")
    }
}