package coredevices.indexai.agent

import co.touchlab.kermit.Logger
import coredevices.indexai.data.entity.ConversationMessageDocument
import coredevices.mcp.SessionContext
import coredevices.mcp.client.McpSession

/**
 * Conversational agent harness: runs [ToolCallingAgent]'s tool-calling in a loop,
 * feeding tool results back into inference until the model stops calling tools
 * (capped at [maxToolRounds]).
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
        while (true) {
            val assistantMessage = inferAndEmit(input, tools, mcpSession, includePromptsFromMcps)
            val toolCalls = decodeToolCalls(assistantMessage)
            if (toolCalls.isEmpty() || skipToolExecution) return@withToolSession
            if (round >= maxToolRounds) throw Exception("Exceeded maximum tool iterations")
            if (executeToolCalls(toolCalls, mcpSession, sessionContext)) return@withToolSession
            round++
        }
    }
}