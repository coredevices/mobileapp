@file:OptIn(ExperimentalTime::class)

package coredevices.ring.service.indexfeed

import coredevices.indexai.data.entity.ItemDocument
import coredevices.indexai.data.entity.ItemDocument.ItemMetadata
import coredevices.mcp.data.SemanticResult
import coredevices.ring.service.indexfeed.DefaultListsBootstrap.Companion.LIST_NOTES_SELF_ID
import coredevices.ring.service.indexfeed.DefaultListsBootstrap.Companion.LIST_SHOPPING_ID
import coredevices.ring.service.indexfeed.DefaultListsBootstrap.Companion.LIST_TODOS_ID
import kotlinx.datetime.LocalTime
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class ItemFactory {

    internal fun simpleUid(): String =
        ((Clock.System.now().toEpochMilliseconds() and 0xFFFFFF).toString(36)) +
        "_" +
        ((1..1_000_000).random().toString(36))

    /**
     * Maps a tool's [SemanticResult] to the feed item it should produce, stamped with the
     * originating [toolCallId]. Returns null for results that do not create an item.
     */
    fun createFromSemanticResult(
        result: SemanticResult,
        sourceRecordingId: String,
        createdAt: Instant,
        toolCallId: String?,
    ): ItemDocument? = when (result) {
        is SemanticResult.TaskCreation ->
            reminderItem(sourceRecordingId, createdAt, result.title, result.deadline, toolCallId, result.localReminderId)
        is SemanticResult.ListItemCreation ->
            noteItem(sourceRecordingId, createdAt, result.content, result.listUsed, toolCallId, result.resolvedListId)
        is SemanticResult.AlarmCreation ->
            alarmItem(sourceRecordingId, createdAt, result.fireTime, toolCallId)
        is SemanticResult.CalendarEventCreation ->
            calendarEventItem(sourceRecordingId, createdAt, result.title, result.startTime, result.endTime, result.location, toolCallId)
        is SemanticResult.TimerCreation ->
            timerItem(sourceRecordingId, createdAt, result.fireTime, result.requestedDuration, toolCallId)
        is SemanticResult.MessageSent ->
            messageItem(sourceRecordingId, createdAt, result.recipientName, result.text, result.contactId, ItemMetadata.Message.Status.Sent, toolCallId)
        is SemanticResult.ActionLogged ->
            actionLogItem(sourceRecordingId, createdAt, result.title, result.toolName, result.success, toolCallId, result.body)
        is SemanticResult.SupportingData if !result.assistiveOnly ->
            answerItem(sourceRecordingId, createdAt, result.question ?: "", result.summary ?: "", toolCallId)
        is SemanticResult.SupportingData,
        is SemanticResult.Response,
        is SemanticResult.GenericSuccess,
        is SemanticResult.GenericFailure -> null
    }

    // --- Per-call item factories used by tool implementations ---

    fun reminderItem(
        sourceRecordingId: String?,
        createdAt: Instant,
        title: String,
        dueAt: Instant?,
        toolCallId: String?,
        localReminderId: Int? = null,
    ): ItemDocument = createItem(
        createdAt = createdAt,
        title = title,
        dueAt = dueAt,
        parents = listOf(LIST_TODOS_ID),
        recordingId = sourceRecordingId,
        toolCallId = toolCallId,
        metadata = ItemMetadata.Reminder(repeat = "one_time", notification = "push", localReminderId = localReminderId),
    )

    private fun pickNoteList(listUsedHint: String?): String {
        val hint = listUsedHint?.trim()?.lowercase() ?: return LIST_NOTES_SELF_ID
        return when {
            hint.isEmpty() -> LIST_NOTES_SELF_ID
            "shopping" in hint || "grocer" in hint -> LIST_SHOPPING_ID
            else -> LIST_NOTES_SELF_ID
        }
    }

    fun noteItem(
        sourceRecordingId: String?,
        createdAt: Instant,
        title: String,
        listHint: String?,
        toolCallId: String?,
        resolvedListId: String? = null,
    ): ItemDocument = createItem(
        createdAt = createdAt,
        title = title,
        parents = listOf(resolvedListId ?: pickNoteList(listHint)),
        recordingId = sourceRecordingId,
        toolCallId = toolCallId,
        metadata = ItemMetadata.Note,
    )

    fun alarmItem(
        sourceRecordingId: String,
        createdAt: Instant,
        fireTime: LocalTime,
        toolCallId: String?,
        repeatDays: Set<Int> = emptySet(),
    ): ItemDocument {
        val timeStr = fireTime.toString().substringBefore('.').take(5)
        return createItem(
            createdAt = createdAt,
            title = "Alarm · $timeStr",
            parents = listOf(LIST_TODOS_ID),
            recordingId = sourceRecordingId,
            toolCallId = toolCallId,
            metadata = ItemMetadata.Scheduled(
                fireKind = ItemMetadata.Scheduled.FireKind.Alarm,
                fireTime = fireTime,
                repeatDays = repeatDays.toList(),
                enabled = true,
            ),
        )
    }

    fun calendarEventItem(
        sourceRecordingId: String?,
        createdAt: Instant,
        title: String,
        startTime: Instant,
        endTime: Instant,
        location: String?,
        toolCallId: String?,
    ): ItemDocument = createItem(
        createdAt = createdAt,
        title = title,
        body = location.orEmpty(),
        dueAt = startTime,
        parents = listOf(LIST_TODOS_ID),
        recordingId = sourceRecordingId,
        toolCallId = toolCallId,
        metadata = ItemDocument.ItemMetadata.CalendarEvent(
            startTime = startTime,
            endTime = endTime,
            location = location,
        ),
    )

    fun timerItem(
        sourceRecordingId: String,
        createdAt: Instant,
        dueAt: Instant?,
        duration: Duration?,
        toolCallId: String?,
    ): ItemDocument {
        val durationPretty = duration?.toString()
        val title = "Timer" + (durationPretty?.let { " · $it" } ?: "")
        return createItem(
            createdAt = createdAt,
            title = title,
            dueAt = dueAt,
            parents = listOf(LIST_TODOS_ID),
            recordingId = sourceRecordingId,
            toolCallId = toolCallId,
            metadata = ItemMetadata.Scheduled(
                fireKind = ItemMetadata.Scheduled.FireKind.Timer,
                duration = duration?.inWholeMilliseconds,
            ),
        )
    }

    fun messageItem(
        sourceRecordingId: String,
        createdAt: Instant,
        recipientName: String?,
        text: String,
        contactId: String,
        status: ItemMetadata.Message.Status,
        toolCallId: String?,
        errorMessage: String? = null,
    ): ItemDocument = createItem(
        createdAt = createdAt,
        title = "Message · ${recipientName ?: contactId}",
        body = text,
        parents = emptyList(),
        recordingId = sourceRecordingId,
        toolCallId = toolCallId,
        metadata = ItemMetadata.Message(
            integration = "beeper",
            contact = contactId,
            recipientName = recipientName ?: contactId,
            text = text,
            sentAt = createdAt,
            status = status,
            errorMessage = errorMessage,
        ),
    )

    fun actionLogItem(
        sourceRecordingId: String,
        createdAt: Instant,
        title: String,
        toolName: String,
        success: Boolean,
        toolCallId: String?,
        body: String = "",
    ): ItemDocument = createItem(
        createdAt = createdAt,
        title = title,
        body = body,
        parents = emptyList(),
        recordingId = sourceRecordingId,
        toolCallId = toolCallId,
        metadata = ItemMetadata.ActionLog(toolName = toolName, success = success),
    )

    fun answerItem(
        sourceRecordingId: String,
        createdAt: Instant,
        question: String,
        answer: String,
        toolCallId: String?,
    ): ItemDocument = createItem(
        createdAt = createdAt,
        title = question.trim().replace(Regex("""\s+"""), " "),
        body = answer,
        parents = emptyList(),
        recordingId = sourceRecordingId,
        toolCallId = toolCallId,
        metadata = ItemMetadata.Answer(question = question.trim()),
    )

    private fun createItem(
        createdAt: Instant,
        title: String,
        body: String = "",
        dueAt: Instant? = null,
        parents: List<String>,
        recordingId: String?,
        toolCallId: String?,
        metadata: ItemMetadata,
    ): ItemDocument {
        val autoDone = dueAt != null && dueAt.toEpochMilliseconds() < Clock.System.now().toEpochMilliseconds()
        return ItemDocument(
            createdAt = createdAt,
            updatedAt = createdAt,
            title = title,
            body = body,
            done = autoDone,
            dueAt = dueAt,
            parentListIds = parents,
            sourceRecordingId = recordingId,
            sourceToolCallId = toolCallId,
            metadata = metadata,
        )
    }
}
