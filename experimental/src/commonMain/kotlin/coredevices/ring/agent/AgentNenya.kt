package coredevices.ring.agent

import co.touchlab.kermit.Logger
import coredevices.indexai.agent.Agent
import coredevices.indexai.data.entity.ConversationMessageDocument
import coredevices.indexai.data.entity.MessageRole
import coredevices.mcp.client.McpSession
import coredevices.mcp.data.SemanticResult
import coredevices.ring.api.NenyaClient
import coredevices.mcp.data.ToolCallResult
import io.ktor.http.isSuccess
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.Serializable
import kotlinx.io.IOException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.koin.core.component.KoinComponent

/**
 * Represents a conversation with an agent.
 * @param recordingId The ID of the recording feed entry to which this conversation belongs.
 */
class AgentNenya(
    private val nenyaClient: NenyaClient,
    conversation: List<ConversationMessageDocument>,
    private val useSearchMode: Boolean = false
): KoinComponent, Agent {
    override val label = "Nenya"
    // We don't use StateFlow because we want to suspend on emit if there's backpressure
    private var _conversation = MutableSharedFlow<List<ConversationMessageDocument>>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST).apply {
        tryEmit(conversation)
    }
    override val conversation: SharedFlow<List<ConversationMessageDocument>> get() = _conversation

    companion object Companion {
        private val logger = Logger.withTag(AgentNenya::class.simpleName!!)
        private const val AGENT_CONTEXT = """
You are an assistant primarily designed to help users create and manage notes and reminders. You can
help with a multitude of tasks in addition to this too.
Create a note with the user's input unless they specify a different action, do not assume an action that wasn't explicitly requested, just make a note.
Eagerly run tools to assist the user, including running multiple tools in succession to achieve an overall goal.
Avoid asking follow-up questions unless necessary.
Always lean towards creating a note, for example if the user doesn't ask for a timer don't create a timer, even if the request has a duration in it.
"""
        private const val MAX_TOOL_ITERATIONS = 3
    }

    private suspend fun sendNormal(
        input: String,
        mcpSession: McpSession,
        includePromptsFromMcps: Map<String, Set<String>>,
        skipToolExecution: Boolean
    ) {
        _conversation.emit(_conversation.first() + ConversationMessageDocument(
            role = MessageRole.user,
            content = input
        ))
        val tools = mcpSession.listTools()
        val toolDeclarations = tools.mapNotNull {
            val definition = it.tool.definition
            val compositeName = "${it.integrationName}.${definition.name}"
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
                                    }
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
        var resp = nenyaClient.run(
            null,
            conversationHistory = conversation.first(),
            toolSpecs = toolDeclarations,
            additionalContext = AGENT_CONTEXT+"\n"+mcpSession.getExtraContext(includePromptsFromMcps).orEmpty()
        )
        if (!resp.statusCode.isSuccess()) {
            throw Exception("Failed to run agent: ${resp.statusCode} (${resp.response?.message})")
        }
        _conversation.emit(_conversation.first() + resp.response?.conversation?.last()!!.toConversationMessage())
        var toolIterations = 0
        var lastMessage = resp.response.conversation.last().toConversationMessage()
        while (toolIterations++ < MAX_TOOL_ITERATIONS && lastMessage.role == MessageRole.assistant && !lastMessage.tool_calls.isNullOrEmpty() && !skipToolExecution) {
            // Tools need to be run
            val responses = lastMessage.tool_calls!!.map {
                val args: Map<String, JsonElement> = try {
                    Json.Default.decodeFromString(it.function!!.arguments)
                } catch (e: SerializationException) {
                    logger.w { "Failed to deserialize tool call arguments for tool ${it.function!!.name}" }
                    emptyMap()
                }
                val compositeName = it.function!!.name.split(".", limit = 2)
                if (compositeName.size != 2) {
                    throw Exception("Invalid tool name: ${it.function!!.name}")
                }
                val (integration, tool) = compositeName
                val result = mcpSession.callTool(
                    integration,
                    tool,
                    args
                )
                Pair(it.id, result)
            }
            _conversation.emit(
                _conversation.first().toMutableList().apply {
                    addAll(responses.map { (id, result) ->
                        ConversationMessageDocument(
                            role = MessageRole.tool,
                            content = buildJsonObject {
                                put("result", result.resultString)
                            }.toString(),
                            tool_call_id = id,
                            semantic_result = result.semanticResult
                        )
                    })
                }
            )
            resp = try {
                nenyaClient.run(
                    null,
                    conversation.first(),
                    toolSpecs = toolDeclarations,
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
            _conversation.emit(_conversation.first() + resp.response?.conversation?.last()!!.toConversationMessage())
            lastMessage = resp.response.conversation.last().toConversationMessage()
        }
        if (toolIterations >= MAX_TOOL_ITERATIONS && lastMessage.role == MessageRole.assistant && !lastMessage.tool_calls.isNullOrEmpty() && !skipToolExecution) {
            throw Exception("Exceeded maximum tool iterations")
        }
    }

    private suspend fun sendSearch(input: String, mcpSession: McpSession) {
        _conversation.emit(_conversation.first() + ConversationMessageDocument(
            role = MessageRole.user,
            content = input
        ))
        val resp = try {
            nenyaClient.run(
                null,
                conversationHistory = conversation.first().filter {
                    it.role != MessageRole.tool || it.tool_call_id != null // filter out tool messages that are not tool call responses (e.g. fake search completion message above)
                },
                toolSpecs = emptyList(),
                additionalContext = "Provide a concise summary of the search results to be shown on a small smartwatch screen, with no additional commentary and no markdown formatting.",
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
        val text = resp.response?.conversation?.last()!!.toConversationMessage().content
            ?.replace("**", "") // remove markdown bolding
        _conversation.emit(
            _conversation.first() + ConversationMessageDocument(
                role = MessageRole.tool,
                content = "",
                semantic_result = SemanticResult.SupportingData(text ?: "No results", assistiveOnly = false)
            )
        )
    }

    override suspend fun send(
        input: String,
        mcpSession: McpSession,
        includePromptsFromMcps: Map<String, Set<String>>,
        skipToolExecution: Boolean
    ) {
        when {
            useSearchMode -> sendSearch(input, mcpSession)
            else -> sendNormal(input, mcpSession, includePromptsFromMcps, skipToolExecution)
        }
    }

    override suspend fun addMessage(message: ConversationMessageDocument) {
        _conversation.emit(_conversation.first() + message)
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
    val maximum: Int? = null
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
