package coredevices.ring.agent.builtin_servlets.messaging

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import co.touchlab.kermit.Logger
import coredevices.indexai.util.JsonSnake
import coredevices.mcp.BuiltInMcpTool
import coredevices.mcp.data.SemanticResult
import coredevices.mcp.data.ToolCallResult
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

actual class SendBeeperMessageTool : BuiltInMcpTool(
    definition = Tool(
        name = SendBeeperMessageToolConstants.TOOL_NAME,
        description = SendBeeperMessageToolConstants.TOOL_DESCRIPTION,
        inputSchema = SendBeeperMessageToolConstants.INPUT_SCHEMA
    ),
    extraContext = "If the user explicitly requests sending a message, use the contact/messaging " +
            "tools."
), KoinComponent {
    private val context: Context by inject()

    companion object {
        private val logger = Logger.withTag("SendBeeperMessageTool")
    }

    actual override suspend fun call(jsonInput: String): ToolCallResult {
        return try {
            val (contactId, text) = JsonSnake.decodeFromString<SendBeeperMessageArgs>(jsonInput)

            val encodedText = Uri.encode(text)
            val uri =
                Uri.parse("content://com.beeper.api/messages?roomId=$contactId&text=$encodedText")

            val resultUri = context.contentResolver.insert(uri, ContentValues())

            if (resultUri?.getQueryParameter("messageId") != null) {
                ToolCallResult(
                    "{}",
                    SemanticResult.GenericSuccess
                )
            } else {
                ToolCallResult(
                    "{}",
                    SemanticResult.GenericFailure("Failed to send message. Check permissions and if Beeper is installed.")
                )
            }
        } catch (e: Exception) {
            logger.e(e) { "Error sending message: ${e.message}" }
            ToolCallResult(
                "{}",
                SemanticResult.GenericFailure("Internal error while sending message")
            )
        }
    }
}