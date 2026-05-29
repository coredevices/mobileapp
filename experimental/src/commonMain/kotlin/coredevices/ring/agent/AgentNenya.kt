package coredevices.ring.agent

import co.touchlab.kermit.Logger
import coredevices.indexai.agent.AgentToolCall
import coredevices.indexai.agent.IterativeAgent
import coredevices.indexai.data.entity.ConversationMessageDocument
import coredevices.indexai.data.entity.MessageRole
import coredevices.mcp.client.McpSession
import coredevices.mcp.client.McpSessionTool
import coredevices.mcp.data.SemanticResult
import coredevices.mcp.data.ToolCallResult
import coredevices.ring.api.NenyaClient
import coredevices.ring.database.room.repository.ItemRepository
import coredevices.ring.service.indexfeed.ItemFactory
import coredevices.ring.service.indexfeed.RecordingSessionContext
import io.ktor.http.isSuccess
import kotlinx.io.IOException
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.koin.core.component.KoinComponent

/**
 * Online agent backed by the Nenya HTTP API. Iterative: tool results are fed
 * back to the model until it stops calling tools (capped at [MAX_TOOL_ITERATIONS]).
 * Search mode bypasses the shared tool harness entirely.
 */
class AgentNenya(
    private val nenyaClient: NenyaClient,
    private val itemFactory: ItemFactory,
    private val itemRepository: ItemRepository,
    conversation: List<ConversationMessageDocument>,
    private val useSearchMode: Boolean = false
): KoinComponent, IterativeAgent(conversation) {
    override val label = "Nenya"

    override val logger: Logger = Logger.withTag("AgentNenya")
    companion object Companion {
        private const val AGENT_CONTEXT = """
You are primarily tasked with helping users create and manage notes, lists, and reminders. You can
help with a multitude of tasks in addition to this too.
## Interpretation guidelines:
 - Create a note with the user's input unless they specify a different action, do not assume an action that wasn't explicitly requested, just make a note.
 - Avoid asking follow-up questions unless necessary.
 - Always lean towards creating a note, for example if the user doesn't ask for a timer don't create a timer, even if the request has a duration in it.
 - Prioritise the first action a user requests, for example 'remind me tomorrow to message John' should create a reminder and not attempt a message.
 - When users provide multiple items, for example 'remind me to buy milk and bread tomorrow', or 'add Apple and China to my book list', take a single action with
both as the content unless it's clearly two separate actions, for example 'remind me to buy milk tomorrow and bread the day after' should create two reminders.

## Response and action guidelines:
 - Eagerly run tools to assist the user by gathering required information and taking actions.
 - Avoid additional commentary after taking a final action unless the user asked for it, e.g. when asking a question. The user can see actions without you notifying them.
 - Always take an action, even if you just fall back to creating a note with what the user said.
"""
    }

    private fun prepareTools(tools: List<McpSessionTool>): List<ToolDeclaration> {
        return tools.mapNotNull {
            val definition = it.tool.definition
            val compositeName = "${it.integrationName}__${definition.name}"
            try {
                ToolDeclaration(
                    function = FunctionDeclaration(
                        name = compositeName,
                        description = definition.description ?: "",
                        parameters = FunctionDeclarationParameters(
                            properties = definition.inputSchema.properties?.mapValues { (key, param) ->
                                FunctionDeclarationParameter(
                                    type = param.jsonObject["type"] ?: run {
                                        if (param.jsonObject["anyOf"] != null) {
                                            null
                                        } else {
                                            throw Exception("Parameter $key has no type")
                                        }
                                    },
                                    description = param.jsonObject["description"]?.toString() ?: "",
                                    enum = param.jsonObject["enum"]?.jsonArray?.map { it.toString().trim('"') },
                                    minimum = param.jsonObject["minimum"]?.toString()?.toIntOrNull(),
                                    maximum = param.jsonObject["maximum"]?.toString()?.toIntOrNull(),
                                    anyOf = param.jsonObject["anyOf"]?.jsonArray?.map { anyOfParam ->
                                        val p = anyOfParam.jsonObject
                                        FunctionDeclarationParameter(
                                            type = p["type"]!!,
                                            description = p["description"]?.toString(),
                                            enum = p["enum"]?.jsonArray?.map { it.toString().trim('"') },
                                            minimum = p["minimum"]?.toString()?.toIntOrNull(),
                                            maximum = p["maximum"]?.toString()?.toIntOrNull(),
                                        )
                                    },
                                    items = param.jsonObject["items"]?.jsonObject ?: if (param.jsonObject["type"]?.toString() == "array") {
                                        buildJsonObject {
                                            put("type", JsonPrimitive("string")) // default to string arrays if items schema is missing
                                        }
                                    } else null
                                )
                            } ?: emptyMap(),
                            required = definition.inputSchema.required ?: emptyList(),
                            additionalProperties = false
                        )
                    )
                )
            } catch (e: Exception) {
                logger.e(e) { "Failed to create tool declaration for tool ${compositeName}: ${e.message}\n${definition}" }
                null
            }
        }
    }

    override suspend fun runInference(
        input: String,
        history: List<ConversationMessageDocument>,
        tools: List<McpSessionTool>,
        mcpSession: McpSession,
        includePromptsFromMcps: Map<String, Set<String>>,
    ): ConversationMessageDocument {
        val tools = prepareTools(tools)
        val resp = try {
            nenyaClient.run(
                conversationHistory = history,
                toolSpecs = tools,
                additionalContext = AGENT_CONTEXT + "\n" + mcpSession.getExtraContext(includePromptsFromMcps).orEmpty()
            )
        } catch (e: IOException) {
            throw AgentNetworkException("Network error when running agent: ${e.message}", e)
        }
        if (!resp.statusCode.isSuccess()) {
            if (resp.statusCode.value in 501..504) {
                throw AgentNetworkException("Network error at gateway when running agent: ${resp.statusCode} (${resp.response?.message})")
            } else {
                throw Exception("Failed to run agent: ${resp.statusCode} (${resp.response?.message})")
            }
        }
        return resp.response?.conversation?.last()!!.toConversationMessage(resp.response.language_model_used)
    }

    override fun decodeToolCalls(
        assistantMessage: ConversationMessageDocument
    ): List<AgentToolCall> {
        if (assistantMessage.role != MessageRole.assistant) return emptyList()
        return (assistantMessage.tool_calls ?: emptyList()).map { call ->
            val args: Map<String, JsonElement> = try {
                Json.Default.decodeFromString(call.function!!.arguments)
            } catch (e: SerializationException) {
                logger.w { "Failed to deserialize tool call arguments for tool ${call.function!!.name}" }
                emptyMap()
            }
            val composite = call.function!!.name.split("__", limit = 2)
            if (composite.size != 2) {
                throw Exception("Invalid tool name: ${call.function!!.name}")
            }
            AgentToolCall(
                id = call.id,
                integrationName = composite[0],
                toolName = composite[1],
                arguments = args
            )
        }
    }

    override fun encodeToolResultContent(result: ToolCallResult): String =
        buildJsonObject { put("result", result.resultString) }.toString()

    override suspend fun send(
        input: String,
        mcpSession: McpSession,
        includePromptsFromMcps: Map<String, Set<String>>,
        skipToolExecution: Boolean
    ) {
        if (useSearchMode) {
            sendSearch(input, mcpSession)
        } else {
            super.send(input, mcpSession, includePromptsFromMcps, skipToolExecution)
        }
    }

    private suspend fun sendSearch(input: String, mcpSession: McpSession) {
        emit(ConversationMessageDocument(role = MessageRole.user, content = input))
        val resp = try {
            nenyaClient.run(
                conversationHistory = currentConversation().filter {
                    it.role != MessageRole.tool || it.tool_call_id != null // filter out tool messages that are not tool call responses (e.g. fake search completion message above)
                },
                toolSpecs = emptyList(),
                additionalContext = "Provide a concise answer to the query after searching the internet, to be shown on a small display. The answer should have no additional commentary or markdown formatting.",
                searchMode = true
            )
        } catch (e: IOException) {
            throw AgentNetworkException("Network error when running agent: ${e.message}", e)
        }
        if (!resp.statusCode.isSuccess()) {
            if (resp.statusCode.value in 501..504) {
                throw AgentNetworkException("Network error at gateway when running agent: ${resp.statusCode} (${resp.response?.message})")
            } else {
                throw Exception("Failed to run agent: ${resp.statusCode} (${resp.response?.message})")
            }
        }
        val text = resp.response?.conversation?.last()!!.toConversationMessage(resp.response.language_model_used).content
            ?.replace("**", "") // remove markdown bolding
        emit(
            ConversationMessageDocument(
                role = MessageRole.tool,
                content = "",
                semantic_result = SemanticResult.SupportingData(text ?: "No results", assistiveOnly = false)
            )
        )

        currentSessionContext()?.let { ctx ->
            runCatching {
                itemRepository.setItem(
                    itemFactory.simpleUid(),
                    itemFactory.answerItem(
                        sourceRecordingId = ctx.sourceRecordingId,
                        createdAt = ctx.createdAt,
                        question = input,
                        answer = text ?: "No results"
                    )
                )
            }
        }
    }
}

@Serializable
data class ToolDeclaration(
    val function: FunctionDeclaration? = null,
    val type: String = "function",
)

@Serializable
data class FunctionDeclaration(
    val name: String,
    val description: String,
    val parameters: FunctionDeclarationParameters,
    val strict: Boolean = true
)

@Serializable
data class FunctionDeclarationParameters(
    val properties: Map<String, FunctionDeclarationParameter>,
    val required: List<String> = emptyList(),
    val additionalProperties: Boolean = false,
    val type: String = "object"
)

@Serializable
data class FunctionDeclarationParameter(
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val type: JsonElement? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val description: String?,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val enum: List<String>? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val anyOf: List<FunctionDeclarationParameter>? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val minimum: Int? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val maximum: Int? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val items: JsonObject? = null
)

@Serializable
data class FunctionCallArgs(
    val method: String,
    val params: FunctionArgs
)

@Serializable
data class FunctionArgs(
    val name: String,
    val arguments: JsonObject
)
