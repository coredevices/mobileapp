package coredevices.ring.agent

import co.touchlab.kermit.Logger
import com.cactus.Cactus
import com.cactus.CompletionOptions
import com.cactus.Message
import coredevices.indexai.agent.Agent
import coredevices.indexai.data.entity.ConversationMessageDocument
import coredevices.indexai.data.entity.FunctionToolCall
import coredevices.indexai.data.entity.MessageRole
import coredevices.indexai.data.entity.ToolCall
import coredevices.mcp.client.McpSession
import coredevices.mcp.data.SemanticResult
import coredevices.ring.model.CactusModelProvider
import coredevices.ring.transcription.InferenceBoostProvider
import coredevices.ring.transcription.NoOpInferenceBoostProvider
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.koin.core.component.KoinComponent
import kotlin.time.Clock

class AgentCactus(
    private val modelProvider: CactusModelProvider,
    conversation: List<ConversationMessageDocument>,
    private val inferenceBoost: InferenceBoostProvider = NoOpInferenceBoostProvider()
) : KoinComponent, Agent {
    override val label = "Cactus"
    private var _conversation = MutableSharedFlow<List<ConversationMessageDocument>>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST).apply {
        tryEmit(conversation)
    }
    override val conversation: SharedFlow<List<ConversationMessageDocument>> get() = _conversation

    companion object {
        private val logger = Logger.withTag(AgentCactus::class.simpleName!!)
    }

    private val agentMutex = Mutex()
    private var model: Cactus? = null

    private suspend fun initializeIfNeeded() {
        if (model == null) {
            logger.d { "Initializing CactusAgent for the first time..." }
            val initStart = Clock.System.now()
            val modelPath = modelProvider.getLMModelPath()
            model = Cactus.create(modelPath)
            val initDuration = Clock.System.now() - initStart
            logger.i { "CactusAgent model initialized: $modelPath in $initDuration" }
        }
    }

    override suspend fun send(
        input: String,
        mcpSession: McpSession,
        includePromptsFromMcps: Map<String, Set<String>>,
        skipToolExecution: Boolean
    ) {
        logger.i { "CactusAgent received input: $input" }

        agentMutex.withLock {
            initializeIfNeeded()
            val cactus = model ?: throw IllegalStateException("CactusAgent model not initialized")

            // Convert MCP tool definitions to Cactus List<Map<String, Any>> format
            val mcpTools = mcpSession.listTools()
            val tools = mcpTools.map { (parentName, tool) ->
                val definition = tool.definition
                val required = definition.inputSchema.required ?: emptyList()
                val properties = definition.inputSchema.properties?.mapValues { (_, param) ->
                    val paramMap = mutableMapOf<String, Any>(
                        "type" to (param.jsonObject["type"]?.jsonPrimitive?.content ?: "string")
                    )
                    param.jsonObject["description"]?.jsonPrimitive?.content?.let {
                        paramMap["description"] = it
                    }
                    paramMap as Map<String, Any>
                } ?: emptyMap()
                mapOf<String, Any>(
                    "type" to "function",
                    "function" to mapOf(
                        "name" to "$parentName.${definition.name}",
                        "description" to (definition.description ?: ""),
                        "parameters" to mapOf(
                            "type" to "object",
                            "properties" to properties,
                            "required" to required
                        )
                    )
                )
            }

            tools.forEach { tool ->
                @Suppress("UNCHECKED_CAST")
                val fn = tool["function"] as? Map<String, Any>
                logger.i { "CactusAgent tool available: ${fn?.get("name")}" }
            }

            val messages = listOf(
                Message.system("""
                You are an assistant primarily designed to help users create and manage notes and reminders. You can
                help with a multitude of tasks in addition to this too.
                Create a note with the user's input unless they specify a different action.
                Avoid asking follow-up questions unless necessary.
                ${mcpSession.getExtraContext(includePromptsFromMcps)?.ifBlank { null } ?: ""}
            """.trimIndent()),
                Message.user("/no_think $input")
            )

            _conversation.emit(_conversation.first() + ConversationMessageDocument(
                role = MessageRole.user,
                content = input
            ))

            inferenceBoost.acquire()
            val result = try {
                cactus.complete(
                    messages = messages,
                    options = CompletionOptions(
                        maxTokens = 200,
                        temperature = 0.1f,
                        forceTools = true
                    ),
                    tools = tools
                )
            } finally {
                inferenceBoost.release()
            }

            // Parse function calls from result
            val toolCalls = result.functionCalls?.mapNotNull { call ->
                @Suppress("UNCHECKED_CAST")
                val name = call["name"] as? String ?: return@mapNotNull null
                val arguments = call["arguments"]
                Triple(name, arguments, call)
            } ?: emptyList()

            _conversation.emit(_conversation.first() + ConversationMessageDocument(
                role = MessageRole.assistant,
                content = result.text.ifBlank { null },
                tool_calls = toolCalls.map { (name, args, _) ->
                    ToolCall(
                        id = name,
                        type = "function",
                        function = FunctionToolCall(
                            name = name,
                            arguments = args.toString()
                        )
                    )
                },
            ))

            if (toolCalls.isNotEmpty() && !skipToolExecution) {
                for ((name, arguments, _) in toolCalls) {
                    val toolCompositeName = name.split(".", limit = 2)
                    if (toolCompositeName.size != 2) {
                        logger.w { "Invalid tool name format: $name" }
                        _conversation.emit(
                            _conversation.first().toMutableList().apply {
                                add(ConversationMessageDocument(
                                    role = MessageRole.tool,
                                    tool_call_id = name,
                                    content = """{"error": "Invalid tool name"}""",
                                    semantic_result = SemanticResult.GenericFailure(
                                        "Invalid tool call",
                                        llmRecoverable = true
                                    )
                                ))
                            }
                        )
                        continue
                    }
                    val (parent, toolName) = toolCompositeName
                    @Suppress("UNCHECKED_CAST")
                    val jsonInput = when (arguments) {
                        is Map<*, *> -> buildJsonObject {
                            (arguments as Map<String, Any>).forEach { (k, v) ->
                                put(k, JsonPrimitive(v.toString()))
                            }
                        }
                        else -> buildJsonObject {}
                    }
                    val toolResult = mcpSession.callTool(
                        integrationName = parent,
                        toolName = toolName,
                        jsonInput = jsonInput,
                        requireExists = false
                    )
                    _conversation.emit(
                        _conversation.first().toMutableList().apply {
                            add(ConversationMessageDocument(
                                role = MessageRole.tool,
                                tool_call_id = name,
                                content = toolResult.resultString,
                                semantic_result = toolResult.semanticResult
                            ))
                        }
                    )
                }
            }
        }
    }

    override suspend fun addMessage(message: ConversationMessageDocument) {
        _conversation.emit(_conversation.first() + message)
    }
}
