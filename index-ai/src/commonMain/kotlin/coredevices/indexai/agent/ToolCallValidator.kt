package coredevices.indexai.agent

import coredevices.indexai.data.entity.ConversationMessageDocument
import coredevices.mcp.client.McpSessionTool
import coredevices.mcp.data.SemanticResult

/** Judges a tool call before it runs. */
fun interface ToolCallValidator {
    /**
     * @param tool What the session lists for [call], or null when it lists no such tool.
     * @param conversation The conversation up to and including the message that made [call].
     * @return Why the user should confirm [call] before it runs, or null to run it.
     */
    suspend fun validate(
        call: AgentToolCall,
        tool: McpSessionTool?,
        conversation: List<ConversationMessageDocument>,
    ): SemanticResult.ValidationFailure?
}
