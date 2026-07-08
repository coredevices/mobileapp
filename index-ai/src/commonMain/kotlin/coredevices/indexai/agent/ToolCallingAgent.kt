package coredevices.indexai.agent

import co.touchlab.kermit.Logger
import coredevices.indexai.data.entity.ConversationMessageDocument
import coredevices.indexai.data.entity.MessageRole
import coredevices.mcp.SessionContext
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
 * Model-agnostic tool-calling base: owns the conversation state, runs a single
 * inference round, and dispatches the resulting tool calls back through
 * [McpSession]. The default [send] is a single input -> tool-call pass with no
 * iteration; conversational agents that loop on tool results should extend
 * [IterativeAgent] instead.
 */
abstract class ToolCallingAgent(
    initialConversation: List<ConversationMessageDocument>,
) : Agent {
    private val _conversation = MutableSharedFlow<List<ConversationMessageDocument>>(
        replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST
    ).apply { tryEmit(initialConversation) }
    override val conversation: SharedFlow<List<ConversationMessageDocument>> get() = _conversation

    protected open val logger = Logger.withTag("ToolCallingAgent")

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
        sessionContext: SessionContext,
        includePromptsFromMcps: Map<String, Set<String>>,
    ): ConversationMessageDocument

    /** Decode the (already-emitted) assistant message's tool calls into
     *  dispatchable calls. May throw to abort the run on a malformed call. */
    protected abstract fun decodeToolCalls(
        assistantMessage: ConversationMessageDocument
    ): List<AgentToolCall>

    /** Lifecycle prep before a run. */
    protected open suspend fun prepare() {}

    /** How a tool result is encoded into the tool message `content`. */
    protected open fun encodeToolResultContent(result: ToolCallResult): String =
        result.resultString

    // ---- shared tool-calling helpers ----

    /** Open the MCP session, emit the user message, hand the available tools to
     *  [block], and always close the session afterwards. */
    protected suspend fun <T> withToolSession(
        input: String,
        mcpSession: McpSession,
        block: suspend (tools: List<McpSessionTool>) -> T,
    ): T {
        prepare()
        mcpSession.openSession()
        return try {
            emit(ConversationMessageDocument(role = MessageRole.user, content = input))
            block(mcpSession.listTools())
        } finally {
            mcpSession.closeSession()
        }
    }

    /** Run one inference round and emit the assistant message it produced. */
    protected suspend fun inferAndEmit(
        input: String,
        tools: List<McpSessionTool>,
        mcpSession: McpSession,
        sessionContext: SessionContext,
        includePromptsFromMcps: Map<String, Set<String>>,
    ): ConversationMessageDocument =
        runInference(input, currentConversation(), tools, mcpSession, sessionContext, includePromptsFromMcps)
            .also { emit(it) }

    /** Dispatch [toolCalls], emit their results, and return `true` if a
     *  non-recoverable error means the run should abort. */
    protected suspend fun executeToolCalls(
        toolCalls: List<AgentToolCall>,
        mcpSession: McpSession,
        sessionContext: SessionContext,
    ): Boolean {
        val results = toolCalls.map { call ->
            val r = mcpSession.callTool(
                call.integrationName, call.toolName, call.arguments, sessionContext,
                requireExists = false
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
            return true
        }
        return false
    }

    override suspend fun send(
        input: String,
        mcpSession: McpSession,
        sessionContext: SessionContext,
        includePromptsFromMcps: Map<String, Set<String>>,
        skipToolExecution: Boolean,
    ) = withToolSession(input, mcpSession) { tools ->
        val assistantMessage = inferAndEmit(input, tools, mcpSession, sessionContext, includePromptsFromMcps)
        val toolCalls = decodeToolCalls(assistantMessage)
        if (toolCalls.isEmpty() || skipToolExecution) return@withToolSession
        executeToolCalls(toolCalls, mcpSession, sessionContext)
        Unit
    }
}

/** A tool call normalized to something [McpSession.callTool] can dispatch directly. */
data class AgentToolCall(
    val id: String,
    val integrationName: String,
    val toolName: String,
    val arguments: Map<String, JsonElement>,
)
