package coredevices.ring.agent.builtin_servlets.notifications

import co.touchlab.kermit.Logger
import coredevices.indexai.util.JsonSnake
import coredevices.mcp.BuiltInMcpTool
import coredevices.mcp.SessionContext
import coredevices.mcp.data.SemanticResult
import coredevices.mcp.data.ToolCallResult
import coredevices.ring.agent.toolschema.builtinToolDefinitions
import coredevices.toolschema.ToolDefinition
import coredevices.toolschema.ToolParam
import io.rebble.libpebblecommon.connection.DisplayedNotificationReplyResult
import io.rebble.libpebblecommon.connection.NotificationReplies
import kotlin.time.Clock
import kotlinx.serialization.Serializable

class ReplyToWatchNotificationTool(
    private val notificationReplies: NotificationReplies,
) : BuiltInMcpTool(
    definition = builtinToolDefinitions.getValue(TOOL_NAME),
    extraContext = "When the user's request starts with 'reply' (e.g. 'reply on my way'), call " +
        "'$TOOL_NAME' with the rest of the request as the text, rather than any other messaging tool."
) {
    companion object {
        private val logger = Logger.withTag("ReplyToWatchNotificationTool")
        const val TOOL_NAME = "reply_to_watch_notification"
        const val TOOL_DESCRIPTION =
            "Reply to the notification currently shown on the user's Pebble watch. Use when the user says 'reply ...'."
    }

    @ToolDefinition(servlet = NotificationReplyServlet.NAME, name = TOOL_NAME, description = TOOL_DESCRIPTION)
    @Serializable
    private data class ReplyArgs(
        @ToolParam("The reply to send, quoted from what the user said after 'reply'")
        val text: String,
    )

    override suspend fun call(jsonInput: String, context: SessionContext): ToolCallResult {
        val text = JsonSnake.decodeFromString<ReplyArgs>(jsonInput).text.trim()
        if (text.isEmpty()) {
            return failure("Nothing to reply with", llmRecoverable = true)
        }
        val at = context.timeBase ?: Clock.System.now()
        val result = try {
            notificationReplies.replyToDisplayedNotification(text, at)
        } catch (e: Exception) {
            logger.e(e) { "Failed to reply to watch notification" }
            DisplayedNotificationReplyResult.Failed
        }
        return when (result) {
            is DisplayedNotificationReplyResult.Sent -> ToolCallResult(
                "{\"success\": true}",
                SemanticResult.MessageSent(recipientName = result.title ?: "notification", text = text)
            )
            DisplayedNotificationReplyResult.NoNotificationDisplayed ->
                failure("No notification was showing on your watch")
            DisplayedNotificationReplyResult.NotReplyable ->
                failure("The notification on your watch can't be replied to")
            DisplayedNotificationReplyResult.Unsupported ->
                failure("Replying to notifications isn't supported on this phone")
            DisplayedNotificationReplyResult.Failed ->
                failure("Failed to send the reply")
        }
    }

    private fun failure(message: String, llmRecoverable: Boolean = false) = ToolCallResult(
        "{\"success\": false, \"error_message\": \"$message\"}",
        SemanticResult.GenericFailure(message, llmRecoverable = llmRecoverable)
    )
}
