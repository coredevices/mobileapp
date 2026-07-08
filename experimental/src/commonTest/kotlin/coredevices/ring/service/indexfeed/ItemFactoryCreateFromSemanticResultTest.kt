@file:OptIn(ExperimentalTime::class)

package coredevices.ring.service.indexfeed

import coredevices.indexai.data.entity.ItemDocument.ItemMetadata
import coredevices.indexai.util.JsonSnake
import coredevices.mcp.data.SemanticResult
import coredevices.ring.service.indexfeed.DefaultListsBootstrap.Companion.LIST_NOTES_SELF_ID
import coredevices.ring.service.indexfeed.DefaultListsBootstrap.Companion.LIST_SHOPPING_ID
import coredevices.ring.service.indexfeed.DefaultListsBootstrap.Companion.LIST_TODOS_ID
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime

class ItemFactoryCreateFromSemanticResultTest {

    private val factory = ItemFactory()
    private val recordingId = "rec-1"
    private val createdAt = Clock.System.now()
    private val toolCallId = "call-abc"

    private fun map(result: SemanticResult) =
        factory.createFromSemanticResult(result, recordingId, createdAt, toolCallId)

    // reminderItem/noteItem are called by the builtin integrations (LocalNoteClient,
    // BuiltInReminderFeedItems) rather than mapped from semantic results, so they are
    // exercised directly here.

    @Test
    fun reminderItemRoutesToTodosWithReminderMetadata() {
        val due = createdAt + 5.minutes
        val item = factory.reminderItem(recordingId, createdAt, "Call mom", due, toolCallId = null, localReminderId = 42)

        assertEquals("Call mom", item.title)
        assertEquals(due, item.dueAt)
        assertEquals(listOf(LIST_TODOS_ID), item.parentListIds)
        assertEquals(recordingId, item.sourceRecordingId)
        assertNull(item.sourceToolCallId)
        val meta = item.metadata
        assertTrue(meta is ItemMetadata.Reminder)
        assertEquals(42, meta.localReminderId)
    }

    @Test
    fun reminderItemWithoutLocalReminderIdLeavesItNull() {
        val item = factory.reminderItem(recordingId, createdAt, "Call mom", dueAt = null, toolCallId = null)
        val meta = item.metadata
        assertTrue(meta is ItemMetadata.Reminder)
        assertNull(meta.localReminderId)
    }

    @Test
    fun reminderItemCarriesNotifyBeforeIntoReminderMetadata() {
        val item = factory.reminderItem(
            recordingId,
            createdAt,
            "Leave for airport",
            createdAt + 5.minutes,
            toolCallId = null,
            localReminderId = 7,
            notifyBeforeMillis = 2.hours.inWholeMilliseconds,
        )
        val meta = item.metadata
        assertTrue(meta is ItemMetadata.Reminder)
        assertEquals(2.hours.inWholeMilliseconds, meta.notifyBeforeMillis)
    }

    @Test
    fun reminderItemWithoutNotifyBeforeLeavesItNull() {
        val item = factory.reminderItem(recordingId, createdAt, "Call mom", createdAt + 5.minutes, toolCallId = null)
        val meta = item.metadata
        assertTrue(meta is ItemMetadata.Reminder)
        assertNull(meta.notifyBeforeMillis)
    }

    @Test
    fun reminderMetadataFromOlderJsonDecodesWithNullLocalReminderId() {
        // Records written before localReminderId existed must still decode.
        val legacy = """{"type":"reminder","repeat":"one_time","notification":"push"}"""
        val meta = JsonSnake.decodeFromString(ItemMetadata.serializer(), legacy)
        assertTrue(meta is ItemMetadata.Reminder)
        assertNull(meta.localReminderId)
    }

    @Test
    fun noteItemRoutesToResolvedList() {
        val item = factory.noteItem(
            recordingId, createdAt, "Milk", listHint = null, toolCallId = null, resolvedListId = "list_custom",
        )

        assertEquals("Milk", item.title)
        assertEquals(listOf("list_custom"), item.parentListIds)
        assertNull(item.sourceToolCallId)
        assertTrue(item.metadata is ItemMetadata.Note)
    }

    @Test
    fun noteItemWithoutListFallsBackToNotesList() {
        val item = factory.noteItem(recordingId, createdAt, "Idea", listHint = null, toolCallId = null)
        assertEquals(listOf(LIST_NOTES_SELF_ID), item.parentListIds)
    }

    @Test
    fun noteItemRoutedToShoppingBecomesChecklist() {
        val item = factory.noteItem(
            recordingId, createdAt, "Milk", listHint = "Shopping", toolCallId = null, resolvedListId = LIST_SHOPPING_ID,
        )

        assertEquals(listOf(LIST_SHOPPING_ID), item.parentListIds)
        assertTrue(item.metadata is ItemMetadata.Checklist)
    }

