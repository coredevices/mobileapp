@file:OptIn(kotlin.time.ExperimentalTime::class)

package coredevices.ring.ui.viewmodel

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Mail
import coredevices.indexai.data.entity.FunctionToolCall
import coredevices.indexai.data.entity.ItemDocument
import coredevices.indexai.data.entity.MessageRole
import coredevices.indexai.data.entity.MessageTag
import coredevices.indexai.data.entity.ToolCall
import coredevices.indexai.database.dao.RecordingToolMessage
import coredevices.mcp.data.SemanticResult
import coredevices.ring.data.entity.room.indexfeed.CachedItem
import coredevices.ring.data.entity.room.indexfeed.CachedList
import coredevices.ring.service.indexfeed.DefaultListsBootstrap.Companion.LIST_NOTES_SELF_ID
import kotlinx.datetime.LocalTime
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class RecordingActionsTest {

    private val now = Clock.System.now()

    private fun toolMessage(result: SemanticResult, callId: String = "call-1", recordingId: Long = 1, id: Long = 2) =
        RecordingToolMessage(recordingId, id, MessageRole.tool, null, callId, result)

    private fun assistantMessage(vararg calls: Pair<String, String>, recordingId: Long = 1, id: Long = 1) =
        RecordingToolMessage(
            recordingId, id, MessageRole.assistant,
            calls.map { (callId, name) -> ToolCall(callId, "function", FunctionToolCall(name, "{}")) },
            null, null,
        )

    private fun answerMessage(text: String, recordingId: Long = 1, id: Long = 3, itemId: String? = null) =
        RecordingToolMessage(
            recordingId, id, MessageRole.assistant, null, null, null,
            content = text, messageTags = listOf(MessageTag.AnswerResponse(itemId)),
        )

    private fun single(result: SemanticResult, lists: List<CachedList> = emptyList()): RecordingAction =
        recordingActions(listOf(toolMessage(result)), lists).getValue(1L).single()

    @Test
    fun groupsResultsByRecordingInConversationOrder() {
        val actions = recordingActions(
            listOf(
                toolMessage(SemanticResult.GenericSuccess, callId = "b", recordingId = 2, id = 5),
                toolMessage(SemanticResult.ListItemCreation("milk"), callId = "a", recordingId = 1, id = 3),
                answerMessage("hi", recordingId = 1, id = 4),
            ),
            lists = emptyList(),
        )
        assertEquals(listOf("a", null), actions.getValue(1L).map { it.toolCallId })
        assertEquals(listOf("b"), actions.getValue(2L).map { it.toolCallId })
    }

    @Test
    fun assistantMessagesAndResultlessToolMessagesProduceNoAction() {
        val actions = recordingActions(
            listOf(assistantMessage("call-1" to "create_note"), toolMessage(SemanticResult.GenericSuccess).copy(semanticResult = null)),
            lists = emptyList(),
        )
        assertTrue(actions.isEmpty())
    }

    @Test
    fun itemIdComesFromTheResultWhenRecorded() {
        val action = single(SemanticResult.ListItemCreation("milk", itemId = "item-9"))
        assertEquals("item-9", action.itemId)
    }

    @Test
    fun legacyResultWithoutItemIdFallsBackToItemMatchedByToolCall() {
        val item = CachedItem(firestoreId = "item-3", title = "milk", sourceToolCallId = "call-1")
        val actions = recordingActions(listOf(toolMessage(SemanticResult.ListItemCreation("milk"))), emptyList(), listOf(item))
        assertEquals("item-3", actions.getValue(1L).single().itemId)

        val deleted = recordingActions(
            listOf(toolMessage(SemanticResult.ListItemCreation("milk"))),
            emptyList(),
            listOf(item.copy(deleted = true)),
        )
        assertNull(deleted.getValue(1L).single().itemId)
    }

    @Test
    fun noteLabelsResolveTheListTitle() {
        val lists = listOf(
            CachedList(firestoreId = LIST_NOTES_SELF_ID, title = "Ideas"),
            CachedList(firestoreId = "list-shop", title = "Shopping"),
        )
        assertEquals("Added to Shopping", single(SemanticResult.ListItemCreation("milk", resolvedListId = "list-shop"), lists).label)
        assertEquals("Added to Groceries", single(SemanticResult.ListItemCreation("milk", listUsed = "Groceries"), lists).label)
        assertEquals("Added to Ideas", single(SemanticResult.ListItemCreation("milk"), lists).label)
        assertEquals("Added to Notes to self", single(SemanticResult.ListItemCreation("milk")).label)
        assertEquals(Icons.Default.Menu, single(SemanticResult.ListItemCreation("milk")).glyph)
    }

    @Test
    fun externalDestinationsReadAsSentTo() {
        val note = single(SemanticResult.ListItemCreation("milk", listUsed = "Inbox", destination = "Notion"))
        assertEquals("Sent to Notion", note.label)
        assertEquals(Icons.Outlined.Mail, note.glyph)
        val task = single(SemanticResult.TaskCreation("Call mom", deadline = null, destination = "Google Tasks"))
        assertEquals("Sent to Google Tasks", task.label)
    }

    @Test
    fun reminderAlarmTimerAndCalendarLabels() {
        assertEquals("Call mom", single(SemanticResult.TaskCreation("Call mom", deadline = now)).label)
        assertEquals("Reminder", single(SemanticResult.TaskCreation("", deadline = now)).label)
        assertEquals("Alarm · 07:05", single(SemanticResult.AlarmCreation(LocalTime(7, 5))).label)
        assertEquals("Timer · 20 min", single(SemanticResult.TimerCreation(20.minutes, now)).label)
        assertEquals("Timer", single(SemanticResult.TimerCreation(null, now)).label)
        val calendar = single(SemanticResult.CalendarEventCreation("Lunch", now, now + 1.hours))
        assertEquals("Added to calendar", calendar.label)
        assertEquals(Icons.Outlined.CalendarToday, calendar.glyph)
    }

    @Test
    fun messageLabelsShortenMatrixRoomIds() {
        assertEquals("Sent to Alice", single(SemanticResult.MessageSent("Alice")).label)
        assertEquals("Sent to abcdefghijkl…", single(SemanticResult.MessageSent("!abcdefghijklmnop:matrix.org")).label)
    }

    @Test
    fun resultsWithoutItemsAreLabelledFromTheToolCall() {
        val messages = listOf(
            assistantMessage("call-1" to "set_thermostat", "call-2" to "open_garage"),
            toolMessage(SemanticResult.GenericSuccess, callId = "call-1", id = 2),
            toolMessage(SemanticResult.GenericFailure("Garage offline"), callId = "call-2", id = 3),
        )
        val (ok, failed) = recordingActions(messages, emptyList()).getValue(1L)
        assertEquals("Set thermostat", ok.label)
        assertFalse(ok.failed)
        assertNull(ok.itemId)
        assertEquals("Open garage failed", failed.label)
        assertTrue(failed.failed)
        assertEquals("Garage offline", failed.errorMessage)
    }

    @Test
    fun resultsWithoutAKnownToolFallBackToGenericLabels() {
        assertEquals("Action completed", single(SemanticResult.GenericSuccess).label)
        assertEquals("Action failed", single(SemanticResult.GenericFailure(null)).label)
        assertNull(single(SemanticResult.GenericFailure("   ")).errorMessage)
    }

    @Test
    fun taggedAnswersBecomeAnsweredActions() {
        val answer = recordingActions(listOf(answerMessage("75F and sunny")), emptyList()).getValue(1L).single()
        assertEquals("Answered", answer.label)
        assertEquals("75F and sunny", answer.answerText)
        assertEquals("75F and sunny", answer.searchText)
        assertNull(answer.result)
        assertNull(answer.toolCallId)
        assertNull(answer.itemId)
        assertFalse(answer.assistive)
        assertFalse(answer.intermediate)
        assertFalse(answer.failed)
    }

    @Test
    fun searchAnswersLinkToTheirAnswerItem() {
        val answer = recordingActions(listOf(answerMessage("75F and sunny", itemId = "item-4")), emptyList()).getValue(1L).single()
        assertEquals("item-4", answer.itemId)
    }

    @Test
    fun untaggedAndBlankAssistantMessagesAreNotAnswers() {
        val messages = listOf(
            answerMessage("  ", id = 1),
            RecordingToolMessage(1, 2, MessageRole.assistant, null, null, null, content = "Done"),
        )
        assertTrue(recordingActions(messages, emptyList()).isEmpty())
    }

    @Test
    fun supportingDataIsAnAssistiveLookup() {
        val lookup = single(SemanticResult.SupportingData("Read weather/current"))
        assertEquals("Read weather/current", lookup.label)
        assertTrue(lookup.assistive)
        assertNull(lookup.answerText)

        val unnamedLookup = recordingActions(
            listOf(assistantMessage("call-1" to "read_source"), toolMessage(SemanticResult.SupportingData(null))),
            emptyList(),
        ).getValue(1L).single()
        assertEquals("Read source", unnamedLookup.label)
    }

    @Test
    fun actionLoggedUsesItsTitle() {
        val action = single(SemanticResult.ActionLogged("evaluate_js", "Ran JavaScript", success = true, body = "1+1"))
        assertEquals("Ran JavaScript", action.label)
        assertEquals("Ran JavaScript 1+1", action.searchText)
    }

    @Test
    fun contentTextCarriesWhatTheUserWouldSearchFor() {
        assertEquals("milk", SemanticResult.ListItemCreation("milk").contentText)
        assertEquals("Call mom", SemanticResult.TaskCreation("Call mom", null).contentText)
        assertNull(SemanticResult.GenericSuccess.contentText)
        assertNull(SemanticResult.AlarmCreation(LocalTime(7, 0)).contentText)
    }

    @Test
    fun pluginToolsAreLabelledByShortNameWithTheirSource() {
        val messages = listOf(
            assistantMessage("call-1" to "notion__search_pages", "call-2" to "builtin_note__create_note"),
            toolMessage(SemanticResult.GenericSuccess, callId = "call-1", id = 2),
            toolMessage(SemanticResult.GenericSuccess, callId = "call-2", id = 3),
        )
        val (plugin, builtin) = recordingActions(messages, emptyList()).getValue(1L)
        assertEquals("Search pages", plugin.label)
        assertEquals("Notion", plugin.source)
        assertEquals("Create note", builtin.label)
        assertNull(builtin.source)
    }

    @Test
    fun toolSourceIgnoresBuiltinsAndUnnamespacedTools() {
        assertEquals("Home assistant", toolSource("home_assistant__set_light"))
        assertNull(toolSource("builtin_clock__set_alarm"))
        assertNull(toolSource("set_alarm"))
        assertNull(toolSource(null))
    }

    @Test
    fun humanizeToolNameHandlesBlanks() {
        assertEquals("Search contacts", humanizeToolName("search_contacts"))
        assertEquals("Search contacts", humanizeToolName("notion__search_contacts"))
        assertNull(humanizeToolName(null))
        assertNull(humanizeToolName("  "))
    }

    @Test
    fun formatDurationPrettyPrints() {
        assertEquals("20 min", formatDuration(20.minutes))
        assertEquals("1 hr 30 min", formatDuration(90.minutes))
        assertEquals("45 sec", formatDuration(45.seconds))
        assertEquals("2 hr", formatDuration(2.hours))
    }

    @Test
    fun metadataOnlyItemsDoNotLeakIntoLabels() {
        // A reminder item's kind never affects the label; only the result does.
        val item = CachedItem(
            firestoreId = "item-1",
            title = "Different title",
            sourceToolCallId = "call-1",
            metadata = ItemDocument.ItemMetadata.Reminder(repeat = "one_time", notification = "push"),
        )
        val action = recordingActions(
            listOf(toolMessage(SemanticResult.TaskCreation("Call mom", deadline = null))), emptyList(), listOf(item),
        ).getValue(1L).single()
        assertEquals("Call mom", action.label)
        assertEquals("item-1", action.itemId)
    }

    @Test
    fun pendingConfirmationIsAnActionUntilItsRealResultArrives() {
        val request = SemanticResult.RequiresConfirmation("Music", "clear_queue", JsonObject(emptyMap()), destructive = false)
        val pending = recordingActions(
            listOf(assistantMessage("call-1" to "music__clear_queue"), toolMessage(request, id = 2)),
            lists = emptyList(),
        ).getValue(1L).single()
        assertEquals("Needs confirmation", pending.label)
        assertTrue(pending.needsConfirmation)
        assertFalse(pending.intermediate)

        val answered = recordingActions(
            listOf(
                assistantMessage("call-1" to "music__clear_queue"),
                toolMessage(request, id = 2),
                toolMessage(SemanticResult.GenericSuccess, id = 3),
            ),
            lists = emptyList(),
        ).getValue(1L).single()
        assertEquals(SemanticResult.GenericSuccess, answered.result)
    }

    @Test
    fun deniedCallIsNotAFailure() {
        val request = SemanticResult.RequiresConfirmation("Music", "clear_queue", JsonObject(emptyMap()), destructive = false)
        val denied = recordingActions(
            listOf(
                assistantMessage("call-1" to "music__clear_queue"),
                toolMessage(request, id = 2),
                toolMessage(SemanticResult.UserDenied, id = 3),
            ),
            lists = emptyList(),
        ).getValue(1L).single()
        assertFalse(denied.failed)
        assertFalse(denied.needsConfirmation)
        assertNull(denied.errorMessage)
    }
}
