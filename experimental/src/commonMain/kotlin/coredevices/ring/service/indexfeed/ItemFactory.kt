@file:OptIn(ExperimentalTime::class)

package coredevices.ring.service.indexfeed

import coredevices.indexai.data.entity.ItemDocument
import coredevices.indexai.data.entity.ItemDocument.ItemMetadata
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

    // --- Per-call item factories used by tool implementations ---

    fun reminderItem(
        sourceRecordingId: String,
        createdAt: Instant,
        title: String,
        dueAt: Instant?,
    ): ItemDocument = createItem(
        createdAt = createdAt,
        title = title,
        dueAt = dueAt,
        parents = listOf(LIST_TODOS_ID),
        recordingId = sourceRecordingId,
        toolCallId = null,
        metadata = ItemMetadata.Reminder(repeat = "one_time", notification = "push"),
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
        sourceRecordingId: String,
        createdAt: Instant,
        title: String,
        listHint: String?,
    ): ItemDocument = createItem(
        createdAt = createdAt,
        title = title,
        parents = listOf(pickNoteList(listHint)),
        recordingId = sourceRecordingId,
        toolCallId = null,
        metadata = ItemMetadata.Note,
    )

    fun alarmItem(
        sourceRecordingId: String,
        createdAt: Instant,
        fireTime: LocalTime,
        repeatDays: Set<Int> = emptySet()
    ): ItemDocument {
        val timeStr = fireTime.toString().substringBefore('.').take(5)
        return createItem(
            createdAt = createdAt,
            title = "Alarm · $timeStr",
            parents = listOf(LIST_TODOS_ID),
            recordingId = sourceRecordingId,
            toolCallId = null,
            metadata = ItemMetadata.Scheduled(
                fireKind = ItemMetadata.Scheduled.FireKind.Alarm,
                fireTime = fireTime,
                repeatDays = repeatDays.toList(),
                enabled = true,
            ),
        )
    }

    fun timerItem(
        sourceRecordingId: String,
        createdAt: Instant,
        dueAt: Instant?,
        duration: Duration?,
    ): ItemDocument {
        val durationPretty = duration?.toString()
        val title = "Timer" + (durationPretty?.let { " · $it" } ?: "")
        return createItem(
            createdAt = createdAt,
            title = title,
            dueAt = dueAt,
            parents = listOf(LIST_TODOS_ID),
            recordingId = sourceRecordingId,
            toolCallId = null,
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
        errorMessage: String? = null,
    ): ItemDocument = createItem(
        createdAt = createdAt,
        title = "Message · ${recipientName ?: contactId}",
        body = text,
        parents = emptyList(),
        recordingId = sourceRecordingId,
        toolCallId = null,
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
        body: String = "",
    ): ItemDocument = createItem(
        createdAt = createdAt,
        title = title,
        body = body,
        parents = emptyList(),
        recordingId = sourceRecordingId,
        toolCallId = null,
        metadata = ItemMetadata.ActionLog(toolName = toolName, success = success),
    )

    fun answerItem(
        sourceRecordingId: String,
        createdAt: Instant,
        question: String,
        answer: String,
    ): ItemDocument = createItem(
        createdAt = createdAt,
        title = question.trim().replace(Regex("""\s+"""), " "),
        body = answer,
        parents = listOf(LIST_NOTES_SELF_ID),
        recordingId = sourceRecordingId,
        toolCallId = null,
        metadata = ItemMetadata.Answer(question = question.trim()),
    )

    private fun createItem(
        createdAt: Instant,
        title: String,
        body: String = "",
        dueAt: Instant? = null,
        parents: List<String>,
        recordingId: String,
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
