package coredevices.ring.ui.viewmodel

import androidx.compose.runtime.Composable
import coreapp.ring.generated.resources.Res
import coreapp.ring.generated.resources.tool_call_called
import coreapp.ring.generated.resources.tool_call_called_tool
import coreapp.ring.generated.resources.tool_call_calling
import coreapp.ring.generated.resources.tool_call_calling_tool
import coreapp.ring.generated.resources.tool_call_loaded_mcp
import coreapp.ring.generated.resources.tool_call_loading_mcp
import coreapp.ring.generated.resources.tool_call_read_sources
import coreapp.ring.generated.resources.tool_call_reading_sources
import coreapp.ring.generated.resources.tool_calls_called
import coreapp.ring.generated.resources.tool_calls_calling
import coredevices.indexai.data.entity.ConversationMessageEntity
import coredevices.indexai.data.entity.MessageRole
import coredevices.indexai.data.entity.RecordingEntryEntity
import coredevices.mcp.client.LazyLoadingMcpSession
import coredevices.mcp.data.SemanticResult
import coredevices.ring.agent.IndexAgentCactus
import coredevices.ring.agent.confirmation.confirmationRequest
import coredevices.ring.agent.builtin_servlets.plugins.ReadSourceTool
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** A conversation as the recording details screen lays it out: one entry per user or assistant turn. */
sealed class ConversationTurn {
    data class User(val text: String, val transcribedOffline: Boolean = false) : ConversationTurn()

    /**
     * Every consecutive assistant/tool message up to the next user turn, folded into one row.
     * [parts] keep message order; adjacent parts of the same kind are merged.
     */
    data class Assistant(
        val parts: List<AssistantPart>,
        /** Some of this turn came from the on-device agent. */
        val processedOffline: Boolean = false,
    ) : ConversationTurn() {
        val replies: List<String> get() = parts.filterIsInstance<AssistantPart.Reply>().map { it.text }
        val hasCalls: Boolean get() = parts.any { it !is AssistantPart.Reply }
    }
}

sealed class AssistantPart {
    data class Reply(val text: String) : AssistantPart()
    /** Calls whose result is shown as a chip. */
    data class Actions(val actions: List<RecordingAction>) : AssistantPart()
    /** Intermediate calls (lookups with no user-facing result), shown collapsed. */
    data class ToolCalls(val steps: List<ToolCallStep>) : AssistantPart()
    /** A call the agent is waiting for the user to allow or deny. */
    data class Confirmation(val callId: String, val request: SemanticResult.RequiresConfirmation) : AssistantPart()
}

/** One intermediate tool call; [done] once its result message exists, [summary] from its result if any. */
data class ToolCallStep(
    val callId: String,
    val toolName: String?,
    val done: Boolean,
    val summary: String? = null,
)

/** [active] while the call is in flight, [done] once it has returned. */
data class ToolCallLabel(val active: String, val done: String)

private val customToolCallLabels = mapOf(
    ReadSourceTool.TOOL_NAME to (Res.string.tool_call_reading_sources to Res.string.tool_call_read_sources),
    LazyLoadingMcpSession.LOAD_TOOLS_NAME to (Res.string.tool_call_loading_mcp to Res.string.tool_call_loaded_mcp),
)

// MCP tools are namespaced as `<integration>__<tool>`.
internal fun customToolCallLabel(toolName: String?): Pair<StringResource, StringResource>? =
    customToolCallLabels[toolName?.substringAfterLast("__")]

@Composable
fun toolCallLabel(toolName: String?): ToolCallLabel {
    customToolCallLabel(toolName)?.let { (active, done) ->
        return ToolCallLabel(stringResource(active), stringResource(done))
    }
    val tool = humanizeToolName(toolName)?.replaceFirstChar { it.lowercase() }
        ?: return ToolCallLabel(stringResource(Res.string.tool_call_calling_tool), stringResource(Res.string.tool_call_called_tool))
    return ToolCallLabel(stringResource(Res.string.tool_call_calling, tool), stringResource(Res.string.tool_call_called, tool))
}

