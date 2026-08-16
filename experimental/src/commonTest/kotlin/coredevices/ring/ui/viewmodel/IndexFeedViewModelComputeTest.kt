@file:OptIn(kotlin.time.ExperimentalTime::class)

package coredevices.ring.ui.viewmodel

import coredevices.indexai.data.entity.ItemDocument
import coredevices.indexai.data.entity.LocalRecording
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

    @Test
    fun calendarEventSemanticResultLabelsChipWhenNoItemExists() {
        // Calendar events create no feed item — the peek must still show the action.
        val state = IndexFeedViewModel.compute(
            recordings = listOf(recording),
            items = emptyList(),
            lists = emptyList(),
            entries = emptyList(),
            query = "",
            semanticResults = mapOf(
                1L to SemanticResult.CalendarEventCreation(
                    title = "Meet Jims",
                    startTime = Clock.System.now(),
                    endTime = Clock.System.now(),
                )
            ),
        )
        val peek = state.recordings.single()
        assertEquals("Added to calendar", peek.primaryChip)
        assertFalse(peek.orphan)
    }

    @Test
    fun failedActionSurfacesItsErrorMessage() {
        val state = IndexFeedViewModel.compute(
            recordings = listOf(recording),
            items = emptyList(),
            lists = emptyList(),
            entries = emptyList(),
            query = "",
            semanticResults = mapOf(
                1L to SemanticResult.GenericFailure(
                    "Failed to create reminder: Notification permission not granted."
                )
            ),
        )
        val peek = state.recordings.single()
        assertEquals(
            "Failed to create reminder: Notification permission not granted.",
            peek.actionError,
        )
    }

    @Test
    fun failureWithoutMessageLeavesNoActionError() {
        val state = IndexFeedViewModel.compute(
            recordings = listOf(recording),
            items = emptyList(),
            lists = emptyList(),
            entries = emptyList(),
            query = "",
            semanticResults = mapOf(1L to SemanticResult.GenericFailure(null)),
        )
        assertNull(state.recordings.single().actionError)
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
    fun recordingWithNoItemsAndNoCalendarResultShowsNoActionTaken() {
        val state = IndexFeedViewModel.compute(
            recordings = listOf(recording),
            items = emptyList(),
            lists = emptyList(),
            entries = emptyList(),
            query = "",
        )
        val peek = state.recordings.single()
        assertEquals("No action taken", peek.primaryChip)
        assertTrue(peek.orphan)
    }
}
