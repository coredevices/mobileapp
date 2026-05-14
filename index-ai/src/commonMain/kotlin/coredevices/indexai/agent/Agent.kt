package coredevices.indexai.agent

import coredevices.indexai.data.entity.ConversationMessageDocument
import coredevices.mcp.client.McpSession
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.io.files.Path

/**
 * Interface for agent implementations that can process transcribed text
 * and generate appropriate responses through tool calls.
 */
interface Agent {
    /**
     * Send input to the agent for processing.
     * @param input The transcribed text to process
     * @param mode The chat mode to use for this input
     * @param mcpSession The MCP session to use for tool calls
     * @param includePromptsFromMcps A map of MCP integration names to sets of prompt names to include as context
     * @param skipToolExecution If true, the agent will not execute any tools, only generate responses (useful for testing)
     */
    suspend fun send(
        input: String,
        mcpSession: McpSession,
        includePromptsFromMcps: Map<String, Set<String>> = emptyMap(),
        skipToolExecution: Boolean = false
    )

    /**
     * Add a message to the conversation, without triggering any processing.
     * @param message The message to add
     * @return The index of the added message in the conversation
     */
    suspend fun addMessage(message: ConversationMessageDocument)
    val conversation: SharedFlow<List<ConversationMessageDocument>>
    val label: String
}