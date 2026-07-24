@file:OptIn(kotlin.time.ExperimentalTime::class)

package coredevices.ring.ui.viewmodel

import coredevices.indexai.data.entity.LocalRecording
import coredevices.mcp.data.SemanticResult
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
