package coredevices.indexai.agent

import co.touchlab.kermit.Logger
import coredevices.indexai.data.entity.ConversationMessageDocument
import coredevices.indexai.data.entity.MessageRole
import coredevices.mcp.client.McpSession
import coredevices.mcp.client.McpSessionTool
import coredevices.mcp.data.SemanticResult
import coredevices.mcp.data.ToolCallResult
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonElement

/**
 * Model-agnostic agent harness: maintains the conversation flow, emits the user
 * message, runs inference, dispatches tool calls back through [McpSession], and
 * (optionally) loops until the model stops calling tools.
 */
abstract class IterativeAgent(
    initialConversation: List<ConversationMessageDocument>
) : Agent {
    private val _conversation = MutableSharedFlow<List<ConversationMessageDocument>>(
        replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST
    ).apply { tryEmit(initialConversation) }
    override val conversation: SharedFlow<List<ConversationMessageDocument>> get() = _conversation

    protected open val logger = Logger.withTag("IterativeAgent")

    protected suspend fun currentConversation(): List<ConversationMessageDocument> =
        _conversation.first()

    protected suspend fun emit(message: ConversationMessageDocument) {
        _conversation.emit(currentConversation() + message)
    }

    protected suspend fun emitAll(messages: List<ConversationMessageDocument>) {
        _conversation.emit(currentConversation() + messages)
    }

    override suspend fun addMessage(message: ConversationMessageDocument) = emit(message)

    // ---- model-specific contract ----

    /** Run one inference round and build the assistant message. Must not throw on
     *  tool-call issues — those surface from [decodeToolCalls] after the emit. */
    protected abstract suspend fun runInference(
        input: String,
        history: List<ConversationMessageDocument>,
        tools: List<McpSessionTool>,
        mcpSession: McpSession,
        includePromptsFromMcps: Map<String, Set<String>>,
    ): ConversationMessageDocument

    /** Decode the (already-emitted) assistant message's tool calls into
     *  dispatchable calls. May throw to abort the run on a malformed call. */
    protected abstract fun decodeToolCalls(
        assistantMessage: ConversationMessageDocument
    ): List<AgentToolCall>

    /** Max tool-execution rounds before erroring. */
    protected open val maxToolRounds: Int get() = 3

    /** Lifecycle prep before the loop. */
    protected open suspend fun prepare() {}

    /** How a tool result is encoded into the tool message `content`. */
    protected open fun encodeToolResultContent(result: ToolCallResult): String =
        result.resultString

    override suspend fun send(
        input: String,
        mcpSession: McpSession,
        includePromptsFromMcps: Map<String, Set<String>>,
        skipToolExecution: Boolean,
    ) {
        runLoop(input, mcpSession, includePromptsFromMcps, skipToolExecution)
    }

    private suspend fun runLoop(
        input: String,
        mcpSession: McpSession,
        includePromptsFromMcps: Map<String, Set<String>>,
        skipToolExecution: Boolean,
    ) {
        prepare()
        mcpSession.openSession()
        try {
            emit(ConversationMessageDocument(role = MessageRole.user, content = input))
            val tools = mcpSession.listTools()

            var round = 0
            while (true) {
                val assistantMessage = runInference(
                    input, currentConversation(), tools, mcpSession, includePromptsFromMcps
                )
                emit(assistantMessage)
                val toolCalls = decodeToolCalls(assistantMessage)

                if (toolCalls.isEmpty()) return
                if (skipToolExecution) return
                if (round >= maxToolRounds) throw Exception("Exceeded maximum tool iterations")

                val results = toolCalls.map { call ->
                    val r = mcpSession.callTool(
                        call.integrationName, call.toolName, call.arguments, requireExists = false
                    )
                    ConversationMessageDocument(
                        role = MessageRole.tool,
                        content = encodeToolResultContent(r),
                        tool_call_id = call.id,
                        semantic_result = r.semanticResult,
                    )
                }
                emitAll(results)
                val fatalError = results.firstOrNull {
                    it.semantic_result is SemanticResult.GenericFailure && !it.semantic_result.llmRecoverable
                }
                if (fatalError != null) {
                    logger.w { "Aborting tool loop due to error semantic result" }
                    return
                }
                round++
            }
        } finally {
            mcpSession.closeSession()
        }
    }
}

/** A tool call normalized to something [McpSession.callTool] can dispatch directly. */
data class AgentToolCall(
    val id: String,
    val integrationName: String,
    val toolName: String,
    val arguments: Map<String, JsonElement>,
)