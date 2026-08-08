package coredevices.ring.service

import co.touchlab.kermit.Logger
import coredevices.mcp.SessionContext
import coredevices.mcp.data.SemanticResult
import coredevices.ring.agent.McpSessionFactory
import coredevices.ring.data.entity.room.ClickAction
import coredevices.ring.data.entity.room.ClickActionBinding
import coredevices.ring.database.room.repository.ItemRepository
import coredevices.ring.database.room.repository.McpSandboxRepository
import coredevices.ring.external.indexwebhook.IndexWebhookApi
import coredevices.ring.external.indexwebhook.IndexWebhookRecordingTrigger
import coredevices.ring.service.indexfeed.ItemFactory
import coredevices.ring.service.recordings.RecordingProcessingQueue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

/**
 * Runs the action bound to a custom click gesture. Unlike a recording, there is no transcript
 * and no agent turn, so tool calls are dispatched directly with the arguments the user
 * configured and the outcome is logged to the feed here rather than by
 * [coredevices.ring.service.recordings.RecordingProcessor].
 */
class ClickActionExecutor(
    private val mcpSessionFactory: McpSessionFactory,
    private val mcpSandboxRepository: McpSandboxRepository,
    private val recordingProcessingQueue: RecordingProcessingQueue,
    private val webhookApi: IndexWebhookApi,
    private val itemFactory: ItemFactory,
    private val itemRepository: ItemRepository,
) {
    companion object {
        private val logger = Logger.withTag("ClickActionExecutor")
        private val SESSION_CLOSE_TIMEOUT = 3.seconds
    }

    suspend fun execute(binding: ClickActionBinding) {
        when (val action = binding.action) {
            is ClickAction.AgentText -> recordingProcessingQueue.queueTextProcessing(action.text)
            is ClickAction.ToolCall -> runTool(action, binding.clickCount)
            ClickAction.Unsupported ->
                logger.w { "${binding.clickCount} clicks is bound to an unreadable action; ignoring" }
            // No trigger check here: unlike a recording, the binding itself is the user's
            // explicit opt-in, so IndexWebhookPreferences.trigger doesn't apply.
            ClickAction.Webhook -> webhookApi.uploadIfEnabled(
                samples = null,
                sampleRate = RingSync.TARGET_SAMPLE_RATE,
                recordingId = "clicks-${binding.clickCount}-${Clock.System.now()}",
                transcription = null,
                recordedAt = Clock.System.now(),
                trigger = IndexWebhookRecordingTrigger.Clicks(binding.clickCount),
            )
        }
    }

    private suspend fun runTool(action: ClickAction.ToolCall, clickCount: Int) = coroutineScope {
        val groupId = mcpSandboxRepository.getDefaultGroupIdOrNull()
            ?: error("No MCP sandbox group configured")
        val session = mcpSessionFactory.createForDirectToolCall(
            groupId,
            this,
            onlyIntegration = action.integrationName,
        )
        session.openSession()
        val result = try {
            session.callTool(
                integrationName = action.integrationName,
                toolName = action.toolName,
                jsonInput = action.arguments,
                context = SessionContext(
                    // No recording to anchor to, so relative times resolve against now.
                    timeBase = Clock.System.now(),
                    // Already completed: a tool awaiting this must not block on a gesture
                    // that carries no speech.
                    userMessageText = CompletableDeferred<String?>().apply { complete(null) },
                ),
            )
        } finally {
            // NonCancellable so a cancelled click action still releases the connection.
            withContext(NonCancellable) {
                withTimeout(SESSION_CLOSE_TIMEOUT) { session.closeSession() }
            }
        }
        logger.i { "$clickCount clicks ran ${action.integrationName}/${action.toolName}: ${result.semanticResult}" }
        logToFeed(action, result.semanticResult)
    }

    private suspend fun logToFeed(action: ClickAction.ToolCall, semanticResult: SemanticResult?) {
        // Tools that create their own item (notes, reminders) have already done so; adding an
        // action log for those would double up in the feed.
        if (semanticResult is SemanticResult.ListItemCreation || semanticResult is SemanticResult.TaskCreation) return
        val failure = semanticResult as? SemanticResult.GenericFailure
        val item = itemFactory.actionLogItem(
            sourceRecordingId = null,
            createdAt = Clock.System.now(),
            title = action.toolName,
            toolName = "${action.integrationName}/${action.toolName}",
            success = failure == null,
            toolCallId = null,
            body = failure?.userErrorMessage.orEmpty(),
        )
        runCatching { itemRepository.setItem(itemFactory.simpleUid(), item) }
            .onFailure { logger.e(it) { "Failed to log click action to feed" } }
    }
}
