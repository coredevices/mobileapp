package coredevices.indexai.agent

import co.touchlab.kermit.Logger
import coredevices.indexai.data.entity.ConversationMessageDocument
import coredevices.indexai.data.entity.MessageRole
import coredevices.mcp.SessionContext
import coredevices.mcp.client.McpSession
import coredevices.mcp.data.ToolCallResult

/**
 * Conversational agent harness: runs [ToolCallingAgent]'s tool-calling in a loop,
 * feeding tool results back into inference until the model stops calling tools
 * (capped at [maxToolRounds]). A call identical to one already executed this turn
 * is never re-executed; a round consisting only of repeats ends the turn.
 */
abstract class IterativeAgent(
    initialConversation: List<ConversationMessageDocument>,
) : ToolCallingAgent(initialConversation) {

    override val logger = Logger.withTag("IterativeAgent")

    /** Max tool-execution rounds before erroring. */
    protected open val maxToolRounds: Int get() = 3

    override suspend fun send(
        input: String,
        mcpSession: McpSession,
        sessionContext: SessionContext,
        includePromptsFromMcps: Map<String, Set<String>>,
        skipToolExecution: Boolean,
    ) = withToolSession(input, mcpSession) { tools ->
        var round = 0
        val executed = mutableSetOf<AgentToolCall>()
        while (true) {
            val assistantMessage = inferAndEmit(input, tools, mcpSession, sessionContext, includePromptsFromMcps)
            val toolCalls = decodeToolCalls(assistantMessage)
            if (toolCalls.isEmpty() || skipToolExecution) return@withToolSession
            if (round >= maxToolRounds) throw Exception("Exceeded maximum tool iterations")
            val (repeats, fresh) = toolCalls.partition { it.copy(id = "") in executed }
            if (repeats.isNotEmpty()) {
                logger.w {
                    "Model repeated identical tool call(s) this turn, not re-executing: " +
                            repeats.joinToString { "${it.integrationName}__${it.toolName}" }
                }
                emitAll(repeats.map { duplicateToolResponse(it) })
                if (fresh.isEmpty()) return@withToolSession
            }
            if (executeToolCalls(fresh, mcpSession, sessionContext)) return@withToolSession
            executed += fresh.map { it.copy(id = "") }
            round++
        }
    }

    /** Closes out a duplicate call's tool slot without executing it, so the stored
     *  conversation never contains an unanswered tool call. */
    private fun duplicateToolResponse(call: AgentToolCall) = ConversationMessageDocument(
        role = MessageRole.tool,
        content = encodeToolResultContent(
            ToolCallResult(
                resultString = "Not executed: identical to a call already made this turn. Do not repeat tool calls.",
                semanticResult = null,
            )
        ),
        tool_call_id = call.id,
    )
}