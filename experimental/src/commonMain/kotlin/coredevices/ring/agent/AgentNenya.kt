package coredevices.ring.agent

import co.touchlab.kermit.Logger
import coredevices.indexai.agent.AgentToolCall
import coredevices.indexai.agent.IterativeAgent
import coredevices.indexai.data.entity.ConversationMessageDocument
import coredevices.indexai.data.entity.MessageRole
import coredevices.mcp.SessionContext
import coredevices.mcp.client.McpSession
import coredevices.mcp.client.McpSessionTool
import coredevices.mcp.data.ToolCallResult
import coredevices.ring.api.NenyaClient
import coredevices.ring.api.NenyaModel
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
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.koin.core.component.KoinComponent

/**
 * Online agent backed by the Nenya HTTP API. Iterative: tool results are fed
 * back to the model until it stops calling tools (capped at [MAX_TOOL_ITERATIONS]).
 * Search mode bypasses the shared tool harness entirely.
 */
open class AgentNenya(
    private val nenyaClient: NenyaClient,
    private val context: String,
    private val model: NenyaModel,
    conversation: List<ConversationMessageDocument>,
): KoinComponent, IterativeAgent(conversation) {
    override val label = "Nenya"

    override val logger: Logger = Logger.withTag("AgentNenya")

    /**
     * Sanitizing to the most strict subset providers use, which is the MCP spec (a-z,A-Z,_,-,.) + no dots (.),
     * + 64 max chars. Covers Gemini + Claude
     */
    private fun sanitizeToolName(name: String): String {
        return name.trim().replace(Regex("[^a-zA-Z0-9_-]"), "_").take(64)
    }

    private fun prepareTools(tools: List<McpSessionTool>): List<ToolDeclaration> {
        return tools.mapNotNull {
            val definition = it.tool.definition
            val compositeNameRaw = "${it.integrationName}__${definition.name}"
            val compositeName = sanitizeToolName(compositeNameRaw)
            if (compositeName != compositeNameRaw) {
                logger.w { "Tool name '${definition.name}' from integration '${it.integrationName}' was sanitized to '$compositeName' to meet provider requirements" }
            }
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
                                    description = param.jsonObject["description"]?.jsonPrimitive?.content ?: "",
                                    enum = param.jsonObject["enum"]?.jsonArray?.map { it.toString().trim('"') },
                                    minimum = param.jsonObject["minimum"]?.toString()?.toIntOrNull(),
                                    maximum = param.jsonObject["maximum"]?.toString()?.toIntOrNull(),
                                    anyOf = param.jsonObject["anyOf"]?.jsonArray?.mapNotNull { anyOfParam ->
                                        val p = anyOfParam.jsonObject
                                        val type = p["type"] ?: return@mapNotNull null
                                        FunctionDeclarationParameter(
                                            type = type,
                                            description = p["description"]?.jsonPrimitive?.content,
                                            enum = p["enum"]?.jsonArray?.map { it.toString().trim('"') },
                                            minimum = p["minimum"]?.toString()?.toIntOrNull(),
                                            maximum = p["maximum"]?.toString()?.toIntOrNull(),
                                            items = p["items"]?.jsonObject ?: if (p["type"]?.toString()?.trim('"') == "array") {
                                                buildJsonObject {
                                                    put("type", JsonPrimitive("string")) // default to string arrays if items schema is missing
                                                }
                                            } else null,
                                        )
                                    }?.takeIf { it.isNotEmpty() },
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
        sessionContext: SessionContext,
        includePromptsFromMcps: Map<String, Set<String>>
    ): ConversationMessageDocument {
        logger.v { "Running inference with model $model, tool count = ${tools.size}, context length = ${context.length}, conversation history length = ${history.size}" }
        val tools = prepareTools(tools)
        val resp = try {
            nenyaClient.run(
                conversationHistory = history,
                toolSpecs = tools,
                additionalContext = context + "\n" + mcpSession.getExtraContext(sessionContext, includePromptsFromMcps).orEmpty(),
                model = model
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
    val description: String? = null,
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