    @Test
    fun noteItemRoutedToShoppingByHintBecomesChecklist() {
        // No resolvedListId: pickNoteList sends "shopping"/"grocery" hints to the
        // shopping list, and those items should be checklist items too.
        val item = factory.noteItem(recordingId, createdAt, "Eggs", listHint = "grocery", toolCallId = null)

        assertEquals(listOf(LIST_SHOPPING_ID), item.parentListIds)
        assertTrue(item.metadata is ItemMetadata.Checklist)
    }

    @Test
    fun alarmCreationMapsToScheduledAlarm() {
        val item = map(SemanticResult.AlarmCreation(fireTime = LocalTime(7, 0)))!!
        val meta = item.metadata
        assertTrue(meta is ItemMetadata.Scheduled)
        assertEquals(ItemMetadata.Scheduled.FireKind.Alarm, meta.fireKind)
        assertEquals(LocalTime(7, 0), meta.fireTime)
        assertEquals(toolCallId, item.sourceToolCallId)
        // Owned by the system clock app — must not appear in the Reminders list.
        assertEquals(emptyList(), item.parentListIds)
    }

    @Test
    fun timerCreationMapsToScheduledTimer() {
        val fireAt = createdAt + 10.minutes
        val item = map(SemanticResult.TimerCreation(requestedDuration = 10.minutes, fireTime = fireAt))!!
        val meta = item.metadata
        assertTrue(meta is ItemMetadata.Scheduled)
        assertEquals(ItemMetadata.Scheduled.FireKind.Timer, meta.fireKind)
        assertEquals(fireAt, item.dueAt)
        assertEquals(toolCallId, item.sourceToolCallId)
        // Owned by the system clock app — must not appear in the Reminders list.
        assertEquals(emptyList(), item.parentListIds)
    }

    @Test
    fun calendarEventCreationMapsToCalendarEventItem() {
        val start = createdAt + 30.minutes
        val end = createdAt + 90.minutes
        val item = map(
            SemanticResult.CalendarEventCreation(
                title = "Lunch with Sam",
                startTime = start,
                endTime = end,
                location = "Cafe",
            )
        )!!
        assertEquals("Lunch with Sam", item.title)
        assertEquals("Cafe", item.body)
        assertEquals(start, item.dueAt)
        assertEquals(listOf(LIST_TODOS_ID), item.parentListIds)
        assertEquals(toolCallId, item.sourceToolCallId)
        val meta = item.metadata
        assertTrue(meta is ItemMetadata.CalendarEvent)
        assertEquals(start, meta.startTime)
        assertEquals(end, meta.endTime)
        assertEquals("Cafe", meta.location)
    }

    @Test
    fun messageSentMapsToSentMessage() {
        val item = map(SemanticResult.MessageSent(recipientName = "Alice", text = "hi", contactId = "room1"))!!
        val meta = item.metadata
        assertTrue(meta is ItemMetadata.Message)
        assertEquals("Alice", meta.recipientName)
        assertEquals("hi", meta.text)
        assertEquals("room1", meta.contact)
        assertEquals(ItemMetadata.Message.Status.Sent, meta.status)
        assertEquals(toolCallId, item.sourceToolCallId)
    }

    @Test
    fun actionLoggedMapsToActionLog() {
        val item = map(
            SemanticResult.ActionLogged(toolName = "evaluate_js", title = "Ran JavaScript", success = true, body = "1+1")
        )!!
        val meta = item.metadata
        assertTrue(meta is ItemMetadata.ActionLog)
        assertEquals("evaluate_js", meta.toolName)
        assertTrue(meta.success)
        assertEquals("Ran JavaScript", item.title)
        assertEquals("1+1", item.body)
        assertEquals(toolCallId, item.sourceToolCallId)
    }

    @Test
    fun nonAssistiveSupportingDataMapsToAnswerItem() {
        val item = map(
            SemanticResult.SupportingData("75F and sunny", assistiveOnly = false, question = "weather in NY?")
        )!!
        val meta = item.metadata
        assertTrue(meta is ItemMetadata.Answer)
        assertEquals("weather in NY?", meta.question)
        assertEquals("weather in NY?", item.title)
        assertEquals("75F and sunny", item.body)
        assertEquals(emptyList(), item.parentListIds)
        assertEquals(toolCallId, item.sourceToolCallId)
    }

    @Test
    fun nonItemResultsMapToNull() {
        // Notes and reminders are created by the owning integration, never centrally.
        assertNull(map(SemanticResult.TaskCreation(title = "Call mom", deadline = createdAt + 5.minutes)))
        assertNull(map(SemanticResult.ListItemCreation(content = "Milk", resolvedListId = "list_custom")))
        assertNull(map(SemanticResult.SupportingData("info", assistiveOnly = true)))
        assertNull(map(SemanticResult.Response("hello there")))
        assertNull(map(SemanticResult.GenericSuccess))
        assertNull(map(SemanticResult.GenericFailure("nope")))
    }
}
