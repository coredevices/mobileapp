package coredevices.ring.service.recordings.button

import coredevices.EnableExperimentalDevices
import coredevices.indexai.agent.Agent
import coredevices.mcp.data.ToolCallResult
import coredevices.ring.agent.AgentFactory
import coredevices.ring.agent.ChatMode
import coredevices.ring.agent.McpSessionFactory
import coredevices.ring.database.Preferences
import coredevices.ring.database.PrimaryMode
import coredevices.ring.database.SecondaryMode
import coredevices.ring.database.room.repository.McpSandboxRepository
import coredevices.ring.external.indexwebhook.IndexWebhookApi
import coredevices.ring.external.indexwebhook.IndexWebhookPreferences
import coredevices.ring.service.ButtonPress
import coredevices.ring.storage.RecordingStorage
import coredevices.ring.util.trace.RingTraceSession
import coredevices.util.Platform
import coredevices.util.isIOS

class RecordingOperationFactory(
    private val agentFactory: AgentFactory,
    private val mcpSandboxRepository: McpSandboxRepository,
    private val mcpSessionFactory: McpSessionFactory,
    private val prefs: Preferences,
    private val indexWebhookApi: IndexWebhookApi,
    private val indexWebhookPreferences: IndexWebhookPreferences,
    private val recordingStorage: RecordingStorage,
    private val trace: RingTraceSession,
    private val platform: Platform,
    private val enableExperimentalDevices: EnableExperimentalDevices,
) {
    companion object {
        private val secondaryOperationSequence = listOf(ButtonPress.Short, ButtonPress.Long)
    }
    fun createForButtonSequence(
        recordingId: Long,
        fileId: String,
        transferId: Long?,
        forcedNoteTool: (suspend (messageText: String) -> ToolCallResult),
        sequence: List<ButtonPress>?
    ): RecordingOperation {
        return if (sequence == secondaryOperationSequence) {
            createSecondaryOperation(
                recordingId = recordingId,
                fileId = fileId,
                transferId = transferId,
                forcedTool = forcedNoteTool
            )
        } else {
            createPrimaryOperation(
                recordingId = recordingId,
                fileId = fileId,
                transferId = transferId,
                forcedTool = forcedNoteTool
            )
        }
    }

    fun createTextOnlyOperation(
        recordingId: Long,
        text: String,
        forcedTool: (suspend () -> ToolCallResult),
        agent: Agent = agentFactory.createForChatMode(ChatMode.Normal),
    ): RecordingOperation {
        return TextOnlyRecordingOperation(
            mcpSandboxRepository = mcpSandboxRepository,
            mcpSessionFactory = mcpSessionFactory,
            recordingId = recordingId,
            chatAgent = agent,
            text = text,
            forcedTool = forcedTool
        )
    }

    private fun createPrimaryOperation(
        recordingId: Long,
        transferId: Long?,
        fileId: String,
        forcedTool: (suspend (messageText: String) -> ToolCallResult)
    ): RecordingOperation {
        val eligibleForWebhookPrimary = platform.isIOS
            && prefs.primaryMode.value == PrimaryMode.Webhook
            && indexWebhookPreferences.isLinked
            && enableExperimentalDevices.enabled.value

        return if (eligibleForWebhookPrimary) {
            IndexWebhookUploadRecordingOperation(
                webhookApi = indexWebhookApi,
                webhookPreferences = indexWebhookPreferences,
                recordingStorage = recordingStorage,
                fileId = fileId,
                recordingId = recordingId,
                decorated = DefaultRecordingOperation(
                    mcpSandboxRepository = mcpSandboxRepository,
                    mcpSessionFactory = mcpSessionFactory,
                    chatAgent = agentFactory.createForChatMode(ChatMode.Normal),
                    recordingId = recordingId,
                    transferId = transferId,
                    fileId = fileId,
                    trace = trace,
                    forcedTool = forcedTool,
                    runAgent = prefs.shareWithIndexAgent.value,
                ),
            )
        } else {
            DefaultRecordingOperation(
                mcpSandboxRepository = mcpSandboxRepository,
                mcpSessionFactory = mcpSessionFactory,
                chatAgent = agentFactory.createForChatMode(ChatMode.Normal),
                recordingId = recordingId,
                transferId = transferId,
                fileId = fileId,
                trace = trace,
                forcedTool = forcedTool,
                // runAgent defaults to true
            )
        }
    }

    private fun createSecondaryOperation(
        recordingId: Long,
        transferId: Long?,
        fileId: String,
        forcedTool: (suspend (messageText: String) -> ToolCallResult)
    ): RecordingOperation {
        return when (prefs.secondaryMode.value) {
            SecondaryMode.Disabled -> {
                DefaultRecordingOperation(
                    mcpSandboxRepository = mcpSandboxRepository,
                    mcpSessionFactory = mcpSessionFactory,
                    chatAgent = agentFactory.createForChatMode(ChatMode.Normal),
                    recordingId = recordingId,
                    transferId = transferId,
                    fileId = fileId,
                    trace = trace,
                    forcedTool = forcedTool
                )
            }
            SecondaryMode.Search -> {
                DefaultRecordingOperation(
                    mcpSandboxRepository = mcpSandboxRepository,
                    mcpSessionFactory = mcpSessionFactory,
                    chatAgent = agentFactory.createForChatMode(ChatMode.Search),
                    fileId = fileId,
                    recordingId = recordingId,
                    transferId = transferId,
                    trace = trace,
                    forcedTool = null
                )
            }
            SecondaryMode.IndexWebhook -> {
                IndexWebhookUploadRecordingOperation(
                    webhookApi = indexWebhookApi,
                    webhookPreferences = indexWebhookPreferences,
                    recordingStorage = recordingStorage,
                    fileId = fileId,
                    recordingId = recordingId,
                    decorated = DefaultRecordingOperation(
                        mcpSandboxRepository = mcpSandboxRepository,
                        mcpSessionFactory = mcpSessionFactory,
                        chatAgent = agentFactory.createForChatMode(ChatMode.Normal),
                        recordingId = recordingId,
                        transferId = transferId,
                        fileId = fileId,
                        trace = trace,
                        forcedTool = forcedTool,
                    ),
                )
            }
        }
    }
}