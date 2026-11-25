package coredevices.ring.agent.builtin_servlets.messaging

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import co.touchlab.kermit.Logger
import coredevices.indexai.util.JsonSnake
import coredevices.mcp.BuiltInMcpTool
import coredevices.mcp.SessionContext
import coredevices.mcp.data.SemanticResult
import coredevices.mcp.data.ToolCallResult
import coredevices.ring.agent.toolschema.builtinToolDefinitions
import coredevices.ring.database.Preferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

actual class SendBeeperMessageTool : BuiltInMcpTool(
    definition = builtinToolDefinitions.getValue(SendBeeperMessageToolConstants.TOOL_NAME),
    extraContext = "If the user explicitly requests sending a message, use the " +
            "send_instant_message tool. For example, any request starting with 'send a message to'."
), KoinComponent {
    private val androidContext: Context by inject()
    private val prefs: Preferences by inject()

    companion object {
        private val logger = Logger.withTag("SendBeeperMessageTool")
        private const val BEEPER_SEARCH_LIMIT = 10
        private const val RECENT_CHATS_LIMIT = 200
    }

    private data class Recipient(val roomId: String, val displayName: String, val approved: Boolean)

    private fun approvedCandidates(name: String): List<BeeperChatCandidate> =
        prefs.approvedBeeperContacts.value.map {
            BeeperChatCandidate(
                roomId = it.roomId,
                name = it.nickname ?: it.name,
                match = nameMatch(name, listOf(it.name, it.nickname)),
                approved = true,
            )
        }

    private fun beeperCandidates(resolver: ContentResolver, name: String): List<BeeperChatCandidate> {
        val contacts = BeeperAPI.searchContacts(resolver, name, BEEPER_SEARCH_LIMIT)
        val contactRoomIds = contacts.flatMap { it.roomIdList() }.toSet()
        val directChats = if (contactRoomIds.isEmpty()) emptyList() else {
            BeeperAPI.chats(resolver, roomIds = contactRoomIds).filter { it.isOneToOne }
        }
        val contactCandidates = contacts.mapNotNull { contact ->
            val roomIds = contact.roomIdList().toSet()
            val chat = directChats.filter { it.roomId in roomIds }.maxByOrNull { it.timestamp }
                ?: return@mapNotNull null
            BeeperChatCandidate(
                roomId = chat.roomId,
                name = contact.contactName ?: contact.name,
                match = nameMatch(name, listOf(contact.name, contact.contactName)),
            )
        }
        // ponytail: chats outside the most recent RECENT_CHATS_LIMIT are only found via contact search; Beeper has no chat title search
        val chatCandidates = BeeperAPI.chats(resolver, limit = RECENT_CHATS_LIMIT).mapNotNull { chat ->
            val title = chat.title ?: return@mapNotNull null
            BeeperChatCandidate(roomId = chat.roomId, name = title, match = nameMatch(name, title), group = !chat.isOneToOne)
        }
        return contactCandidates + chatCandidates
    }

    private fun BeeperAPI.Contact.roomIdList(): List<String> =
        roomIds.orEmpty().split(",").filter { it.isNotBlank() }

    private suspend fun resolveRecipient(args: SendBeeperMessageArgs, userConfirmed: Boolean): Recipient? {
        args.roomId?.takeIf { userConfirmed }?.let { roomId ->
            val approved = prefs.approvedBeeperContacts.value.any { it.roomId == roomId }
            return Recipient(roomId, args.contactName, approved = approved)
        }
        return withContext(Dispatchers.IO) {
            val resolver = androidContext.contentResolver
            val best = approvedCandidates(args.contactName).bestMatches()
                .ifEmpty {
                    if (prefs.beeperMessageAnyone.value) beeperCandidates(resolver, args.contactName).bestMatches() else emptyList()
                }
            val lastActivity = best.map { it.roomId }.distinct()
                .associateWith { BeeperAPI.latestMessageTime(resolver, it) ?: 0L }
            best.map { it.copy(lastActivity = lastActivity.getValue(it.roomId)) }
                .pick()
                ?.let {
                    logger.d { "Matched ${it.match} (approved=${it.approved}) among ${best.size}" }
                    Recipient(it.roomId, it.name, it.approved)
                }
        }
    }

    actual override suspend fun call(jsonInput: String, context: SessionContext): ToolCallResult {
        return try {
            val args = JsonSnake.decodeFromString<SendBeeperMessageArgs>(jsonInput)
            val text = args.text

            val recipient = resolveRecipient(args, context.userConfirmed)
                ?: return ToolCallResult(
                    "No contacts matching '${args.contactName}'.",
                    SemanticResult.GenericFailure("No contacts match '${args.contactName}'", forceFallbackTool = true)
                )

            if (!recipient.approved && !context.userConfirmed) {
                logger.d { "Recipient not approved, asking user to confirm" }
                return ToolCallResult(
                    "Waiting for the user to confirm sending this message",
                    SemanticResult.RequiresConfirmation(
                        integrationName = MessagingServlet.name,
                        toolName = SendBeeperMessageToolConstants.TOOL_NAME,
                        arguments = buildJsonObject {
                            put("contact_name", recipient.displayName)
                            put("text", text)
                            put("room_id", recipient.roomId)
                        },
                        destructive = false,
                        description = text,
                        title = "Send to ${recipient.displayName}?",
                        allowLabel = "Send",
                        denyLabel = "Dismiss",
                    )
                )
            }

            val encodedText = Uri.encode(text)
            val encodedId = Uri.encode(recipient.roomId)
            val uri =
                "content://com.beeper.api/messages?roomId=${encodedId}&text=$encodedText".toUri()

            val resultUri = androidContext.contentResolver.insert(uri, ContentValues())

            if (resultUri?.getQueryParameter("messageId") != null) {
                ToolCallResult(
                    "{\"success\": true}",
                    SemanticResult.MessageSent(recipient.displayName, text, recipient.roomId)
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
