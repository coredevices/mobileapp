package coredevices.ring.service.recordings.button

import co.touchlab.kermit.Logger
import coredevices.indexai.data.entity.RecordingEntryErrorType
import coredevices.indexai.data.entity.RecordingEntryStatus
import coredevices.indexai.database.dao.RecordingEntryDao
import coredevices.mcp.data.SemanticResult
import coredevices.mcp.data.ToolCallResult
import coredevices.ring.agent.integrations.ItemSource
import coredevices.ring.agent.integrations.providers.IntegrationProvider
import coredevices.ring.agent.integrations.providers.IntegrationProviderRepository
import coredevices.ring.database.room.repository.RecordingRepository
import coredevices.ring.service.recordings.RecordingProcessor
import coredevices.ring.util.trace.RingTraceSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.time.Clock
import kotlin.uuid.Uuid

/** Delivers a recording's text to an agent plugin and records the outcome on its entry; no Index agent runs. */
class AgentPluginDelivery(
    private val provider: IntegrationProvider,
    private val providers: IntegrationProviderRepository,
) : KoinComponent {
    companion object {
        private val logger = Logger.withTag("AgentPluginDelivery")
    }

    private val recordingEntryDao: RecordingEntryDao by inject()
    private val recordingProcessor: RecordingProcessor by inject()
    private val recordingRepository: RecordingRepository by inject()

    suspend fun deliver(recordingId: Long, entryId: Long, text: String) {
        recordingEntryDao.updateRecordingEntryStatus(entryId, status = RecordingEntryStatus.agent_processing)
        try {
            val recording = withContext(Dispatchers.IO) { recordingRepository.getRecording(recordingId) }
            val callId = Uuid.random().toString()
            val source = ItemSource(recording?.firestoreId, recording?.localTimestamp ?: Clock.System.now(), callId)
            val ack = providers.agentIntegration(provider).sendToAgent(text, source = source)
            recordingProcessor.recordDirectResult(
                recordingId = recordingId,
                recordingEntryId = entryId,
                text = text,
                toolCallId = callId,
                result = ToolCallResult(ack, SemanticResult.ListItemCreation(text, destination = provider.title)),
            )
            recordingEntryDao.updateRecordingEntryStatus(entryId, status = RecordingEntryStatus.completed)
        } catch (e: Exception) {
            logger.e(e) { "Failed to send recording to ${provider.title}" }
            recordingEntryDao.updateRecordingEntryStatus(
                entryId,
                status = RecordingEntryStatus.agent_error,
                error = e.message,
                errorType = RecordingEntryErrorType.agent_failed,
            )
            throw e
        }
    }
}

/** Transcribes a ring or phone-mic recording and sends the transcript to an agent plugin. */
class SendToAgentRecordingOperation(
    private val delivery: AgentPluginDelivery,
    recordingId: Long,
    transferId: Long?,
    fileId: String,
    trace: RingTraceSession,
) : TranscribedRecordingOperation(recordingId, transferId, fileId, trace) {
    override suspend fun processTranscription(scope: CoroutineScope, entryId: Long, text: String) =
        delivery.deliver(recordingId, entryId, text)
}

/** Sends typed text to an agent plugin. */
class SendToAgentTextOperation(
    private val delivery: AgentPluginDelivery,
    recordingId: Long,
    text: String,
) : TextRecordingOperation(recordingId, text) {
    override suspend fun processText(scope: CoroutineScope, entryId: Long) =
        delivery.deliver(recordingId, entryId, text)
}
