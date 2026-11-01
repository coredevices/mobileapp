package coredevices.ring.agent.confirmation

import co.touchlab.kermit.Logger
import com.eygraber.uri.Uri
import coredevices.indexai.agent.pendingConfirmations
import coredevices.indexai.data.entity.ConversationMessageDocument
import coredevices.indexai.database.dao.ConversationMessageDao
import coredevices.mcp.data.SemanticResult
import coredevices.ring.service.GenericNotification
import coredevices.ring.service.IndexNotificationChannel
import coredevices.ring.service.NotificationAction
import coredevices.ring.service.PlatformIndexNotificationManager
import coredevices.ring.service.RecordingBackgroundScope
import coredevices.ring.service.recordings.button.RecordingOperationFactory
import coredevices.ring.ui.navigation.RingRoutes
import coredevices.ring.ui.viewmodel.humanizeToolName
import coredevices.util.Platform
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Lets the user allow or deny a tool call the agent paused on ([SemanticResult.RequiresConfirmation]),
 * from the conversation screen or a notification. Answering runs the call (or records the denial)
 * and resumes the agent.
 */
interface AgentUserConfirmation {
    suspend fun requestConfirmation(recordingId: Long, request: ConversationMessageDocument)
    fun respond(recordingId: Long, toolCallId: String, approved: Boolean)
}

class RealAgentUserConfirmation(
    private val notifications: PlatformIndexNotificationManager,
    private val conversationMessageDao: ConversationMessageDao,
    private val operationFactory: RecordingOperationFactory,
    private val scope: RecordingBackgroundScope,
    private val platform: Platform,
) : AgentUserConfirmation {
    companion object {
        private val logger = Logger.withTag("AgentUserConfirmation")
    }

    private val inFlightLock = Mutex()
    private val inFlight = mutableSetOf<String>()

    override suspend fun requestConfirmation(recordingId: Long, request: ConversationMessageDocument) {
        val result = request.semantic_result as? SemanticResult.RequiresConfirmation ?: return
        val callId = request.tool_call_id ?: return
        notifications.notify(confirmationNotification(recordingId, callId, result))
    }

    override fun respond(recordingId: Long, toolCallId: String, approved: Boolean) {
        notifications.cancel(confirmationNotificationId(toolCallId))
        scope.launch {
            if (!inFlightLock.withLock { inFlight.add(toolCallId) }) return@launch
            try {
                platform.runWithBgTask("tool_confirmation_$toolCallId") {
                    resolve(recordingId, toolCallId, approved)
                }
            } finally {
                inFlightLock.withLock { inFlight.remove(toolCallId) }
            }
        }
    }

    private suspend fun resolve(recordingId: Long, toolCallId: String, approved: Boolean) {
        val pending = conversationMessageDao.getMessagesForRecording(recordingId).first()
            .map { it.document }
            .pendingConfirmations()
            .any { it.tool_call_id == toolCallId }
        if (!pending) {
            logger.w { "Tool call $toolCallId on recording $recordingId is not awaiting confirmation" }
            return
        }
        logger.i { "Tool call $toolCallId on recording $recordingId ${if (approved) "allowed" else "denied"} by user" }
        try {
            operationFactory.createConversationResumeOperation(recordingId, toolCallId, approved).run()
        } catch (e: Exception) {
            logger.e(e) { "Failed to resume recording $recordingId after confirmation" }
        }
    }
}

private const val NOTIFICATION_ID_BASE = 3_000_000
internal const val CONFIRM_TOOL_PATH = "confirm-tool"
private const val RECORDING_ID_PARAM = "recordingId"
private const val CALL_ID_PARAM = "callId"
private const val APPROVED_PARAM = "approved"

internal fun confirmationNotificationId(toolCallId: String): Int =
    NOTIFICATION_ID_BASE + (toolCallId.hashCode() and 0xFFFF)

internal fun confirmationNotification(
    recordingId: Long,
    toolCallId: String,
    request: SemanticResult.RequiresConfirmation,
): GenericNotification {
    val tool = humanizeToolName(request.toolName) ?: request.toolName
    val content = buildString {
        appendLine("Allow agent to run $tool from ${request.integrationName}?")
        request.description?.let {
            appendLine()
            appendLine(it)
        }
        if (request.destructive) {
            appendLine()
            appendLine("Warning: This action is destructive.")
        }
    }.trim()
    return GenericNotification(
        id = confirmationNotificationId(toolCallId),
        title = "Confirm agent action",
        contentText = content.takeIf { it.isNotBlank() },
        deepLink = RingRoutes.recordingDeepLink(recordingId),
        channel = IndexNotificationChannel.IndexAction,
        actions = listOf(
            NotificationAction("Allow", confirmationDeepLink(recordingId, toolCallId, approved = true), background = true),
            NotificationAction("Deny", confirmationDeepLink(recordingId, toolCallId, approved = false), background = true),
        ),
    )
}

internal fun confirmationDeepLink(recordingId: Long, toolCallId: String, approved: Boolean): String =
    Uri.Builder()
        .scheme("pebblecore")
        .authority("index-link")
        .appendPath(CONFIRM_TOOL_PATH)
        .appendQueryParameter(RECORDING_ID_PARAM, recordingId.toString())
        .appendQueryParameter(CALL_ID_PARAM, toolCallId)
        .appendQueryParameter(APPROVED_PARAM, approved.toString())
        .build()
        .toString()

data class ConfirmationResponse(val recordingId: Long, val toolCallId: String, val approved: Boolean)

/** The answer carried by a `pebblecore://index-link/confirm-tool` link, or null if malformed. */
fun parseConfirmationDeepLink(uri: Uri): ConfirmationResponse? {
    if (uri.pathSegments.firstOrNull() != CONFIRM_TOOL_PATH) return null
    val recordingId = uri.getQueryParameter(RECORDING_ID_PARAM)?.toLongOrNull() ?: return null
    val callId = uri.getQueryParameter(CALL_ID_PARAM)?.takeIf { it.isNotBlank() } ?: return null
    val approved = uri.getQueryParameter(APPROVED_PARAM)?.toBooleanStrictOrNull() ?: return null
    return ConfirmationResponse(recordingId, callId, approved)
}
