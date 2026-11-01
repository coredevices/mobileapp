package coredevices.ring.ui.viewmodel

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Assistant
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Error
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.ui.graphics.vector.ImageVector
import coredevices.indexai.data.entity.MessageRole
import coredevices.indexai.database.dao.RecordingToolMessage
import coredevices.indexai.database.dao.answerTag
import coredevices.mcp.data.SemanticResult
import coredevices.ring.data.entity.room.indexfeed.CachedItem
import coredevices.ring.data.entity.room.indexfeed.CachedList
import coredevices.ring.service.indexfeed.DefaultListsBootstrap.Companion.LIST_NOTES_SELF_ID
import kotlin.time.Duration

/** One tool call on a recording and what it produced, or an answer the agent gave, as the
 *  feeds present it. */
data class RecordingAction(
    val toolCallId: String?,
    /** Null for an answer, which the agent gives directly rather than through a tool. */
    val result: SemanticResult?,
    val label: String,
    val glyph: ImageVector,
    /** Feed item the action created, when one exists. */
    val itemId: String?,
    /** Plugin / MCP server that ran the tool; null for builtin servlets. */
    val source: String? = null,
    /** The answer text, for an action built from a tagged answer message. */
    val answerText: String? = null,
) {
    val failed: Boolean get() = result is SemanticResult.GenericFailure

    /** An internal lookup made on the way to an answer (e.g. reading a plugin source). */
    val assistive: Boolean get() = result is SemanticResult.SupportingData

    /** Produced nothing the user can see or open, so it only belongs in the tool-call trail. */
    val intermediate: Boolean get() = assistive || result is SemanticResult.GenericSuccess

    /** Waiting on the user to allow or deny the call. */
    val needsConfirmation: Boolean get() = result is SemanticResult.RequiresConfirmation

    val errorMessage: String? get() =
        (result as? SemanticResult.GenericFailure)?.userErrorMessage?.takeIf { it.isNotBlank() }

    /** Text the user would recognise the action by when searching the feed. */
    val searchText: String? get() = answerText ?: result?.contentText
}

/**
 * Pairs each recording's tool results with the call that issued them, and adds the agent's
 * tagged answers, in conversation order. [items] is only consulted for results written before
 * [SemanticResult.itemId] existed.
 */
fun recordingActions(
    messages: List<RecordingToolMessage>,
    lists: List<CachedList>,
    items: List<CachedItem> = emptyList(),
): Map<Long, List<RecordingAction>> {
    val toolNames = messages.asSequence()
        .flatMap { it.toolCalls.orEmpty() }
        .associate { it.id to it.function?.name }
    val legacyItemsByCall = items.asSequence()
        .filter { !it.deleted && !it.sourceToolCallId.isNullOrBlank() }
        .associate { it.sourceToolCallId!! to it.firestoreId }
    // A confirmation the user has answered is superseded by the call's real result.
    val answeredCalls = messages.asSequence()
        .filter { it.role == MessageRole.tool && it.semanticResult !is SemanticResult.RequiresConfirmation }
        .mapNotNull { it.toolCallId }
        .toSet()
    return messages
        .filter { (it.role == MessageRole.tool && it.semanticResult != null) || (it.answerTag != null && !it.content.isNullOrBlank()) }
        .filterNot { it.semanticResult is SemanticResult.RequiresConfirmation && it.toolCallId in answeredCalls }
        .groupBy({ it.recordingId }) { msg ->
            val result = msg.semanticResult
            if (result == null) {
                RecordingAction(
                    toolCallId = null,
                    result = null,
                    label = "Answered",
                    glyph = Icons.Outlined.Assistant,
                    itemId = msg.answerTag?.itemId,
                    answerText = msg.content,
                )
            } else {
                val toolName = msg.toolCallId?.let { toolNames[it] }
                RecordingAction(
                    toolCallId = msg.toolCallId,
                    result = result,
                    label = result.actionLabel(toolName, lists),
                    glyph = result.actionGlyph(),
                    itemId = result.itemId ?: msg.toolCallId?.let { legacyItemsByCall[it] },
                    source = toolSource(toolName),
                )
            }
        }
}

