package coredevices.ring.service.recordings.button

import coredevices.mcp.SessionContext
import coredevices.mcp.data.ToolCallResult
import coredevices.ring.agent.AgentFactory
import coredevices.ring.agent.ChatMode
import coredevices.ring.agent.McpSessionFactory
import coredevices.libindex.database.repository.RingTransferRepository
import coredevices.ring.database.room.repository.ItemRepository
import coredevices.ring.database.room.repository.RecordingProcessingTaskRepository
import coredevices.ring.external.indexwebhook.IndexWebhookApi
import coredevices.ring.external.indexwebhook.IndexWebhookPayloadMode
import coredevices.ring.external.indexwebhook.IndexWebhookPreferences
import coredevices.ring.external.indexwebhook.sendsFor
import coredevices.ring.service.ButtonPress
import coredevices.ring.service.parseAsButtonSequence
import coredevices.ring.service.button.GestureDestination
import coredevices.ring.service.button.GestureRoutingPreferences
import coredevices.ring.service.button.RingGesture
import coredevices.ring.service.indexfeed.ItemFactory
import coredevices.ring.service.recordings.RecordingProcessingQueue
import coredevices.ring.storage.RecordingStorage
import coredevices.ring.util.trace.RingTraceSession
import coredevices.util.SystemClipboard

class RecordingOperationFactory(
    private val agentFactory: AgentFactory,
    private val mcpSessionFactory: McpSessionFactory,
    private val gestureRouting: GestureRoutingPreferences,
    private val indexWebhookApi: IndexWebhookApi,
    private val indexWebhookPreferences: IndexWebhookPreferences,
    private val recordingStorage: RecordingStorage,
    private val trace: RingTraceSession,
    private val itemFactory: ItemFactory,
    private val itemRepository: ItemRepository,
    private val ringTransferRepository: RingTransferRepository,
    private val queueTaskRepository: RecordingProcessingTaskRepository,
    private val clipboard: SystemClipboard,
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
        val config = indexWebhookPreferences.configFor(gesture)
        if (!config.sendsFor(destination)) return inner
        val decorated = if (webhookNeedsOwnTranscription(config.payloadMode, inner, fileId)) {
            TranscribeOnlyRecordingOperation(fileId!!)
        } else {
            inner
        }
        return IndexWebhookUploadRecordingOperation(
            webhookApi = indexWebhookApi,
            webhookPreferences = indexWebhookPreferences,
            recordingStorage = recordingStorage,
            fileId = fileId,
            recordingId = recordingId,
            gesture = gesture,
            decorated = decorated,
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
        val mode = if (isQuestion) ChatMode.Search else textChatModeFor(destination)
        val agent = agentFactory.createForChatMode(mode)
        val inner = TextOnlyRecordingOperation(
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

    /**
     * Resumes a recording's conversation after the user answered a confirmation, on the route
     * the recording took: its ring gesture, or Hold & Talk's for typed and phone-mic input.
     */
    suspend fun createConversationResumeOperation(
        recordingId: Long,
        toolCallId: String,
        approved: Boolean,
    ): RecordingOperation {
        val transfer = ringTransferRepository.getTransfersByRecordingId(recordingId).firstOrNull()
        val gesture = if (transfer == null) {
            RingGesture.Hold
        } else {
            queueTaskRepository.getLatestButtonSequenceForTransfer(transfer.id)
                ?.parseAsButtonSequence()
                ?.let { RingGesture.forSequence(it) }
        }
        val destination = gestureRouting.recordingDestinationFor(gesture)
        return ConversationResumeOperation(
            recordingId = recordingId,
            toolCallId = toolCallId,
            approved = approved,
            chatMode = textChatModeFor(destination),
            agentFactory = agentFactory,
            mcpSessionFactory = mcpSessionFactory,
        )
    }

    private fun createForDestination(
        destination: GestureDestination.Recording,
        recordingId: Long,
        transferId: Long?,
        fileId: String,
        forcedTool: (suspend (messageText: String, sessionContext: SessionContext) -> ToolCallResult)
    ): RecordingOperation {
        return when (destination) {
            GestureDestination.IndexAgent,
            GestureDestination.LocalAgent,
            GestureDestination.RemoteAgent -> {
                DefaultRecordingOperation(
                    mcpSessionFactory = mcpSessionFactory,
                    chatAgent = agentFactory.createForChatMode(textChatModeFor(destination)),
                    recordingId = recordingId,
                    transferId = transferId,
                    fileId = fileId,
                    trace = trace,
                    forcedTool = { text, _, sessionContext -> forcedTool(text, sessionContext) }
                )
            }
            GestureDestination.WebSearch -> {
                DefaultRecordingOperation(
                    mcpSessionFactory = mcpSessionFactory,
                    chatAgent = agentFactory.createForChatMode(ChatMode.Search),
                    fileId = fileId,
                    recordingId = recordingId,
                    transferId = transferId,
                    trace = trace,
                    forcedTool = null
                )
            }
            GestureDestination.CopyToClipboard -> CopyToClipboardRecordingOperation(
                transcribing = TranscribeOnlyRecordingOperation(fileId),
                clipboard = clipboard,
            )
            // maybeWrapWithWebhook layers the webhook send on top of this no-op.
            GestureDestination.WebhookOnly, GestureDestination.Nothing -> NoAgentRecordingOperation
        }
    }
}

private object NoAgentRecordingOperation : RecordingOperation {
    override suspend fun run(handle: RecordingProcessingQueue.TaskHandle?) = Unit
}

/** The payload wants a transcript that no other part of the operation will produce. */
internal fun webhookNeedsOwnTranscription(
    payloadMode: IndexWebhookPayloadMode,
    inner: RecordingOperation,
    fileId: String?,
): Boolean = fileId != null &&
    payloadMode != IndexWebhookPayloadMode.RecordingOnly &&
    inner !is TranscribingRecordingOperation

/** Text has no audio to deliver, so webhook-only, clipboard and disabled routes still run the agent. */
internal fun textChatModeFor(destination: GestureDestination.Recording): ChatMode = when (destination) {
    GestureDestination.WebSearch -> ChatMode.Search
    GestureDestination.LocalAgent -> ChatMode.Local
    GestureDestination.RemoteAgent -> ChatMode.Remote
    GestureDestination.IndexAgent,
    GestureDestination.WebhookOnly,
    GestureDestination.CopyToClipboard,
    GestureDestination.Nothing -> ChatMode.Normal
}
