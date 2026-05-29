package coredevices.ring.agent

import co.touchlab.kermit.Logger
import com.cactus.cactusComplete
import com.cactus.cactusInit
import coredevices.indexai.agent.AgentToolCall
import coredevices.indexai.agent.IterativeAgent
import coredevices.indexai.data.entity.ConversationMessageDocument
import coredevices.indexai.data.entity.FunctionToolCall
import coredevices.indexai.data.entity.MessageRole
import coredevices.indexai.data.entity.ToolCall
import coredevices.mcp.client.McpSession
import coredevices.mcp.client.McpSessionTool
import coredevices.ring.model.CactusModelProvider
import coredevices.ring.transcription.InferenceBoostProvider
import coredevices.util.CoreConfigFlow
import coredevices.ring.transcription.NoOpInferenceBoostProvider
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import kotlin.time.Clock

class AgentCactus(
    private val modelProvider: CactusModelProvider,
    conversation: List<ConversationMessageDocument>,
    private val inferenceBoost: InferenceBoostProvider = NoOpInferenceBoostProvider()
) : KoinComponent, IterativeAgent(conversation) {
    override val label = "Cactus"

    override val logger = Logger.withTag("AgentCactus")

    /** Cactus tools JSON plus the short-name -> integration-name map for mapping calls back. */
    data class CactusTools(val toolsJson: String, val parentMap: Map<String, String>)

    private val agentMutex = Mutex()
    private var modelHandle: Long = 0L

    override suspend fun prepare() {
        agentMutex.withLock {
            if (modelHandle == 0L) {
                logger.d { "Initializing CactusAgent for the first time..." }
                val initStart = Clock.System.now()
                val modelPath = modelProvider.getLMModelPath()
                modelHandle = cactusInit(modelPath, null, false)
                val initDuration = Clock.System.now() - initStart
                logger.i { "CactusAgent model initialized: $modelPath in $initDuration" }
            }
        }
    }

    // Use SHORT tool names (e.g. "create_note") not composite names
    // (e.g. "builtin_note.create_note") because Needle's constrained decoding
    // grammar is built from these names and the model was trained on short names.
    private fun prepareTools(tools: List<McpSessionTool>): CactusTools {
        val parentMap = mutableMapOf<String, String>()
        val toolsJson = buildJsonArray {
            tools.forEach { (parentName, tool) ->
                val definition = tool.definition
                val required = definition.inputSchema.required ?: emptyList()
                parentMap[definition.name] = parentName
                add(buildJsonObject {
                    put("type", "function")
                    put("function", buildJsonObject {
                        put("name", definition.name)
                        put("description", definition.description ?: "")
                        put("parameters", buildJsonObject {
                            put("type", "object")
                            put("properties", buildJsonObject {
                                definition.inputSchema.properties?.forEach { (propName, param) ->
                                    put(propName, buildJsonObject {
                                        put("type", param.jsonObject["type"]?.jsonPrimitive?.content ?: "string")
                                        param.jsonObject["description"]?.jsonPrimitive?.content?.let {
                                            put("description", it)
                                        }
                                    })
                                }
                            })
                            put("required", buildJsonArray {
                                required.forEach { add(JsonPrimitive(it)) }
                            })
                        })
                    })
                })
            }
        }.toString()

        tools.forEach { (parentName, tool) ->
            logger.i { "CactusAgent tool available: $parentName.${tool.definition.name}" }
        }
        return CactusTools(toolsJson, parentMap)
    }

    override suspend fun runInference(
        input: String,
        history: List<ConversationMessageDocument>,
        tools: List<McpSessionTool>,
        mcpSession: McpSession,
        includePromptsFromMcps: Map<String, Set<String>>,
    ): ConversationMessageDocument {
        logger.i { "CactusAgent received input: ${if (get<CoreConfigFlow>().value.obfuscateSensitiveLogs) "[${input.length} chars redacted]" else input}" }

        val handle = modelHandle
        if (handle == 0L) throw IllegalStateException("CactusAgent model not initialized")

        val messagesJson = buildJsonArray {
            add(buildJsonObject {
                put("role", "user")
                put("content", input)
            })
        }.toString()

        val optionsJson = buildJsonObject {
            put("max_tokens", 256)
            put("temperature", 0.0)
            put("tool_rag_top_k", 0)
        }.toString()

        val preparedTools = prepareTools(tools)
        val resultJson = agentMutex.withLock {
            inferenceBoost.acquire()
            try {
                cactusComplete(handle, messagesJson, optionsJson, preparedTools.toolsJson, null)
            } finally {
                inferenceBoost.release()
            }
        }

        val resultObj = Json.parseToJsonElement(resultJson).jsonObject
        val resultText = resultObj["response"]?.jsonPrimitive?.content ?: ""
        val functionCalls = resultObj["function_calls"]?.jsonArray

        // Model returns SHORT names; map back to composite "parent.short".
        val parsed: List<Pair<String, JsonObject?>> = functionCalls?.mapNotNull { callElement ->
            val call = callElement.jsonObject
            val shortName = call["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val parent = preparedTools.parentMap[shortName]
            if (parent == null) {
                logger.w { "Unknown tool name from model: $shortName" }
                return@mapNotNull null
            }
            "$parent.$shortName" to call["arguments"]?.jsonObject
        } ?: emptyList()

        return ConversationMessageDocument(
            role = MessageRole.assistant,
            content = resultText.ifBlank { null },
            tool_calls = parsed.map { (name, args) ->
                ToolCall(
                    id = name,
                    type = "function",
                    function = FunctionToolCall(name = name, arguments = args.toString())
                )
            },
            language_model_used = "cactus-${modelProvider.getLMModelPath().substringAfterLast("/")}"
        )
    }

    // Cactus already mapped SHORT -> composite "parent.short" while building the
    // assistant message; just split it back out for dispatch.
    override fun decodeToolCalls(
        assistantMessage: ConversationMessageDocument
    ): List<AgentToolCall> {
        return (assistantMessage.tool_calls ?: emptyList()).mapNotNull { call ->
            val name = call.function?.name ?: call.id
            val parts = name.split(".", limit = 2)
            if (parts.size != 2) return@mapNotNull null
            val arguments = call.function?.arguments
                ?.let { runCatching { Json.parseToJsonElement(it) }.getOrNull() }
                as? JsonObject ?: JsonObject(emptyMap())
            AgentToolCall(
                id = call.id,
                integrationName = parts[0],
                toolName = parts[1],
                arguments = arguments
            )
        }
    }
}
