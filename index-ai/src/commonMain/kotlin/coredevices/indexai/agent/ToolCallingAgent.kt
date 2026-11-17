package coredevices.indexai.agent

import co.touchlab.kermit.Logger
import coredevices.indexai.data.entity.ConversationMessageDocument
import coredevices.indexai.data.entity.MessageRole
import coredevices.indexai.data.entity.MessageTag
import coredevices.mcp.SessionContext
import coredevices.mcp.client.McpSession
import coredevices.mcp.client.McpSessionTool
import coredevices.mcp.data.SemanticResult
import coredevices.mcp.data.ToolCallResult
import coredevices.mcp.data.isConfirmationRequest
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

    // Unbuffered so a collector has taken every round by the time send() returns.
    private val _iterations = MutableSharedFlow<AgentIteration>()
    override val iterations: SharedFlow<AgentIteration> get() = _iterations

    protected open val logger = Logger.withTag("ToolCallingAgent")

    /**
     * Asked before each tool call runs; a call it questions waits on the user's confirmation.
     * Not asked about a tool that confirms every call itself.
     */
    protected open val toolCallValidator: ToolCallValidator? get() = null

    private var listedTools = emptyList<McpSessionTool>()

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

    /** What this agent declares to its model out of the session's [tools], for [AgentIteration]. */
    protected open fun declaredTools(tools: List<McpSessionTool>): List<ToolDeclarationRecord> =
        tools.map { ToolDeclarationRecord(it.integrationName, it.tool.definition) }

    /** Lifecycle prep before a run. */
    protected open suspend fun prepare() {}

    /** How a tool result is encoded into the tool message `content`. */
    protected open fun encodeToolResultContent(result: ToolCallResult): String =
        result.resultString

    // ---- shared tool-calling helpers ----

    /** Open the MCP session, emit the user message (none when [input] is null, i.e. a resumed
     *  turn), run [block], and always close the session afterwards. */
    protected suspend fun <T> withToolSession(
        input: String?,
        mcpSession: McpSession,
        block: suspend () -> T,
    ): T {
        prepare()
        mcpSession.openSession()
        return try {
            input?.let { emit(ConversationMessageDocument(role = MessageRole.user, content = it)) }
            block()
        } finally {
            mcpSession.closeSession()
        }
    }

    /** Run one inference round and emit the assistant message it produced, with any
     *  [FinalMessageMarker] stripped so it never reaches the stored conversation; a final
     *  query answer is tagged [MessageTag.AnswerResponse]. Tools are listed afresh each round:
     *  a session may offer different tools after a call
     *  (see [coredevices.mcp.client.LazyLoadingMcpSession]). */
    protected suspend fun inferAndEmit(
        input: String,
        mcpSession: McpSession,
        sessionContext: SessionContext,
        includePromptsFromMcps: Map<String, Set<String>>,
    ): ConversationMessageDocument {
        val history = currentConversation().filterNot { it.awaitsConfirmation }
        val tools = mcpSession.listTools().also { listedTools = it }
        _iterations.emit(
            AgentIteration(
                agent = label,
                toolDeclarations = declaredTools(tools),
                conversation = history,
            )
        )
        val message = runInference(input, history, tools, mcpSession, sessionContext, includePromptsFromMcps)
        val parsed = FinalMessageMarker.parse(message.content)
        val isAnswer = parsed.isQueryAnswer && parsed.text != null && message.tool_calls.isNullOrEmpty()
        return message.copy(
            content = parsed.text,
            messageTags = if (isAnswer) listOf(MessageTag.AnswerResponse()) else message.messageTags,
        ).also { emit(it) }
    }

    /** Dispatch [toolCalls], emit their results, and return `true` if the run should stop:
     *  a non-recoverable error, or a call that is waiting on the user's confirmation. */
    protected suspend fun executeToolCalls(
        toolCalls: List<AgentToolCall>,
        mcpSession: McpSession,
        sessionContext: SessionContext,
    ): Boolean {
        val results = toolCalls.map { call ->
            val r = validationFailure(call)?.let { ToolCallResult(AWAITING_CONFIRMATION_RESULT, it) }
                ?: mcpSession.callTool(
                    call.integrationName,
                    call.toolName,
                    call.arguments,
                    sessionContext.copy(toolCallId = call.id),
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
        if (results.any { it.awaitsConfirmation }) {
            logger.i { "Pausing tool loop until the user confirms a tool call" }
            return true
        }
        return false
    }

    private suspend fun validationFailure(call: AgentToolCall): SemanticResult.ValidationFailure? {
        val validator = toolCallValidator ?: return null
        val tool = listedTools.firstOrNull {
            it.integrationName == call.integrationName && it.tool.definition.name == call.toolName
        }
        if (tool?.tool?.requiresConfirmation == true) return null
        return validator.validate(call, tool, currentConversation().filterNot { it.awaitsConfirmation })
    }

    override suspend fun send(
        input: String,
        mcpSession: McpSession,
        sessionContext: SessionContext,
        includePromptsFromMcps: Map<String, Set<String>>,
        skipToolExecution: Boolean,
    ) = withToolSession(input, mcpSession) {
        val assistantMessage = inferAndEmit(input, mcpSession, sessionContext, includePromptsFromMcps)
        val toolCalls = decodeToolCalls(assistantMessage)
        if (toolCalls.isEmpty() || skipToolExecution) return@withToolSession
        executeToolCalls(toolCalls, mcpSession, sessionContext)
        Unit
    }
}

private const val AWAITING_CONFIRMATION_RESULT = "Waiting for the user to confirm this action"

/** A tool result that is a placeholder for a call the user has yet to confirm. */
val ConversationMessageDocument.awaitsConfirmation: Boolean
    get() = role == MessageRole.tool && semantic_result?.isConfirmationRequest == true

/** Placeholders with no later result for their call, be it the real one or another placeholder. */
fun List<ConversationMessageDocument>.pendingConfirmations(): List<ConversationMessageDocument> {
    val lastResult = withIndex()
        .filter { it.value.role == MessageRole.tool }
        .associate { it.value.tool_call_id to it.index }
    return filterIndexed { index, message -> message.awaitsConfirmation && lastResult[message.tool_call_id] == index }
}

/** A tool call normalized to something [McpSession.callTool] can dispatch directly. */
data class AgentToolCall(
    val id: String,
    val integrationName: String,
    val toolName: String,
    val arguments: Map<String, JsonElement>,
)
