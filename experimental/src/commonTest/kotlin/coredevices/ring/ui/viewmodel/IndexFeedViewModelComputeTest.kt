@file:OptIn(kotlin.time.ExperimentalTime::class)

package coredevices.ring.ui.viewmodel

import coredevices.indexai.data.entity.FunctionToolCall
import coredevices.indexai.data.entity.ItemDocument
import coredevices.indexai.data.entity.LocalRecording
import coredevices.indexai.data.entity.MessageRole
import coredevices.indexai.data.entity.MessageTag
import coredevices.indexai.data.entity.RecordingEntryEntity
import coredevices.indexai.data.entity.RecordingEntryStatus
import coredevices.indexai.data.entity.ToolCall
import coredevices.indexai.database.dao.RecordingToolMessage
import coredevices.libindex.database.entity.RingTransfer
import coredevices.libindex.database.entity.RingTransferStatus
import coredevices.mcp.data.SemanticResult
import coredevices.ring.data.entity.room.indexfeed.CachedItem
import coredevices.ring.data.entity.room.indexfeed.CachedList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock

class IndexFeedViewModelComputeTest {

    private val recording = LocalRecording(id = 1, firestoreId = "rec-1")

    private fun toolMessage(result: SemanticResult, callId: String, id: Long) =
        RecordingToolMessage(1, id, MessageRole.tool, null, callId, result)

    private fun assistantMessage(vararg calls: Pair<String, String>, id: Long = 1) =
        RecordingToolMessage(
            1, id, MessageRole.assistant,
            calls.map { (callId, name) -> ToolCall(callId, "function", FunctionToolCall(name, "{}")) },
            null, null,
        )

    private fun compute(
        vararg results: SemanticResult,
        entries: List<RecordingEntryEntity> = emptyList(),
        extraMessages: List<RecordingToolMessage> = emptyList(),
    ) = IndexFeedViewModel.compute(
        recordings = listOf(recording),
        items = emptyList(),
        lists = emptyList(),
        entries = entries,
        query = "",
        toolMessages = extraMessages + results.mapIndexed { i, r -> toolMessage(r, "call-$i", 10L + i) },
    )

    @Test
    fun calendarEventLabelsTheChipWithoutAnyItem() {
        val peek = compute(
            SemanticResult.CalendarEventCreation(
                title = "Meet Jims",
                startTime = Clock.System.now(),
                endTime = Clock.System.now(),
            )
        ).recordings.single()
        assertEquals("Added to calendar", peek.primaryChip)
        assertFalse(peek.orphan)
    }

    @Test
    fun pendingTransfersSurfaceUnlessSearching() {
        val transfer = RingTransfer(
            id = 7,
            recordingId = null,
            recordingEntryId = null,
            isCurrentIndexIteration = true,
            transferInfo = null,
            status = RingTransferStatus.Started,
        )
        val state = IndexFeedViewModel.compute(
            recordings = emptyList(),
            items = emptyList(),
            lists = emptyList(),
            entries = emptyList(),
            query = "",
            pendingTransfers = listOf(transfer),
        )
        assertEquals(listOf(7L), state.pendingTransfers.map { it.id })

        val searching = IndexFeedViewModel.compute(
            recordings = emptyList(),
            items = emptyList(),
            lists = emptyList(),
            entries = emptyList(),
            query = "milk",
            pendingTransfers = listOf(transfer),
        )
        assertTrue(searching.pendingTransfers.isEmpty())
    }

    @Test
    fun toolResultWithoutItemLabelsTheChipByToolName() {
        val peek = compute(
            SemanticResult.GenericSuccess,
            extraMessages = listOf(assistantMessage("call-0" to "set_thermostat")),
        ).recordings.single()
        assertEquals("Set thermostat", peek.primaryChip)
        assertFalse(peek.orphan)
    }

    @Test
    fun firstSuccessfulActionIsThePrimaryChip() {
        val peek = compute(
            SemanticResult.GenericFailure("Notifications are off"),
            SemanticResult.ListItemCreation("milk"),
            SemanticResult.TaskCreation("Call mom", deadline = null),
        ).recordings.single()
        assertEquals("Added to Notes to self", peek.primaryChip)
        // The last action succeeded, so the earlier failure isn't surfaced.
        assertNull(peek.actionError)
    }

