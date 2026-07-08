package coredevices.ring.agent.builtin_servlets.messaging

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import co.touchlab.kermit.Logger
import coredevices.indexai.util.JsonSnake
import coredevices.mcp.BuiltInMcpTool
import coredevices.mcp.SessionContext
import coredevices.mcp.data.SemanticResult
import coredevices.mcp.data.ToolCallResult
import coredevices.ring.database.Preferences
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import androidx.core.net.toUri

actual class SendBeeperMessageTool : BuiltInMcpTool(
    definition = Tool(
        name = SendBeeperMessageToolConstants.TOOL_NAME,
        description = SendBeeperMessageToolConstants.TOOL_DESCRIPTION,
        inputSchema = SendBeeperMessageToolConstants.INPUT_SCHEMA
    ),
    extraContext = "If the user explicitly requests sending a message, use the messaging " +
            "tools."
), KoinComponent {
    private val androidContext: Context by inject()
    private val prefs: Preferences by inject()

    companion object {
        private val logger = Logger.withTag("SendBeeperMessageTool")
    }

    private fun searchForContactId(name: String): List<Pair<ApprovedBeeperContact, Int>> {
        val approvedContacts = prefs.approvedBeeperContacts.value
        return approvedContacts
            .map { it to it.matchScore(name) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
    }

    actual override suspend fun call(jsonInput: String, context: SessionContext): ToolCallResult {
        return try {
            val (contactName, text) = JsonSnake.decodeFromString<SendBeeperMessageArgs>(jsonInput)

            val matches = searchForContactId(contactName)
            logger.d { "Found ${matches.size} matching contacts, scores: ${matches.joinToString { it.second.toString() }}" }
            if (matches.isEmpty()) {
                return ToolCallResult(
                    "No contacts matching '$contactName'.",
                    SemanticResult.GenericFailure("No approved contacts match '$contactName'", forceFallbackTool = true)
                )
            }

            val (contact, _) = matches.first()

            val encodedText = Uri.encode(text)
            val encodedId = Uri.encode(contact.roomId)
            val uri =
                "content://com.beeper.api/messages?roomId=${encodedId}&text=$encodedText".toUri()

            val resultUri = androidContext.contentResolver.insert(uri, ContentValues())

            if (resultUri?.getQueryParameter("messageId") != null) {
                val displayName = contact.nickname ?: contact.name
                ToolCallResult(
                    "{\"success\": true}",
                    SemanticResult.MessageSent(displayName, text, contact.roomId)
                )
            } else {
                logger.e { "Failed to send message, no messageId in result URI: $resultUri" }
                ToolCallResult(
                    "{\"success\": false}",
                    SemanticResult.GenericFailure("Failed to send message. Check permissions and if Beeper is installed.")
                )
            }
        } catch (e: Exception) {
            logger.e(e) { "Error sending message: ${e.message}" }
            ToolCallResult(
                "{\"success\": false}",
                SemanticResult.GenericFailure("Internal error while sending message")
            )
        }
    }
}