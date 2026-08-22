package coredevices.ring.agent.builtin_servlets.notes

import PlatformUiContext
import coredevices.ring.agent.integrations.ItemSource
import coredevices.ring.agent.integrations.NoteIntegration

/**
 * Notesnook capture is Android-only (explicit intents). iOS returns a disabled stub so it is
 * filtered out of available note providers and never offered.
 */
actual fun createNotesnookNoteClient(): NoteIntegration = object : NoteIntegration {
    override suspend fun createNote(content: String, source: ItemSource?): String? = null
    override suspend fun signIn(uiContext: PlatformUiContext): Boolean = false
    override suspend fun unlink() {}
    override suspend fun isAuthorized(): Boolean = false
}
