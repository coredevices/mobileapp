@file:OptIn(ExperimentalTime::class)

package coredevices.ring.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coredevices.indexai.data.entity.LocalRecording
import coredevices.indexai.data.entity.RecordingEntryEntity
import coredevices.ring.data.entity.room.indexfeed.CachedItem
import coredevices.ring.data.entity.room.indexfeed.CachedList
import coredevices.ring.data.entity.room.indexfeed.kind
import coredevices.ring.database.room.repository.ItemRepository
import coredevices.ring.database.room.repository.ListRepository
import coredevices.ring.database.room.repository.RecordingRepository
import coredevices.ring.service.recordings.RecordingProcessingQueue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Backs the [coredevices.ring.ui.screens.indexfeed.FullFeed] screen.
 * Produces a flat list of `Entry` rows ordered chronologically (oldest →
 * newest) with day-divider rows interleaved between calendar days. Items
 * extracted from each recording become outlined chip pills on the
 * assistant side; same labels/glyphs as the home peek card.
 */
class FullFeedViewModel(
    recordingRepo: RecordingRepository,
    itemRepo: ItemRepository,
    listRepo: ListRepository,
    private val recordingQueue: RecordingProcessingQueue,
) : ViewModel() {

    val query = MutableStateFlow("")

    val state: StateFlow<UiState> = combine(
        recordingRepo.getAllRecordings(),
        itemRepo.getAllFlow(),
        listRepo.getAllFlow(),
        recordingRepo.getAllEntriesFlow(),
        query,
    ) { recs, items, lists, entries, q ->
        compute(recs, items, lists, entries, q.trim())
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = UiState.empty(),
    )

    fun setQuery(q: String) { query.value = q }

    fun submitText(text: String) {
        val msg = text.trim().ifBlank { return }
        viewModelScope.launch {
            recordingQueue.queueTextProcessing(msg)
        }
    }

    data class UiState(val entries: List<Entry>) {
        companion object { fun empty() = UiState(emptyList()) }
    }

    sealed class Entry {
        abstract val uniqueKey: String

        data class DayDivider(val label: String, val ts: Long) : Entry() {
            override val uniqueKey: String get() = "day-$ts"
        }

        data class RecordingRow(
            val recording: LocalRecording,
            val transcription: String,
            val assistantReply: String?,
            val chips: List<Chip>,
        ) : Entry() {
            override val uniqueKey: String get() = "rec-${recording.id}"
        }
    }

    data class Chip(
        val itemId: String,
        val label: String,
        val glyph: String,
    )

    companion object {
        internal fun compute(
            recordings: List<LocalRecording>,
            items: List<CachedItem>,
            lists: List<CachedList>,
            entries: List<RecordingEntryEntity>,
            query: String,
        ): UiState {
            val q = query.lowercase()
            fun match(text: String?) = q.isEmpty() || (text ?: "").lowercase().contains(q)

            val itemsByRecording = items
                .filter { !it.deleted }
                .groupBy { it.sourceRecordingId.orEmpty() }
            // First entry transcription per recording (ordered by timestamp ASC).
            val transcriptionByRec = entries
                .groupBy { it.recordingId }
                .mapValues { (_, es) -> es.firstOrNull()?.transcription.orEmpty() }

            val sorted = recordings.sortedBy { it.localTimestamp.toEpochMilliseconds() }

            val tz = TimeZone.currentSystemDefault()
            val today = Clock.System.now().toLocalDateTime(tz).date

            val out = mutableListOf<Entry>()
            var lastDay: LocalDate? = null
            for (r in sorted) {
                val transcription = transcriptionByRec[r.id].orEmpty()
                val matchedRec = match(r.assistantTitle) || match(transcription)
                val recItems = itemsByRecording[r.firestoreId.orEmpty()].orEmpty() +
                    itemsByRecording["local:${r.id}"].orEmpty()
                val matchedItems = recItems.filter { match(it.title) || match(it.body) }
                if (q.isNotEmpty() && !matchedRec && matchedItems.isEmpty()) continue

                val recDay = r.localTimestamp.toLocalDateTime(tz).date
                if (recDay != lastDay) {
                    out += Entry.DayDivider(
                        label = dayLabel(today, recDay),
                        ts = r.localTimestamp.toEpochMilliseconds(),
                    )
                    lastDay = recDay
                }
                out += Entry.RecordingRow(
                    recording = r,
                    transcription = transcription,
                    assistantReply = null, // wire when we mirror assistantReply onto LocalRecording
                    chips = recItems.take(8).map { item ->
                        Chip(
                            itemId = item.firestoreId,
                            label = IndexFeedViewModel.chipLabel(item, lists).take(64),
                            glyph = chipGlyph(item.kind),
                        )
                    },
                )
            }
            return UiState(entries = out)
        }

        // Mirrors prototype `objectChip` icons (NIcon.alarm/bullets/sparkle/send).
        private fun chipGlyph(kind: String): String = when (kind) {
            "reminder" -> "⏰"
            "scheduled" -> "⏰"
            "note" -> "≡"
            "answer" -> "✨"
            "message" -> "✉"
            "action_log" -> "✉"
            else -> "•"
        }

        /** Calendar-day-aware label for a day divider. */
        private fun dayLabel(today: LocalDate, recDay: LocalDate): String {
            val daysAgo = (today.toEpochDays() - recDay.toEpochDays()).toInt()
            return when {
                daysAgo == 0 -> "Today"
                daysAgo == 1 -> "Yesterday"
                daysAgo in 2..6 -> recDay.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }
                else -> "${recDay.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${recDay.dayOfMonth}"
            }
        }
    }
}
