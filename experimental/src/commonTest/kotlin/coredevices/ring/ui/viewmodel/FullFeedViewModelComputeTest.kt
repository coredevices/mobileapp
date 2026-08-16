@file:OptIn(kotlin.time.ExperimentalTime::class)

package coredevices.ring.ui.viewmodel

import coredevices.indexai.data.entity.LocalRecording
import coredevices.indexai.data.entity.RecordingEntryEntity
import coredevices.indexai.data.entity.RecordingEntryStatus
import coredevices.mcp.data.SemanticResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FullFeedViewModelComputeTest {

    private val recording = LocalRecording(id = 1, firestoreId = "rec-1")

    private fun compute(
        semanticResults: Map<Long, SemanticResult?>,
        entries: List<RecordingEntryEntity> = emptyList(),
    ) = FullFeedViewModel.compute(
        recordings = listOf(recording),
        items = emptyList(),
        lists = emptyList(),
        entries = entries,
        query = "",
        semanticResults = semanticResults,
    )

    private fun rowOf(state: FullFeedViewModel.UiState) =
        state.entries.filterIsInstance<FullFeedViewModel.Entry.RecordingRow>().single()

    @Test
    fun failedActionSurfacesItsErrorMessage() {
        val row = rowOf(
            compute(
                mapOf(
                    1L to SemanticResult.GenericFailure(
                        "Failed to create reminder: Notification permission not granted."
                    )
                )
            )
        )
        assertEquals(
            "Failed to create reminder: Notification permission not granted.",
            row.actionError,
        )
    }

    @Test
    fun failureWithoutMessageLeavesNoActionError() {
        assertNull(rowOf(compute(mapOf(1L to SemanticResult.GenericFailure(null)))).actionError)
    }

    @Test
    fun successfulActionLeavesNoActionError() {
        assertNull(rowOf(compute(mapOf(1L to SemanticResult.GenericSuccess))).actionError)
    }

    @Test
    fun retryableTranscriptionFailureHidesStaleActionError() {
        val row = rowOf(
            compute(
                semanticResults = mapOf(1L to SemanticResult.GenericFailure("Stale failure")),
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
}
