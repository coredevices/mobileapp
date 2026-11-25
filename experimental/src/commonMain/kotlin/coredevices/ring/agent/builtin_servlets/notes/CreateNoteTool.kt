package coredevices.ring.agent.builtin_servlets.notes

import co.touchlab.kermit.Logger
import coredevices.indexai.util.JsonSnake
import coredevices.mcp.BuiltInMcpTool
import coredevices.mcp.SessionContext
import coredevices.mcp.data.SemanticResult
import coredevices.mcp.data.ToolCallResult
import coredevices.ring.agent.builtin_servlets.DESTINATION_ARG
import coredevices.ring.agent.builtin_servlets.NOTE_DESTINATION_DESCRIPTION
import coredevices.ring.agent.integrations.itemSource
import coredevices.ring.agent.integrations.providers.IntegrationProviderRepository
import coredevices.ring.agent.integrations.providers.IntegrationSupport
import coredevices.ring.agent.integrations.providers.externalDestination
import coredevices.ring.agent.toolschema.builtinToolDefinitions
import coredevices.toolschema.ToolDefinition
import coredevices.toolschema.ToolParam
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.koin.core.component.KoinComponent

class CreateNoteTool(private val providers: IntegrationProviderRepository) : BuiltInMcpTool(
    definition = builtinToolDefinitions.getValue(TOOL_NAME),
), KoinComponent {

    companion object {
        const val TOOL_NAME = "create_note"
        const val TOOL_DESCRIPTION = "Save a note, idea, or thought for later. Use when the user wants to jot down or note something, or remember a fact or piece of information. Not for 'remember to do something' requests - those are reminders."
        private val logger = Logger.withTag("CreateNoteTool")
    }

    @ToolDefinition(
        servlet = NoteServlet.NAME,
        name = TOOL_NAME,
        description = TOOL_DESCRIPTION,
        output = CreateNoteResult::class,
    )
    @Serializable
    data class CreateNoteArgs(
        @ToolParam("The text of the note, usually a direct quote from the user")
        val text: String,
        val automatic: Boolean = false,
        @ToolParam(NOTE_DESTINATION_DESCRIPTION)
        val destination: String? = null,
    )
    @Serializable
    data class CreateNoteResult(
        @EncodeDefault(EncodeDefault.Mode.NEVER)
        @SerialName("note_id")
        @ToolParam("The unique identifier of the created note")
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
        val provider = providers.destinationProvider(createNoteArgs.destination, IntegrationSupport.Note)
            ?: return ToolCallResult(
                JsonSnake.encodeToString(CreateNoteResult()),
                SemanticResult.GenericFailure(
                    "Unknown note destination '${createNoteArgs.destination}'. Use a name from the " +
                        "context, or omit $DESTINATION_ARG to use the default.",
                    llmRecoverable = true
                )
            )
        return try {
            val noteId = providers.noteIntegration(provider).createNote(text, context.itemSource())
            ToolCallResult(
                JsonSnake.encodeToString(CreateNoteResult(noteId = noteId)),
                SemanticResult.ListItemCreation(text, destination = provider.externalDestination)
            )
        } catch (e: Exception) {
            logger.e(e) { "Failed to create note" }
            ToolCallResult(
                JsonSnake.encodeToString(CreateNoteResult()),
                SemanticResult.GenericFailure(e.message)
            )
        }
    }
}