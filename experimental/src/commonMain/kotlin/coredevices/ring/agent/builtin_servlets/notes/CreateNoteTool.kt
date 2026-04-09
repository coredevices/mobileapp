package coredevices.ring.agent.builtin_servlets.notes

import co.touchlab.kermit.Logger
import coredevices.indexai.util.JsonSnake
import coredevices.mcp.BuiltInMcpTool
import coredevices.mcp.data.SemanticResult
import coredevices.mcp.data.ToolCallResult
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import io.modelcontextprotocol.kotlin.sdk.types.toJson
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import org.koin.core.component.KoinComponent

class CreateNoteTool(private val noteIntegrationFactory: NoteIntegrationFactory): BuiltInMcpTool(
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
        const val TOOL_DESCRIPTION = "Create a new note with the given user text"
    }

    @Serializable
    data class CreateNoteArgs(val text: String, val automatic: Boolean = false)
    @Serializable
    data class CreateNoteResult(
        @EncodeDefault(EncodeDefault.Mode.NEVER)
        @SerialName("note_id")
        val noteId: String? = null
    )

    override suspend fun call(jsonInput: String): ToolCallResult {
        val createNoteArgs = JsonSnake.decodeFromString<CreateNoteArgs>(jsonInput)
        Logger.d { "Creating note with text: ${createNoteArgs.text}" }
        return try {
            val noteClient = noteIntegrationFactory.createNoteClient()
            val noteId = noteClient.createNote(createNoteArgs.text)
            ToolCallResult(
                JsonSnake.encodeToString(CreateNoteResult(noteId = noteId)),
                SemanticResult.ListItemCreation(createNoteArgs.text)
            )
        } catch (e: Exception) {
            Logger.e(e) { "Failed to create note" }
            ToolCallResult(
                JsonSnake.encodeToString(CreateNoteResult()),
                SemanticResult.GenericFailure("Failed to create note: ${e.message}")
            )
        }
    }
}