package coredevices.ring.agent

import co.touchlab.kermit.Logger
import coredevices.indexai.agent.AgentToolCall
import coredevices.indexai.agent.ToolCallValidator
import coredevices.indexai.agent.systemone.SystemOneAgent
import coredevices.indexai.agent.systemone.decide
import coredevices.indexai.data.entity.ConversationMessageDocument
import coredevices.mcp.client.LazyLoadingMcpSession
import coredevices.mcp.client.McpSessionTool
import coredevices.mcp.data.SemanticResult
import coredevices.ring.agent.builtin_servlets.notes.CreateNoteTool
import coredevices.ring.agent.builtin_servlets.notes.NoteServlet
import coredevices.ring.agent.builtin_servlets.plugins.PluginsServlet
import coredevices.ring.agent.builtin_servlets.plugins.ReadSourceTool
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.seconds

/**
 * Rejects a tool call that [systemOne] doesn't think the conversation asked for, with less benefit
 * of the doubt for a tool marked destructive. Read-only tools, note captures, source reads and
 * tool loading always pass, as does any call when no verdict can be had.
 */
class SystemOneToolCallValidator(private val systemOne: SystemOneAgent) : ToolCallValidator {
    override suspend fun validate(
        call: AgentToolCall,
        tool: McpSessionTool?,
        conversation: List<ConversationMessageDocument>,
    ): SemanticResult.ValidationFailure? {
        if (call.integrationName == LazyLoadingMcpSession.LOADER_INTEGRATION_NAME) return null
        if ((call.integrationName to call.toolName) in UNCHECKED_TOOLS) return null
        val annotations = tool?.tool?.definition?.annotations
        if (annotations?.readOnlyHint == true) return null
        val name = "${call.integrationName}/${call.toolName}"
        val arguments = JsonObject(call.arguments)
        val state = buildJsonObject {
            putJsonArray(STATE_CONVERSATION) { conversation.forEach { add(it.toStateJson()) } }
            putJsonObject(STATE_TOOL_CALL) {
                put("integration", call.integrationName)
                put("tool", call.toolName)
                tool?.tool?.definition?.description?.takeIf { it.isNotBlank() }?.let { put("description", it) }
                put("arguments", arguments)
            }
        }
        val answers = try {
            withTimeoutOrNull(VALIDATE_TIMEOUT) {
                systemOne.decide(state) {
                    yesNo(QUESTION, INSTRUCTIONS) {
                        yes = VALID_CRITERIA
                        no = INVALID_CRITERIA
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.w(e) { "Couldn't validate $name, running it unchecked: ${e.message}" }
            return null
        }
        if (answers == null) {
            logger.w { "Validating $name took over $VALIDATE_TIMEOUT, running it unchecked" }
            return null
        }
        val probability = answers.yesNo(QUESTION).probability
        val threshold = if (annotations?.destructiveHint == true) MIN_VALID_PROBABILITY_DESTRUCTIVE else MIN_VALID_PROBABILITY
        val valid = probability >= threshold
        logger.i {
            "Tool call $name ${if (valid) "looks right" else "needs confirmation"}: " +
                "probability=$probability threshold=$threshold"
        }
        if (valid) return null
        return SemanticResult.ValidationFailure(call.integrationName, call.toolName, arguments, probability)
    }

    companion object {
        private val logger = Logger.withTag("SystemOneToolCallValidator")
        internal const val QUESTION = "tool_call_valid"
        internal const val MIN_VALID_PROBABILITY = 0.3
        internal const val MIN_VALID_PROBABILITY_DESTRUCTIVE = 0.6
        internal val VALIDATE_TIMEOUT = 5.seconds
        private val UNCHECKED_TOOLS = setOf(
            NoteServlet.NAME to CreateNoteTool.TOOL_NAME,
            PluginsServlet.NAME to ReadSourceTool.TOOL_NAME,
        )
        private const val STATE_CONVERSATION = "conversation"
        private const val STATE_TOOL_CALL = "tool_call"
        private const val INSTRUCTIONS =
            "Is the tool call in $STATE_TOOL_CALL what the user asked for? $STATE_CONVERSATION is the exchange " +
                "between the user, the assistant and the assistant's tools that led to it."
        private const val VALID_CRITERIA =
            "The call carries out the user's request or gathers information needed for it, or saves what the " +
                "user said when they asked for nothing else, and its arguments are stated in or worked out " +
                "from $STATE_CONVERSATION"
        private const val INVALID_CRITERIA =
            "The call does something the user did not ask for, or an argument is made up, contradicts " +
                "$STATE_CONVERSATION, or changes or leaves out a detail the user gave"
    }
}

private fun ConversationMessageDocument.toStateJson() = buildJsonObject {
    put("role", role.name)
    content?.let { put("content", it) }
    tool_calls?.mapNotNull { it.function }?.takeIf { it.isNotEmpty() }?.let { calls ->
        putJsonArray("tool_calls") {
            calls.forEach {
                addJsonObject {
                    put("name", it.name)
                    put("arguments", it.arguments)
                }
            }
        }
    }
}