@Composable
fun toolCallLabel(step: ToolCallStep): ToolCallLabel = toolCallLabel(step.toolName).withSummary(step.summary)

internal fun ToolCallLabel.withSummary(summary: String?): ToolCallLabel = summary?.let { copy(done = it) } ?: this

/** Header for a group of steps: the single step's label, or a count once there are several. */
@Composable
fun toolCallsLabel(steps: List<ToolCallStep>, active: Boolean): String {
    val single = steps.singleOrNull()?.let { toolCallLabel(it) }
    return when {
        single != null -> if (active) single.active else single.done
        active -> pluralStringResource(Res.plurals.tool_calls_calling, steps.size, steps.size)
        else -> pluralStringResource(Res.plurals.tool_calls_called, steps.size, steps.size)
    }
}

fun conversationTurns(
    messages: List<ConversationMessageEntity>,
    actionsByCall: Map<String?, RecordingAction>,
    entries: List<RecordingEntryEntity> = emptyList(),
): List<ConversationTurn> {
    val answeredCalls = messages.asSequence()
        .filter { it.document.role == MessageRole.tool }
        .mapNotNull { it.document.tool_call_id }
        .toSet()
    val calledToolIds = messages.asSequence()
        .flatMap { it.document.tool_calls.orEmpty().asSequence() }
        .map { it.id }
        .toSet()

    val turns = mutableListOf<ConversationTurn>()
    val parts = mutableListOf<AssistantPart>()
    var offline = false

    fun flushAssistant() {
        if (parts.isNotEmpty()) turns += ConversationTurn.Assistant(parts.toList(), processedOffline = offline)
        parts.clear()
        offline = false
    }

    fun addAction(action: RecordingAction) {
        val last = parts.lastOrNull()
        if (last is AssistantPart.Actions) parts[parts.lastIndex] = AssistantPart.Actions(last.actions + action)
        else parts += AssistantPart.Actions(listOf(action))
    }

    fun addStep(step: ToolCallStep) {
        val last = parts.lastOrNull()
        if (last is AssistantPart.ToolCalls) parts[parts.lastIndex] = AssistantPart.ToolCalls(last.steps + step)
        else parts += AssistantPart.ToolCalls(listOf(step))
    }

    fun addResult(action: RecordingAction) {
        action.errorMessage?.let { parts += AssistantPart.Reply(it) }
        addAction(action)
    }

    for (message in messages) {
        val doc = message.document
        when (doc.role) {
            MessageRole.user -> {
                flushAssistant()
                doc.content?.trim()?.takeIf { it.isNotBlank() }?.let {
                    val fallback = entries.any { e -> e.userMessageId == message.id && e.transcribedViaLocalFallback }
                    turns += ConversationTurn.User(it, transcribedOffline = fallback)
                }
            }
            MessageRole.assistant -> {
                if (doc.language_model_used?.startsWith(IndexAgentCactus.LANGUAGE_MODEL_PREFIX) == true) offline = true
                doc.content?.trim()?.takeIf { it.isNotBlank() }?.let { parts += AssistantPart.Reply(it) }
                for (call in doc.tool_calls.orEmpty()) {
                    val action = actionsByCall[call.id]
                    val confirmation = action?.result?.confirmationRequest
                    when {
                        confirmation != null -> parts += AssistantPart.Confirmation(call.id, confirmation)
                        action != null && !action.intermediate -> addResult(action)
                        else -> addStep(
                            ToolCallStep(
                                call.id,
                                call.function?.name,
                                done = call.id in answeredCalls,
                                summary = (action?.result as? SemanticResult.SupportingData)?.summary?.takeIf { it.isNotBlank() },
                            )
                        )
                    }
                }
            }
            MessageRole.tool -> {
                // Results with no issuing call (e.g. a forced fallback tool) still need a row.
                val action = actionsByCall[doc.tool_call_id]
                if (action != null && doc.tool_call_id !in calledToolIds) addResult(action)
            }
        }
    }
    flushAssistant()
    return turns
}
