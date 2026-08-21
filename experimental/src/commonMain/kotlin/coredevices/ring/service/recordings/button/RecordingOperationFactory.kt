package coredevices.ring.service.recordings.button

import coredevices.mcp.SessionContext
import coredevices.mcp.data.SemanticResult
import coredevices.mcp.data.ToolCallResult
import coredevices.ring.agent.AgentFactory
import coredevices.ring.agent.ChatMode
import coredevices.ring.agent.McpSessionFactory
import coredevices.ring.database.room.repository.ItemRepository
import coredevices.ring.database.room.repository.McpSandboxRepository
import coredevices.ring.external.indexwebhook.IndexWebhookApi
import coredevices.ring.external.indexwebhook.IndexWebhookPreferences
import coredevices.ring.external.indexwebhook.sendsFor
import coredevices.ring.service.ButtonPress
import coredevices.indexai.data.entity.mcp_sandbox.McpSandboxGroupEntity
import coredevices.ring.service.button.GestureDestination
import coredevices.ring.service.button.GestureRoutingPreferences
import coredevices.ring.service.button.RingGesture
import coredevices.ring.service.indexfeed.ItemFactory
import coredevices.ring.service.recordings.RecordingProcessingQueue
import coredevices.ring.storage.RecordingStorage
import coredevices.ring.util.trace.RingTraceSession

class RecordingOperationFactory(
    private val agentFactory: AgentFactory,
    private val mcpSandboxRepository: McpSandboxRepository,
    private val mcpSessionFactory: McpSessionFactory,
    private val gestureRouting: GestureRoutingPreferences,
    private val indexWebhookApi: IndexWebhookApi,
    private val indexWebhookPreferences: IndexWebhookPreferences,
    private val recordingStorage: RecordingStorage,
    private val trace: RingTraceSession,
    private val itemFactory: ItemFactory,
    private val itemRepository: ItemRepository,
) {
    suspend fun createForButtonSequence(
        recordingId: Long,
        fileId: String,
        transferId: Long?,
        forcedNoteTool: (suspend (messageText: String, sessionContext: SessionContext) -> ToolCallResult),
        sequence: List<ButtonPress>?
    ): RecordingOperation {
        val gesture = sequence?.let { RingGesture.forSequence(it) }
        val destination = gestureRouting.recordingDestinationFor(gesture)
        val inner = createForDestination(
            destination = destination,
            recordingId = recordingId,
            fileId = fileId,
            transferId = transferId,
            forcedTool = forcedNoteTool
        )
        // The phone's own hold-to-record has no gesture and borrows Hold & Talk's webhook, so the
        // send is gated on Hold & Talk's route too — not the agent route resolved above.
        val webhookGesture = gesture ?: RingGesture.Hold
        return maybeWrapWithWebhook(
            recordingId = recordingId,
            fileId = fileId,
            gesture = webhookGesture,
            destination = gestureRouting.recordingDestinationFor(webhookGesture),
            inner = inner,
        )
    }

    private fun maybeWrapWithWebhook(
        recordingId: Long,
        fileId: String?,
        gesture: RingGesture,
        destination: GestureDestination.Recording,
        inner: RecordingOperation,
    ): RecordingOperation {
        if (!indexWebhookPreferences.configFor(gesture).sendsFor(destination)) return inner
        return IndexWebhookUploadRecordingOperation(
            webhookApi = indexWebhookApi,
            webhookPreferences = indexWebhookPreferences,
            recordingStorage = recordingStorage,
            fileId = fileId,
            recordingId = recordingId,
            gesture = gesture,
            decorated = inner,
        )
    }

    suspend fun createTextOnlyOperation(
        recordingId: Long,
        text: String,
        forcedTool: (suspend (sessionContext: SessionContext) -> ToolCallResult)?,
        isQuestion: Boolean = false,
    ): RecordingOperation {
        // A typed question routes to the search/answer agent and forces no note. Anything else
        // follows wherever Hold & Talk points, so typing stands in for the gesture.
        val destination = gestureRouting.recordingDestinationFor(RingGesture.Hold)
        val mode = if (isQuestion) {
            ChatMode.Search
        } else {
            textChatModeFor(destination, sandboxGroupFor(destination))
        }
        val agent = agentFactory.createForChatMode(mode)
        val inner = TextOnlyRecordingOperation(
            mcpSandboxRepository = mcpSandboxRepository,
            mcpSessionFactory = mcpSessionFactory,
            recordingId = recordingId,
            chatAgent = agent,
            text = text,
            forcedTool = if (isQuestion) null else forcedTool,
        )
        return maybeWrapWithWebhook(
            recordingId = recordingId,
            fileId = null,
            gesture = RingGesture.Hold,
            destination = destination,
            inner = inner,
        )
    }

    private suspend fun sandboxGroupFor(destination: GestureDestination.Recording) =
        (destination as? GestureDestination.McpSandbox)?.groupId
            ?.let { mcpSandboxRepository.getGroupById(it) }

    private suspend fun createForDestination(
        destination: GestureDestination.Recording,
        recordingId: Long,
        transferId: Long?,
        fileId: String,
        forcedTool: (suspend (messageText: String, sessionContext: SessionContext) -> ToolCallResult)
    ): RecordingOperation {
        return when (destination) {
            GestureDestination.IndexAgent -> {
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
            GestureDestination.WebSearch -> {
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
            // maybeWrapWithWebhook layers the webhook send on top of this no-op.
            GestureDestination.WebhookOnly, GestureDestination.Nothing -> NoAgentRecordingOperation
            is GestureDestination.McpSandbox -> {
                val group = destination.groupId
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

private object NoAgentRecordingOperation : RecordingOperation {
    override suspend fun run(handle: RecordingProcessingQueue.TaskHandle?) = Unit
}

/** Text has no audio to deliver, so webhook-only and disabled routes still run the agent. */
internal fun textChatModeFor(
    destination: GestureDestination.Recording,
    sandboxGroup: McpSandboxGroupEntity?,
): ChatMode = when (destination) {
    GestureDestination.WebSearch -> ChatMode.Search
    is GestureDestination.McpSandbox -> sandboxGroup?.let { ChatMode.McpSandbox(it) } ?: ChatMode.Normal
    GestureDestination.IndexAgent,
    GestureDestination.WebhookOnly,
    GestureDestination.Nothing -> ChatMode.Normal
}
