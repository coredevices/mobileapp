package coredevices.ring.service.recordings.button

import coredevices.indexai.data.entity.RecordingEntryErrorType
import coredevices.indexai.data.entity.RecordingEntryStatus
import coredevices.indexai.database.dao.ConversationMessageDao
import coredevices.indexai.database.dao.RecordingEntryDao
import coredevices.ring.agent.AgentFactory
import coredevices.ring.agent.ChatMode
import coredevices.ring.agent.McpSessionFactory
import coredevices.ring.database.room.repository.RecordingRepository
import coredevices.ring.service.recordings.RecordingProcessingQueue
import coredevices.ring.service.recordings.RecordingProcessor
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.time.Duration.Companion.seconds

/**
 * Picks a recording's conversation back up once the user has answered a tool call the agent
 * paused on: the call runs (or its denial is recorded) and the agent continues from there.
 */
class ConversationResumeOperation(
    private val recordingId: Long,
    private val toolCallId: String,
    private val approved: Boolean,
    private val chatMode: ChatMode,
    private val agentFactory: AgentFactory,
    private val mcpSessionFactory: McpSessionFactory,
) : RecordingOperation, KoinComponent {
    private val recordingProcessor: RecordingProcessor by inject()
    private val recordingEntryDao: RecordingEntryDao by inject()
    private val conversationMessageDao: ConversationMessageDao by inject()
    private val recordingRepository: RecordingRepository by inject()

    override suspend fun run(handle: RecordingProcessingQueue.TaskHandle?) {
        val entry = recordingRepository.getRecordingEntriesFlow(recordingId).first().maxByOrNull { it.id }
            ?: error("Recording $recordingId has no entry to resume")
        recordingEntryDao.updateRecordingEntryStatus(entry.id, status = RecordingEntryStatus.agent_processing)
        try {
            coroutineScope {
                val conversation = conversationMessageDao.getMessagesForRecording(recordingId).first().map { it.document }
                val agent = agentFactory.createForChatMode(chatMode, conversation)
                val mcpSession = mcpSessionFactory.create(this, lazyToolLoading = agent.isCloud)
                try {
                    mcpSession.openSession()
                    recordingProcessor.resumeAfterConfirmation(
                        recordingId = recordingId,
                        recordingEntryId = entry.id,
                        mcpSession = mcpSession,
                        agent = agent,
                        toolCallId = toolCallId,
                        approved = approved,
                    )
                } finally {
                    withTimeout(3.seconds) { mcpSession.closeSession() }
                }
            }
            recordingEntryDao.updateRecordingEntryStatus(entry.id, status = RecordingEntryStatus.completed)
        } catch (e: Exception) {
            recordingEntryDao.updateRecordingEntryStatus(
                entry.id,
                status = RecordingEntryStatus.agent_error,
                error = e.message,
                errorType = RecordingEntryErrorType.agent_failed,
            )
            throw e
        }
    }
}
