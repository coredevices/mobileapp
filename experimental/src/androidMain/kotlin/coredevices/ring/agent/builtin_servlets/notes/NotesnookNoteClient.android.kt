package coredevices.ring.agent.builtin_servlets.notes

import PlatformUiContext
import coredevices.ring.agent.integrations.ItemSource
import coredevices.ring.agent.integrations.NoteIntegration
import coredevices.ring.external.indexlocal.IndexLocalCaptureApi
import coredevices.ring.service.button.RingGesture
import coredevices.util.integrations.IntegrationTokenStorage
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.time.Clock

actual fun createNotesnookNoteClient(): NoteIntegration = NotesnookNoteClient()

/**
 * Routes notes to the Notesnook app on this phone via an explicit capture intent.
 * Connecting records an opt-in flag; authorized only while opted in and installed.
 * Agent-created notes send transcription only; ring recordings attach audio through
 * [coredevices.ring.external.indexlocal.IndexLocalCaptureRecordingOperation].
 */
class NotesnookNoteClient : NoteIntegration, KoinComponent {
    private val tokenStorage: IntegrationTokenStorage by inject()
    private val api: IndexLocalCaptureApi by inject()

    override suspend fun createNote(content: String, source: ItemSource?): String {
        val recordingId = source?.recordingFirestoreId
            ?: source?.toolCallId
            ?: "note-${Clock.System.now().toEpochMilliseconds()}"
        if (api.isCapturingRecording()) {
            // The recording decorator sends one note with audio + transcript.
            return recordingId
        }
        api.deliverIfEnabled(
            samples = null,
            sampleRate = 16000,
            recordingId = recordingId,
            transcription = content,
            recordedAt = source?.createdAt ?: Clock.System.now(),
            gesture = RingGesture.Hold,
        )
        return recordingId
    }

    override suspend fun signIn(uiContext: PlatformUiContext): Boolean {
        if (!api.isNotesnookInstalled()) return false
        tokenStorage.saveToken(NOTESNOOK_TOKEN_STORAGE_KEY, "enabled")
        return true
    }

    override suspend fun unlink() {
        tokenStorage.deleteToken(NOTESNOOK_TOKEN_STORAGE_KEY)
    }

    override suspend fun isAuthorized(): Boolean =
        api.isNotesnookInstalled() && tokenStorage.getToken(NOTESNOOK_TOKEN_STORAGE_KEY) != null
}
