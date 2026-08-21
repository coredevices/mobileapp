package coredevices.ring.agent.builtin_servlets.reminders

import co.touchlab.kermit.Logger
import coredevices.indexai.util.JsonSnake
import coredevices.mcp.BuiltInMcpTool
import coredevices.mcp.SessionContext
import coredevices.mcp.data.SemanticResult
import coredevices.mcp.data.ToolCallResult
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import io.modelcontextprotocol.kotlin.sdk.types.toJson
import kotlinx.coroutines.flow.first
import coredevices.ring.database.room.repository.ListRepository
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class CreateListTool : BuiltInMcpTool(
    definition = Tool(
        name = TOOL_NAME,
        description = TOOL_DESCRIPTION,
        inputSchema = ToolSchema(
            properties = JsonObject(
                mapOf(
                    "list_name" to JsonObject(
                        mapOf(
                            "type" to "string",
                            "description" to "The name of the new list, e.g. 'travel' for " +
                                    "'Create a new list called travel'"
                        ).toJson()
                    ),
                )
            ),
            required = listOf("list_name")
        )
    ),
), KoinComponent {
    private val listRepo: ListRepository by inject()

    companion object {
        const val TOOL_NAME = "create_list"
        const val TOOL_DESCRIPTION = "Create a new, empty list. ONLY use this when the user's " +
                "message contains the exact phrase 'Create a new list', e.g. " +
                "'Create a new list travel' or 'Create a new list called travel'. " +
                "Never use it for any other phrasing, and never use it to add items to a list."
        private val logger = Logger.withTag("CreateListTool")

        private val triggerRegex = Regex("""\bcreate a new list\b""", RegexOption.IGNORE_CASE)

        fun isTriggerPhrase(text: String?): Boolean =
            text != null && triggerRegex.containsMatchIn(text)
    }

    @Serializable
    private data class CreateListArgs(val list_name: String)

    @Serializable
    data class CreateListResult(
        val success: Boolean,
        val errorMessage: String? = null,
        val id: String? = null,
    )

    private fun failure(errorMessage: String) = ToolCallResult(
        JsonSnake.encodeToString(CreateListResult(success = false, errorMessage = errorMessage)),
        SemanticResult.GenericFailure(errorMessage, llmRecoverable = true)
    )

    override suspend fun call(jsonInput: String, context: SessionContext): ToolCallResult {
        val args = runCatching { JsonSnake.decodeFromString<CreateListArgs>(jsonInput) }
            .getOrElse { return failure("Invalid arguments: ${it.message}") }
        val transcript = runCatching { context.userMessageText.await() }.getOrNull()
        if (!isTriggerPhrase(transcript)) {
            logger.w { "Refusing create_list: transcript doesn't start with the trigger phrase" }
            return failure(
                "Lists can only be created when the user's message starts with " +
                        "'Create a new list'. Do not create a list; use another tool."
            )
        }
        val title = args.list_name.trim()
        if (title.isEmpty()) {
            return failure("list_name must not be empty")
        }
        return try {
            val existing = listRepo.getAllFlow().first()
                .firstOrNull { !it.deleted && it.title.equals(title, ignoreCase = true) }
            if (existing != null) {
                return failure("A list named '${existing.title}' already exists")
            }
            val id = listRepo.createList(title)
            ToolCallResult(
                JsonSnake.encodeToString(CreateListResult(success = true, id = id)),
                SemanticResult.ActionLogged(
                    toolName = TOOL_NAME,
                    title = "Created list '$title'",
                    success = true,
                )
            )
        } catch (e: Exception) {
            logger.e(e) { "Failed to create list" }
            failure("Failed to create list: ${e.message}")
        }
    }
}
