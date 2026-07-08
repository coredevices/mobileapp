package coredevices.ring.agent.builtin_servlets.notes

import co.touchlab.kermit.Logger
import coredevices.indexai.util.JsonSnake
import coredevices.mcp.BuiltInMcpTool
import coredevices.mcp.SessionContext
import coredevices.mcp.data.SemanticResult
import coredevices.mcp.data.ToolCallResult
import coredevices.ring.agent.integrations.itemSource
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import io.modelcontextprotocol.kotlin.sdk.types.toJson
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import org.koin.core.component.KoinComponent

class CreateNoteTool(private val noteIntegrationFactory: NoteIntegrationFactory) : BuiltInMcpTool(
    definition = Tool(
        name = TOOL_NAME,
        description = TOOL_DESCRIPTION,
        inputSchema = ToolSchema(
            properties = JsonObject(
                mapOf(
                    "text" to JsonObject(
                        mapOf(
                            "type" to "string",
                            "description" to "The text of the note, usually a direct quote from the user"
                        ).toJson()
                    )
                )
            ),
            required = listOf("text")
        ),
        outputSchema = ToolSchema(
            properties = JsonObject(
                mapOf(
                    "note_id" to JsonObject(
                        mapOf(
                            "type" to "string",
                            "description" to "The unique identifier of the created note"
                        ).toJson()
                    )
                )
            ),
            required = emptyList()
        )
    ),
), KoinComponent {

    companion object {
        const val TOOL_NAME = "create_note"
        const val TOOL_DESCRIPTION = "Save a note, idea, or thought for later. Use when the user wants to remember, jot down, or note something."
        private val logger = Logger.withTag("CreateNoteTool")
    }

    @Serializable
    data class CreateNoteArgs(val text: String, val automatic: Boolean = false)
    @Serializable
    data class CreateNoteResult(
        @EncodeDefault(EncodeDefault.Mode.NEVER)
        @SerialName("note_id")
        val noteId: String? = null
    )

    override suspend fun call(jsonInput: String, context: SessionContext): ToolCallResult {
        val createNoteArgs = JsonSnake.decodeFromString<CreateNoteArgs>(jsonInput)
        val text = runCatching { context.userMessageText.await() }
            .onFailure {
                logger.e { "Failed to get user message text" }
            }.getOrNull() ?: run {
                logger.w { "User message text is null, using agent-provided text" }
                createNoteArgs.text.trim()
            }
        logger.d { "Creating note with text length: ${text.length}" }
        return try {
            val noteClient = noteIntegrationFactory.createNoteClient()
            val noteId = noteClient.createNote(text, context.itemSource())
            ToolCallResult(
                JsonSnake.encodeToString(CreateNoteResult(noteId = noteId)),
                SemanticResult.ListItemCreation(text)
            )
        } catch (e: Exception) {
            logger.e(e) { "Failed to create note" }
            ToolCallResult(
                JsonSnake.encodeToString(CreateNoteResult()),
                SemanticResult.GenericFailure("Failed to create note: ${e.message}")
            )
        }
    }
}