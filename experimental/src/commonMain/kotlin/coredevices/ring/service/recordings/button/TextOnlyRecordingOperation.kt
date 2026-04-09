package coredevices.ring.service.recordings.button

import co.touchlab.kermit.Logger
import coredevices.indexai.agent.Agent
import coredevices.indexai.data.entity.RecordingEntryEntity
import coredevices.indexai.data.entity.RecordingEntryStatus
import coredevices.indexai.database.dao.RecordingEntryDao
import coredevices.mcp.data.ToolCallResult
import coredevices.ring.agent.McpSessionFactory
import coredevices.ring.database.room.repository.McpSandboxRepository
import coredevices.ring.database.room.repository.RecordingRepository
import coredevices.ring.service.recordings.RecordingProcessingQueue
import coredevices.ring.service.recordings.RecordingProcessingStage
import coredevices.ring.service.recordings.RecordingProcessor
import coredevices.ring.service.recordings.RecordingProcessor.RecordingStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

open class TextOnlyRecordingOperation(
    private val recordingId: Long,
    private val chatAgent: Agent,
    private val text: String,
    private val mcpSandboxRepository: McpSandboxRepository,
    private val mcpSessionFactory: McpSessionFactory,
    private val forcedTool: (suspend () -> ToolCallResult)?,
) : RecordingOperation, KoinComponent {
    companion object {
        private val logger = Logger.withTag("TextOnlyRecordingOperation")
    }

    private val recordingProcessor: RecordingProcessor by inject()
    private val recordingEntryDao: RecordingEntryDao by inject()

    override suspend fun run(handle: RecordingProcessingQueue.TaskHandle?) {
        val entryId = withContext(Dispatchers.IO) {
            if (handle?.stage is RecordingProcessingStage.RecordingEntryCreated) {
                (handle.stage as RecordingProcessingStage.RecordingEntryCreated).recordingEntryId
            } else {
                val newId = recordingEntryDao.insertRecordingEntry(
                    RecordingEntryEntity(
                        recordingId = recordingId,
                        status = RecordingEntryStatus.agent_processing,
                        transcription = text,
                    )
                )
                handle?.updateStage(RecordingProcessingStage.RecordingEntryCreated(
                    recordingEntryId = newId,
                    previous = handle.stage!! as RecordingProcessingStage.RecordingEntityCreated)
                )
                newId
            }
        }
        coroutineScope {
            val mcpSession = mcpSessionFactory.createForSandboxGroup(
                    mcpSandboxRepository.getDefaultGroupId(),
                this
            )
            recordingProcessor.processText(
                recordingId = recordingId,
                mcpSession = mcpSession,
                recordingEntryId = entryId,
                agent = chatAgent,
                forcedTool = forcedTool,
                text = text
            )
        }
    }
}