internal fun SemanticResult.actionLabel(toolName: String?, lists: List<CachedList>): String = when (this) {
    is SemanticResult.TaskCreation -> destination?.let { "Sent to $it" } ?: title.ifBlank { "Reminder" }
    is SemanticResult.ListItemCreation -> destination?.let { "Sent to $it" } ?: "Added to ${listTitle(lists)}"
    is SemanticResult.AlarmCreation -> "Alarm · ${fireTime.toString().take(5)}"
    is SemanticResult.TimerCreation -> requestedDuration?.let { "Timer · ${formatDuration(it)}" } ?: "Timer"
    is SemanticResult.CalendarEventCreation -> "Added to calendar"
    is SemanticResult.MessageSent -> "Sent to ${messageRecipientLabel(recipientName)}"
    is SemanticResult.ActionLogged -> title.ifBlank { humanizeToolName(this.toolName) ?: "Action" }
    is SemanticResult.SupportingData ->
        summary?.takeIf { it.isNotBlank() } ?: humanizeToolName(toolName) ?: "Gathered info"
    is SemanticResult.GenericSuccess -> humanizeToolName(toolName) ?: "Action completed"
    is SemanticResult.GenericFailure -> humanizeToolName(toolName)?.let { "$it failed" } ?: "Action failed"
    is SemanticResult.RequiresConfirmation -> "Needs confirmation"
    is SemanticResult.UserDenied -> "Denied by user"
}

internal fun SemanticResult.actionGlyph(): ImageVector = when (this) {
    is SemanticResult.TaskCreation -> if (destination != null) Icons.Outlined.Mail else Icons.Outlined.Alarm
    is SemanticResult.ListItemCreation -> if (destination != null) Icons.Outlined.Mail else Icons.Default.Menu
    is SemanticResult.AlarmCreation, is SemanticResult.TimerCreation -> Icons.Outlined.Alarm
    is SemanticResult.CalendarEventCreation -> Icons.Outlined.CalendarToday
    is SemanticResult.MessageSent -> Icons.Outlined.Mail
    is SemanticResult.SupportingData -> Icons.Outlined.Info
    is SemanticResult.GenericFailure -> Icons.Outlined.Error
    is SemanticResult.RequiresConfirmation -> Icons.Outlined.Lock
    is SemanticResult.UserDenied -> Icons.Outlined.Block
    is SemanticResult.ActionLogged, is SemanticResult.GenericSuccess -> Icons.Filled.CheckCircle
}

/** Text the user would recognise the action by when searching the feed. */
val SemanticResult.contentText: String? get() = when (this) {
    is SemanticResult.TaskCreation -> title
    is SemanticResult.ListItemCreation -> content
    is SemanticResult.CalendarEventCreation -> title
    is SemanticResult.MessageSent -> text
    is SemanticResult.ActionLogged -> "$title $body"
    is SemanticResult.SupportingData -> summary
    is SemanticResult.AlarmCreation,
    is SemanticResult.TimerCreation,
    is SemanticResult.RequiresConfirmation,
    is SemanticResult.UserDenied,
    is SemanticResult.GenericSuccess,
    is SemanticResult.GenericFailure -> null
}

private const val TOOL_NAMESPACE_SEPARATOR = "__"
private const val BUILTIN_PREFIX = "builtin_"

/** `notion__search_contacts` -> "Search contacts". */
fun humanizeToolName(name: String?): String? = humanize(name?.substringAfterLast(TOOL_NAMESPACE_SEPARATOR))

/** `notion__search_contacts` -> "Notion"; null for builtin servlets and un-namespaced tools. */
fun toolSource(name: String?): String? = name
    ?.takeIf { it.contains(TOOL_NAMESPACE_SEPARATOR) }
    ?.substringBefore(TOOL_NAMESPACE_SEPARATOR)
    ?.takeUnless { it.startsWith(BUILTIN_PREFIX) }
    ?.let(::humanize)

private fun humanize(name: String?): String? = name
    ?.replace('_', ' ')
    ?.trim()
    ?.takeIf { it.isNotEmpty() }
    ?.replaceFirstChar { it.uppercase() }

private fun SemanticResult.ListItemCreation.listTitle(lists: List<CachedList>): String =
    resolvedListId?.let { id -> lists.firstOrNull { it.firestoreId == id }?.title?.takeIf { it.isNotBlank() } }
        ?: listUsed?.takeIf { it.isNotBlank() }
        ?: lists.firstOrNull { it.firestoreId == LIST_NOTES_SELF_ID }?.title?.takeIf { it.isNotBlank() }
        ?: "Notes to self"

/** Strips homeserver suffixes and shortens long Matrix room ids. */
private fun messageRecipientLabel(raw: String): String {
    if (raw.startsWith("!")) {
        val local = raw.substringBefore(":").drop(1)
        return if (local.length > 12) local.take(12) + "…" else local
    }
    return raw.ifBlank { "contact" }
}

/** "20 min", "1 hr 30 min", "45 sec". */
fun formatDuration(duration: Duration): String {
    val totalSec = duration.inWholeSeconds
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return buildString {
        if (h > 0) append("$h hr ")
        if (m > 0) append("$m min ")
        if (s > 0 && h == 0L) append("$s sec")
    }.trim().ifBlank { duration.toString() }
}
