@file:OptIn(ExperimentalTime::class)

package coredevices.ring.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coredevices.indexai.data.entity.LocalRecording
import coredevices.indexai.data.entity.RecordingEntryEntity
import coredevices.indexai.data.entity.RecordingEntryStatus
import coredevices.mcp.data.SemanticResult
import coredevices.ring.data.entity.room.indexfeed.CachedItem
import coredevices.ring.data.entity.room.indexfeed.CachedList
import coredevices.ring.data.entity.room.indexfeed.kind
import coredevices.ring.database.room.repository.ItemRepository
import coredevices.ring.database.room.repository.ListRepository
import coredevices.ring.database.room.repository.RecordingRepository
import coredevices.ring.service.recordings.RecordingProcessingQueue
import coredevices.libindex.database.dao.RingTransferDao
import coredevices.libindex.di.LibIndexCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    private val ringTransferDao: RingTransferDao,
    private val recordingQueue: RecordingProcessingQueue,
    private val appScope: LibIndexCoroutineScope,
) : ViewModel() {

    val query = MutableStateFlow("")

    val state: StateFlow<UiState> = combine(
        combine(
            recordingRepo.getAllRecordings(),
            itemRepo.getAllFlow(),
            listRepo.getAllFlow(),
            recordingRepo.getAllEntriesFlow(),
            query,
        ) { recs, items, lists, entries, q ->
            Inputs(recs, items, lists, entries, q)
        },
        recordingRepo.getLatestToolSemanticResults()
            .map { results -> results.associate { it.recordingId to it.semanticResult } }
            .distinctUntilChanged(),
    ) { inputs, semanticResults ->
        compute(
            inputs.recordings, inputs.items, inputs.lists, inputs.entries,
            inputs.query.trim(), semanticResults,
        )
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

    fun retryRecording(recordingId: Long, entry: RecordingEntryEntity) {
        appScope.launch {
            val transfer = withContext(Dispatchers.IO) {
                ringTransferDao.getByRecordingId(recordingId).firstOrNull()
            }
            if (transfer != null) {
                recordingQueue.retryRecording(
                    transferId = transfer.id,
                    buttonSequence = null,
                    recordingId = recordingId,
                    recordingEntryId = entry.id,
                )
            } else {
                val fileId = entry.fileName ?: return@launch
                recordingQueue.retryLocalRecording(
                    fileId = fileId,
                    buttonSequence = null,
                    recordingId = recordingId,
                    recordingEntryId = entry.id,
                )
            }
        }
    }

    private data class Inputs(
        val recordings: List<LocalRecording>,
        val items: List<CachedItem>,
        val lists: List<CachedList>,
        val entries: List<RecordingEntryEntity>,
        val query: String,
    )

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
            val retryEntry: RecordingEntryEntity?,
            val assistantReply: String?,
            val chips: List<Chip>,
            /** Message from a failed action (e.g. a missing permission).
             *  Failures produce no item, so without this the assistant side
             *  of the row would be empty. */
            val actionError: String? = null,
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
            /** Latest tool-call semantic result per recording id, used to surface
             *  failures — they create no item, so there's no chip to render. */
            semanticResults: Map<Long, SemanticResult?> = emptyMap(),
        ): UiState {
            val q = query.lowercase()
            fun match(text: String?) = q.isEmpty() || (text ?: "").lowercase().contains(q)

            val itemsByRecording = items
                .filter { !it.deleted }
                .groupBy { it.sourceRecordingId.orEmpty() }
            val entriesByRec = entries.groupBy { it.recordingId }

            val sorted = recordings.sortedBy { it.localTimestamp.toEpochMilliseconds() }

            val tz = TimeZone.currentSystemDefault()
            val today = Clock.System.now().toLocalDateTime(tz).date

            val out = mutableListOf<Entry>()
            var lastDay: LocalDate? = null
            for (r in sorted) {
                val recordingEntries = entriesByRec[r.id].orEmpty()
                    .sortedWith(compareBy<RecordingEntryEntity> { it.timestamp }.thenBy { it.id })
                val latestEntry = recordingEntries.lastOrNull()
                val transcription = latestEntry?.transcription?.takeIf { it.isNotBlank() }
                    ?: recordingEntries.firstOrNull()?.transcription.orEmpty()
                val retryEntry = latestEntry?.takeIf { it.isRetryableTranscriptionFailure() }
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
                    retryEntry = retryEntry,
                    assistantReply = null, // wire when we mirror assistantReply onto LocalRecording
                    chips = recItems.take(8).map { item ->
                        Chip(
                            itemId = item.firestoreId,
                            // chipLabel already yields "🔒 Encrypted" for locked
                            // rows; drop the kind glyph so the lock isn't doubled.
                            label = IndexFeedViewModel.chipLabel(item, lists).take(64),
                            glyph = if (item.locked) "" else chipGlyph(item.kind),
                        )
                    },
                    // A retryable transcription failure means the agent never ran
                    // this time round; any stored failure is from an earlier
                    // attempt, so don't surface it.
                    actionError = (semanticResults[r.id] as? SemanticResult.GenericFailure)
                        ?.userErrorMessage?.takeIf { it.isNotBlank() && retryEntry == null },
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
            "delegated" -> "✉"
            "calendar_event" -> "📅"
            else -> "•"
        }

        private fun RecordingEntryEntity.isRetryableTranscriptionFailure(): Boolean =
            status == RecordingEntryStatus.transcription_error

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