    @Test
    fun failedActionSurfacesItsErrorMessage() {
        val peek = compute(
            SemanticResult.GenericFailure("Failed to create reminder: Notification permission not granted.")
        ).recordings.single()
        assertEquals(
            "Failed to create reminder: Notification permission not granted.",
            peek.actionError,
        )
        assertEquals("No action taken", peek.primaryChip)
        assertTrue(peek.orphan)
    }

    @Test
    fun failureAfterASuccessShowsBothChipAndError() {
        val peek = compute(
            SemanticResult.ListItemCreation("milk"),
            SemanticResult.GenericFailure("Garage offline"),
        ).recordings.single()
        assertEquals("Added to Notes to self", peek.primaryChip)
        assertEquals("Garage offline", peek.actionError)
    }

    @Test
    fun failureWithoutMessageLeavesNoActionError() {
        assertNull(compute(SemanticResult.GenericFailure(null)).recordings.single().actionError)
    }

    @Test
    fun retryableTranscriptionFailureHidesStaleActionError() {
        val peek = compute(
            SemanticResult.GenericFailure("Stale failure"),
            entries = listOf(RecordingEntryEntity(id = 1, recordingId = 1, status = RecordingEntryStatus.transcription_error)),
        ).recordings.single()
        assertNull(peek.actionError)
    }

    @Test
    fun assistiveLookupsAreNotActions() {
        val peek = compute(SemanticResult.SupportingData("Read weather/current"))
            .recordings.single()
        assertEquals("No action taken", peek.primaryChip)
        assertTrue(peek.orphan)
    }

    @Test
    fun taggedAnswerIsTheAction() {
        val answer = RecordingToolMessage(
            1, 5, MessageRole.assistant, null, null, null,
            content = "It is 5pm", messageTags = listOf(MessageTag.AnswerResponse()),
        )
        val peek = compute(extraMessages = listOf(answer)).recordings.single()
        assertEquals("Answered", peek.primaryChip)
        assertFalse(peek.orphan)
    }

    @Test
    fun doneChecklistItemsAreLeftOutOfTheListCardPreview() {
        val list = CachedList(firestoreId = "list-1", title = "Groceries", listKind = "checklist")
        fun item(id: String, title: String, done: Boolean) = CachedItem(
            firestoreId = id,
            title = title,
            done = done,
            parentListIdsCsv = list.firestoreId,
            metadata = ItemDocument.ItemMetadata.Checklist,
        )
        val state = IndexFeedViewModel.compute(
            recordings = emptyList(),
            items = listOf(item("i-1", "Milk", done = true), item("i-2", "Onions", done = false)),
            lists = listOf(list),
            entries = emptyList(),
            query = "",
        )
        assertEquals(listOf("Onions"), state.notesLists.single().items.map { it.title })

        val searched = IndexFeedViewModel.compute(
            recordings = emptyList(),
            items = listOf(item("i-1", "Milk", done = true), item("i-2", "Onions", done = false)),
            lists = listOf(list),
            entries = emptyList(),
            query = "milk",
        )
        assertEquals(listOf("Milk"), searched.notesLists.single().items.map { it.title })
    }

    @Test
    fun recordingWithNoToolResultsShowsNoActionTaken() {
        val peek = compute().recordings.single()
        assertEquals("No action taken", peek.primaryChip)
        assertTrue(peek.orphan)
    }

    private fun assistantFromModel(model: String?) = RecordingToolMessage(
        1, 20, MessageRole.assistant, null, null, null, content = "ok", languageModelUsed = model,
    )

    @Test
    fun onDeviceAgentMarksTheRecordingProcessedOffline() {
        assertTrue(compute(extraMessages = listOf(assistantFromModel("cactus-qwen"))).recordings.single().processedOffline)
        assertFalse(compute(extraMessages = listOf(assistantFromModel("gemini-flash"))).recordings.single().processedOffline)
        assertFalse(compute(extraMessages = listOf(assistantFromModel(null))).recordings.single().processedOffline)
    }

    @Test
    fun localSttFallbackMarksTheRecordingProcessedOffline() {
        fun offline(fallback: Boolean) = compute(
            entries = listOf(RecordingEntryEntity(id = 1, recordingId = 1, transcribedViaLocalFallback = fallback)),
        ).recordings.single().processedOffline
        assertTrue(offline(true))
        assertFalse(offline(false))
    }
}
