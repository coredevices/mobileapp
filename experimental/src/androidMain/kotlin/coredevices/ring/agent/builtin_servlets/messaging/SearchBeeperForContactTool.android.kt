package coredevices.ring.agent.builtin_servlets.messaging

import android.content.Context
import androidx.core.database.getStringOrNull
import androidx.core.net.toUri
import co.touchlab.kermit.Logger
import coredevices.indexai.util.JsonSnake
import coredevices.mcp.BuiltInMcpTool
import coredevices.mcp.data.SemanticResult
import coredevices.mcp.data.ToolCallResult
import coredevices.ring.agent.builtin_servlets.messaging.SearchBeeperForContactToolConstants.EXTRA_CONTEXT
import coredevices.ring.agent.builtin_servlets.messaging.SearchBeeperForContactToolConstants.INPUT_SCHEMA
import coredevices.ring.agent.builtin_servlets.messaging.SearchBeeperForContactToolConstants.TOOL_DESCRIPTION
import coredevices.ring.agent.builtin_servlets.messaging.SearchBeeperForContactToolConstants.TOOL_NAME
import coredevices.ring.database.Preferences
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

actual class SearchBeeperForContactTool : BuiltInMcpTool(
    definition = Tool(
        name = TOOL_NAME,
        description = TOOL_DESCRIPTION,
        inputSchema = INPUT_SCHEMA
    ),
    extraContext = EXTRA_CONTEXT
), KoinComponent {
    private val context: Context by inject()
    private val prefs: Preferences by inject()

    companion object {
        private val logger = Logger.withTag("SearchBeeperForContactTool")
    }

    private data class Contact(
        val id: String,
        val displayName: String,
        val roomId: String,
        val timestamp: Long
    )

    actual override suspend fun call(jsonInput: String): ToolCallResult {
        val (name) = JsonSnake.decodeFromString<SearchBeeperForContactArgs>(jsonInput)
        val approvedContacts = prefs.approvedBeeperContacts.value.toSet()

        val contactsUri = "content://com.beeper.api/contacts".toUri()
            .buildUpon()
            .appendQueryParameter("query", name)
            .build()

        val contentResolver = context.contentResolver
        val contactChats = mutableListOf<Contact>() // displayName, (roomId, timestamp)

        try {
            withContext(Dispatchers.IO) {
                contentResolver.query(
                    contactsUri,
                    arrayOf("id", "displayName", "roomIds"),
                    null,
                    null,
                    null
                )?.use { contactsCursor ->
                    val idIndex = contactsCursor.getColumnIndexOrThrow("id")
                    val displayNameIndex =
                        contactsCursor.getColumnIndexOrThrow("displayName")
                    val roomIdsIndex = contactsCursor.getColumnIndexOrThrow("roomIds")

                    while (contactsCursor.moveToNext()) {
                        val id = contactsCursor.getString(idIndex)
                        val displayName = contactsCursor.getString(displayNameIndex)
                        val roomIds = contactsCursor.getStringOrNull(roomIdsIndex)

                        if (displayName.isNullOrBlank() || roomIds.isNullOrBlank()) continue

                        val chatsUri = "content://com.beeper.api/chats".toUri()
                            .buildUpon()
                            .appendQueryParameter("roomIds", roomIds)
                            .build()

                        contentResolver.query(
                            chatsUri,
                            arrayOf("roomId", "oneToOne", "timestamp"),
                            null,
                            null,
                            "timestamp DESC"
                        )?.use { chatsCursor ->
                            val roomIdIndex = chatsCursor.getColumnIndexOrThrow("roomId")
                            val timestampIndex =
                                chatsCursor.getColumnIndexOrThrow("timestamp")
                            val oneToOneIndex =
                                chatsCursor.getColumnIndexOrThrow("oneToOne")

                            while (chatsCursor.moveToNext()) {
                                val isOneToOne = chatsCursor.getInt(oneToOneIndex) == 1
                                if (isOneToOne) {
                                    val roomId = chatsCursor.getString(roomIdIndex)
                                    val timestamp = chatsCursor.getLong(timestampIndex)
                                    contactChats.add(
                                        Contact(
                                            id = id,
                                            displayName = displayName,
                                            roomId = roomId,
                                            timestamp = timestamp
                                        )
                                    )
                                    break // Found the most recent one-to-one chat for this contact
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logger.e(e) { "Failed to query Beeper API: ${e.message}" }
            return ToolCallResult(
                "Error querying Beeper contacts: ${e.message}",
                SemanticResult.GenericFailure("Internal error while querying contacts")
            )
        }

        contactChats.sortByDescending { it.timestamp }
        val filteredContacts = contactChats.filter { approvedContacts.contains(it.id) }

        if (filteredContacts.isEmpty() && contactChats.isNotEmpty()) {
            return ToolCallResult(
                "No approved contacts found matching '$name'",
                SemanticResult.GenericFailure("Contacts found were not approved to message")
            )
        }

        val resultMap = LinkedHashMap<String, String>()
        filteredContacts.forEach { contactChat ->
            if (!resultMap.containsKey(contactChat.displayName)) {
                resultMap[contactChat.displayName] = contactChat.roomId
            }
        }

        return ToolCallResult(
            JsonSnake.encodeToString(resultMap),
            SemanticResult.SupportingData("Found ${resultMap.size} contacts matching '$name'")
        )
    }
}