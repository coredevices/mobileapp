@file:OptIn(kotlin.time.ExperimentalTime::class)

package coredevices.ring.ui.viewmodel

import coredevices.indexai.data.entity.LocalRecording
import coredevices.indexai.data.entity.MessageRole
import coredevices.indexai.data.entity.RecordingEntryEntity
import coredevices.indexai.data.entity.RecordingEntryStatus
import coredevices.indexai.database.dao.RecordingToolMessage
import coredevices.libindex.database.entity.RingTransfer
import coredevices.libindex.database.entity.RingTransferStatus
import coredevices.mcp.data.SemanticResult
import coredevices.ring.data.entity.room.indexfeed.CachedItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours

class FullFeedViewModelComputeTest {

    private val recording = LocalRecording(id = 1, firestoreId = "rec-1")

    private fun toolMessage(result: SemanticResult, callId: String, id: Long) =
        RecordingToolMessage(1, id, MessageRole.tool, null, callId, result)

    private fun compute(
        vararg results: SemanticResult,
        entries: List<RecordingEntryEntity> = emptyList(),
        items: List<CachedItem> = emptyList(),
        query: String = "",
    ) = FullFeedViewModel.compute(
        recordings = listOf(recording),
        items = items,
        lists = emptyList(),
        entries = entries,
        query = query,
        toolMessages = results.mapIndexed { i, r -> toolMessage(r, "call-$i", 10L + i) },
    )

    private fun rowOf(state: FullFeedViewModel.UiState) =
        state.entries.filterIsInstance<FullFeedViewModel.Entry.RecordingRow>().single()

    @Test
    fun eachSuccessfulResultBecomesAChipInCallOrder() {
        val row = rowOf(
            compute(
                SemanticResult.ListItemCreation("milk", itemId = "item-1"),
                SemanticResult.SupportingData("Read weather"),
                SemanticResult.TaskCreation("Call mom", deadline = null),
                SemanticResult.GenericFailure("Garage offline"),
            )
        )
        assertEquals(listOf("Added to Notes to self", "Call mom"), row.chips.map { it.label })
        assertEquals("item-1", row.chips.first().itemId)
        assertEquals("Garage offline", row.actionError)
    }

    @Test
    fun legacyChipNavigatesToTheItemMatchedByToolCall() {
        val row = rowOf(
            compute(
                SemanticResult.ListItemCreation("milk"),
                items = listOf(CachedItem(firestoreId = "item-7", title = "milk", sourceToolCallId = "call-0")),
            )
        )
        assertEquals("item-7", row.chips.single().itemId)
    }

    @Test
    fun failedActionSurfacesItsErrorMessage() {
        val row = rowOf(compute(SemanticResult.GenericFailure("Failed to create reminder: Notification permission not granted.")))
        assertEquals("Failed to create reminder: Notification permission not granted.", row.actionError)
        assertTrue(row.chips.isEmpty())
    }

    @Test
    fun failureWithoutMessageLeavesNoActionError() {
        assertNull(rowOf(compute(SemanticResult.GenericFailure(null))).actionError)
    }

    @Test
    fun successfulActionLeavesNoActionError() {
        assertNull(rowOf(compute(SemanticResult.GenericSuccess)).actionError)
    }

    @Test
    fun pendingTransferAppendsPlaceholderRow() {
        val transfer = RingTransfer(
            id = 7,
            recordingId = null,
            recordingEntryId = null,
            isCurrentIndexIteration = true,
            transferInfo = null,
            status = RingTransferStatus.Started,
        )
        val state = FullFeedViewModel.compute(
            recordings = listOf(recording),
            items = emptyList(),
            lists = emptyList(),
            entries = emptyList(),
            query = "",
            pendingTransfers = listOf(transfer),
        )
        val last = state.entries.last()
        assertTrue(last is FullFeedViewModel.Entry.PendingTransfer)
        assertEquals(7, last.transfer.id)
    }

    @Test
    fun pendingTransferMergesChronologicallyWithRecordings() {
        val now = kotlin.time.Clock.System.now()
        val transfer = RingTransfer(
            id = 7,
            recordingId = null,
            recordingEntryId = null,
            isCurrentIndexIteration = true,
            transferInfo = null,
            status = RingTransferStatus.Started,
            createdAt = now - 1.hours,
        )
        val newerRecording = LocalRecording(id = 2, firestoreId = "rec-2", localTimestamp = now)
        val state = FullFeedViewModel.compute(
            recordings = listOf(newerRecording),
            items = emptyList(),
            lists = emptyList(),
            entries = emptyList(),
            query = "",
            pendingTransfers = listOf(transfer),
        )
        val rows = state.entries.filter { it !is FullFeedViewModel.Entry.DayDivider }
        assertEquals(
            listOf("transfer-7", "rec-2"),
            rows.map { it.uniqueKey },
        )
    }

    @Test
    fun pendingTransfersAreHiddenWhileSearching() {
        val transfer = RingTransfer(
            id = 7,
            recordingId = null,
            recordingEntryId = null,
            isCurrentIndexIteration = true,
            transferInfo = null,
            status = RingTransferStatus.Started,
        )
        val state = FullFeedViewModel.compute(
            recordings = emptyList(),
            items = emptyList(),
            lists = emptyList(),
            entries = emptyList(),
            query = "milk",
            pendingTransfers = listOf(transfer),
        )
        assertTrue(state.entries.isEmpty())
    }

    @Test
    fun retryableTranscriptionFailureHidesStaleActionError() {
        val row = rowOf(
            compute(
                SemanticResult.GenericFailure("Stale failure"),
                entries = listOf(
                    RecordingEntryEntity(
                        id = 1,
                        recordingId = 1,
                        status = RecordingEntryStatus.transcription_error,
                    )
                ),
            )
        )
        assertNull(row.actionError)
    }

    @Test
    fun searchMatchesWhatTheActionsSaidOrCreated() {
        assertEquals(1, compute(SemanticResult.ListItemCreation("buy milk"), query = "milk").entries.count { it is FullFeedViewModel.Entry.RecordingRow })
        assertEquals(1, compute(SemanticResult.ListItemCreation("buy milk"), query = "notes to self").entries.count { it is FullFeedViewModel.Entry.RecordingRow })
        assertTrue(compute(SemanticResult.ListItemCreation("buy milk"), query = "eggs").entries.isEmpty())
    }
}
