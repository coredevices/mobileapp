package coredevices.ring.agent.builtin_servlets.notes

import PlatformUiContext
import coredevices.ring.agent.integrations.ItemSource
import coredevices.ring.agent.integrations.NoteIntegration
import coredevices.ring.tasker.TaskerEndpoint
import coredevices.util.integrations.IntegrationTokenStorage
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

actual fun createTaskerNoteClient(): NoteIntegration = TaskerNoteClient()

/**
 * Routes notes to Tasker via [TaskerEndpoint]. Connecting records an opt-in flag in
 * [IntegrationTokenStorage]; Tasker reports authorized only while both opted in and installed.
 */
class TaskerNoteClient : NoteIntegration, KoinComponent {
    private val tokenStorage: IntegrationTokenStorage by inject()

    override suspend fun createNote(content: String, source: ItemSource?): String =
        TaskerEndpoint.send(content, messageType = "note")

    override suspend fun signIn(uiContext: PlatformUiContext): Boolean {
        if (!TaskerEndpoint.isInstalled()) return false
        tokenStorage.saveToken(TaskerEndpoint.TOKEN_STORAGE_KEY, "enabled")
        return true
    }

    override suspend fun unlink() {
        tokenStorage.deleteToken(TaskerEndpoint.TOKEN_STORAGE_KEY)
    }

    override suspend fun isAuthorized(): Boolean =
        TaskerEndpoint.isInstalled() && tokenStorage.getToken(TaskerEndpoint.TOKEN_STORAGE_KEY) != null
}
