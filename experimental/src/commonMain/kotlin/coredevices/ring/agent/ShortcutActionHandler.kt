package coredevices.ring.agent

import co.touchlab.kermit.Logger
import com.eygraber.uri.Uri
import coredevices.ring.agent.confirmation.AgentUserConfirmation
import coredevices.ring.agent.confirmation.CONFIRM_TOOL_PATH
import coredevices.ring.agent.confirmation.parseConfirmationDeepLink
import coredevices.ring.agent.integrations.UIEmailIntegration
import coredevices.ring.agent.integrations.providers.IntegrationProviderRepository
import coredevices.ring.agent.integrations.providers.supportsNotes
import coredevices.ring.agent.integrations.providers.supportsReminders
import coredevices.ring.database.room.repository.RecordingRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ShortcutActionHandler(
    private val uiEmailIntegration: UIEmailIntegration,
    private val recordingRepository: RecordingRepository,
    private val providers: IntegrationProviderRepository,
    private val agentUserConfirmation: AgentUserConfirmation,
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
                        ?.let { providers.findProvider(it) }
                        ?.takeIf { it.supportsNotes }
                        ?: return false
                    scope.launch {
                        val integration = providers.noteIntegration(provider)
                        try {
                            val entry = recordingRepository.getRecordingEntriesFlow(recordingId).first().first()
                            integration.createNote(entry.transcription ?: "<No transcription>")
                        } catch (e: Exception) {
                            logger.e(e) { "Failed to send recording $recordingId to note provider ${provider.id}" }
                        }
                    }
                    return true
                }
                "send-to-reminder-provider" -> {
                    val recordingId = uri.getQueryParameter("recordingId")?.toLongOrNull() ?: return false
                    val provider = uri.getQueryParameter("provider")
                        ?.let { providers.findProvider(it) }
                        ?.takeIf { it.supportsReminders }
                        ?: return false
                    scope.launch {
                        try {
                            val entry = recordingRepository.getRecordingEntriesFlow(recordingId).first().first()
                            providers.reminderIntegration(provider).createReminder(
                                title = entry.transcription ?: "<No transcription>",
                                deadline = null,
                            )
                        } catch (e: Exception) {
                            logger.e(e) { "Failed to send recording $recordingId to reminder provider ${provider.id}" }
                        }
                    }
                    return true
                }
                CONFIRM_TOOL_PATH -> {
                    val response = parseConfirmationDeepLink(uri) ?: return false
                    agentUserConfirmation.respond(response.recordingId, response.toolCallId, response.approved)
                    return true
                }
                else -> return false
            }
        } else {
            return false
        }
    }
}