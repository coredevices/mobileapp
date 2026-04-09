package coredevices.ring.agent

import co.touchlab.kermit.Logger
import com.eygraber.uri.Uri
import coredevices.ring.agent.builtin_servlets.notes.NoteIntegrationFactory
import coredevices.ring.agent.builtin_servlets.notes.NoteProvider
import coredevices.ring.agent.builtin_servlets.reminders.ReminderFactory
import coredevices.ring.agent.builtin_servlets.reminders.ReminderProvider
import coredevices.ring.agent.integrations.UIEmailIntegration
import coredevices.ring.database.room.repository.RecordingRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ShortcutActionHandler(
    private val uiEmailIntegration: UIEmailIntegration,
    private val recordingRepository: RecordingRepository,
    private val noteIntegrationFactory: NoteIntegrationFactory,
    private val reminderIntegrationFactory: ReminderFactory
) {
    private val scope = CoroutineScope(Dispatchers.Default)
    companion object {
        private val logger = Logger.withTag("ShortcutActionHandler")
    }
    fun handleDeepLink(uri: Uri): Boolean {
        logger.d { "Received deep link: $uri" }
        if (uri.scheme == "pebblecore" && uri.host == "index-link") {
            when (uri.pathSegments.firstOrNull()) {
                "send-to-me" -> {
                    val recordingId = uri.getQueryParameter("recordingId")?.toLongOrNull() ?: return false
                    scope.launch {
                        try {
                            val entry = recordingRepository.getRecordingEntriesFlow(recordingId).first().first()
                            uiEmailIntegration.createNote(entry.transcription ?: "<No transcription>")
                        } catch (e: Exception) {
                            logger.e(e) { "Failed to send recording $recordingId to email" }
                        }
                    }
                    return true
                }
                "send-to-note-provider" -> {
                    val recordingId = uri.getQueryParameter("recordingId")?.toLongOrNull() ?: return false
                    val provider = uri.getQueryParameter("provider")
                        ?.toIntOrNull()
                        ?.let { NoteProvider.fromId(it) }
                        ?: return false
                    scope.launch {
                        val integration = noteIntegrationFactory.createNoteClient(provider)
                        try {
                            val entry = recordingRepository.getRecordingEntriesFlow(recordingId).first().first()
                            integration.createNote(entry.transcription ?: "<No transcription>")
                        } catch (e: Exception) {
                            logger.e(e) { "Failed to send recording $recordingId to note provider ${provider.name}" }
                        }
                    }
                    return true
                }
                "send-to-reminder-provider" -> {
                    val recordingId = uri.getQueryParameter("recordingId")?.toLongOrNull() ?: return false
                    val provider = uri.getQueryParameter("provider")
                        ?.toIntOrNull()
                        ?.let { ReminderProvider.fromId(it) }
                        ?: return false
                    scope.launch {
                        try {
                            val entry = recordingRepository.getRecordingEntriesFlow(recordingId).first().first()
                            val reminder = reminderIntegrationFactory.create(
                                time = null,
                                message = entry.transcription ?: "<No transcription>",
                                integration = provider,
                            )
                            reminder.schedule()
                        } catch (e: Exception) {
                            logger.e(e) { "Failed to send recording $recordingId to reminder provider $provider" }
                        }
                    }
                    return true
                }
                else -> return false
            }
        } else {
            return false
        }
    }
}