package coredevices.ring.service.recordings.button

import coredevices.mcp.SessionContext
import coredevices.mcp.data.SemanticResult
import coredevices.mcp.data.ToolCallResult
import coredevices.ring.agent.AgentFactory
import coredevices.ring.agent.ChatMode
import coredevices.ring.agent.McpSessionFactory
import coredevices.ring.database.Preferences
import coredevices.ring.database.SecondaryMode
import coredevices.ring.database.room.repository.ItemRepository
import coredevices.ring.database.room.repository.McpSandboxRepository
import coredevices.ring.external.indexwebhook.IndexWebhookApi
import coredevices.ring.external.indexwebhook.IndexWebhookPreferences
import coredevices.ring.external.indexwebhook.IndexWebhookGesture
import coredevices.ring.service.ButtonPress
import coredevices.ring.service.indexfeed.ItemFactory
import coredevices.ring.storage.RecordingStorage
import coredevices.ring.util.trace.RingTraceSession

class RecordingOperationFactory(
    private val agentFactory: AgentFactory,
    private val mcpSandboxRepository: McpSandboxRepository,
    private val mcpSessionFactory: McpSessionFactory,
    private val prefs: Preferences,
    private val indexWebhookApi: IndexWebhookApi,
    private val indexWebhookPreferences: IndexWebhookPreferences,
    private val recordingStorage: RecordingStorage,
    private val trace: RingTraceSession,
    private val itemFactory: ItemFactory,
    private val itemRepository: ItemRepository,
) {
    companion object {
        private val secondaryOperationSequence = listOf(ButtonPress.Short, ButtonPress.Long)
    }
    suspend fun createForButtonSequence(
        recordingId: Long,
        fileId: String,
        transferId: Long?,
        forcedNoteTool: (suspend (messageText: String, sessionContext: SessionContext) -> ToolCallResult),
        sequence: List<ButtonPress>?
    ): RecordingOperation {
        val isDoubleClickHold = sequence == secondaryOperationSequence
        val inner = if (isDoubleClickHold) {
            createSecondaryOperation(
                recordingId = recordingId,
                fileId = fileId,
                transferId = transferId,
                forcedTool = forcedNoteTool
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
                forcedTool = { text, _, sessionContext -> forcedNoteTool(text, sessionContext) }
            )
        }
        return maybeWrapWithWebhook(
            recordingId = recordingId,
            fileId = fileId,
            isDoubleClickHold = isDoubleClickHold,
            inner = inner,
        )
    }

    private fun maybeWrapWithWebhook(
        recordingId: Long,
        fileId: String,
        isDoubleClickHold: Boolean,
        inner: RecordingOperation,
    ): RecordingOperation {
        val gesture = if (isDoubleClickHold) {
            IndexWebhookGesture.DoubleClickHold
        } else {
            IndexWebhookGesture.SingleClickHold
        }
        if (!indexWebhookPreferences.config(gesture).value.isConfigured) return inner
        return IndexWebhookUploadRecordingOperation(
            webhookApi = indexWebhookApi,
            webhookPreferences = indexWebhookPreferences,
            gesture = gesture,
            recordingStorage = recordingStorage,
            fileId = fileId,
            recordingId = recordingId,
            decorated = inner,
        )
    }

    fun createTextOnlyOperation(
        recordingId: Long,
        text: String,
        forcedTool: (suspend (sessionContext: SessionContext) -> ToolCallResult)?,
        isQuestion: Boolean = false,
    ): RecordingOperation {
        // A typed question routes to the search/answer agent (the text-input equivalent of the
        // ring's double-click-hold "question" gesture) and forces no note, mirroring the Search path.
        val agent = agentFactory.createForChatMode(if (isQuestion) ChatMode.Search else ChatMode.Normal)
        return TextOnlyRecordingOperation(
            mcpSandboxRepository = mcpSandboxRepository,
            mcpSessionFactory = mcpSessionFactory,
            recordingId = recordingId,
            chatAgent = agent,
            text = text,
            forcedTool = if (isQuestion) null else forcedTool,
        )
    }

    private suspend fun createSecondaryOperation(
        recordingId: Long,
        transferId: Long?,
        fileId: String,
        forcedTool: (suspend (messageText: String, sessionContext: SessionContext) -> ToolCallResult)
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
                    forcedTool = { text, _, sessionContext -> forcedTool(text, sessionContext) }
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
            SecondaryMode.McpSandbox -> {
                val group = prefs.secondaryModeMcpGroupId.value
                    ?.let { mcpSandboxRepository.getGroupById(it) }
                // Fall back to normal behaviour if the configured group was deleted
                val mode = group?.let { ChatMode.McpSandbox(it) } ?: ChatMode.Normal
                DefaultRecordingOperation(
                    mcpSandboxRepository = mcpSandboxRepository,
                    mcpSessionFactory = mcpSessionFactory,
                    chatAgent = agentFactory.createForChatMode(mode),
                    recordingId = recordingId,
                    transferId = transferId,
                    fileId = fileId,
                    trace = trace,
                    // Sandbox mode doesn't force a note; if the agent only replied
                    // with a message, capture it as an answer instead
                    forcedTool = if (group == null) {
                        { text, _, sessionContext -> forcedTool(text, sessionContext) }
                    } else {
                        { text, answer, _ -> forcedAnswerTool(text, answer) }
                    },
                    sandboxGroupId = group?.id
                )
            }
        }
    }

    /** Surfaces the agent's plain reply as a tool result (question = transcription).
     *  No item is created — the reply only shows in the conversation/notification. */
    private suspend fun forcedAnswerTool(question: String, answer: String?): ToolCallResult {
        if (answer.isNullOrBlank()) {
            return ToolCallResult(
                resultString = "Agent took no action and gave no response",
                semanticResult = SemanticResult.GenericFailure(
                    userErrorMessage = "No response from agent"
                )
            )
        }
        return ToolCallResult(
            resultString = "",
            semanticResult = SemanticResult.Response(answer, question = question)
        )
    }
}