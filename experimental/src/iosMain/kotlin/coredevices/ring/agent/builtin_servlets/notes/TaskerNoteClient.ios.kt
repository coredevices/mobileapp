package coredevices.ring.agent.builtin_servlets.notes

import PlatformUiContext
import coredevices.ring.agent.integrations.ItemSource
import coredevices.ring.agent.integrations.NoteIntegration

/**
 * Tasker is Android-only. iOS returns a disabled stub: it reports itself unauthorized, so it is
 * filtered out of the available note providers and never offered to the user.
 */
actual fun createTaskerNoteClient(): NoteIntegration = object : NoteIntegration {
    override suspend fun createNote(content: String, source: ItemSource?): String? = null
    override suspend fun signIn(uiContext: PlatformUiContext): Boolean = false
    override suspend fun unlink() {}
    override suspend fun isAuthorized(): Boolean = false
}
