@file:OptIn(ExperimentalTime::class)

package coredevices.ring.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coredevices.indexai.data.entity.LocalRecording
import coredevices.indexai.data.entity.RecordingEntryEntity
import coredevices.indexai.data.entity.RecordingEntryStatus
import coredevices.indexai.database.dao.RecordingToolMessage
import coredevices.ring.data.entity.room.indexfeed.CachedItem
import coredevices.ring.data.entity.room.indexfeed.CachedList
import coredevices.ring.database.room.repository.ItemRepository
import coredevices.ring.database.room.repository.ListRepository
import coredevices.ring.database.room.repository.RecordingRepository
import coredevices.ring.service.recordings.RecordingProcessingQueue
import coredevices.libindex.database.dao.RingTransferDao
import coredevices.libindex.database.entity.RingTransfer
import coredevices.libindex.di.LibIndexCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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
 * newest) with day-divider rows interleaved between calendar days. Each
 * recording's tool results become outlined chip pills on the assistant
 * side; same labels/glyphs as the home peek card.
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
        recordingRepo.getToolMessages(),
        ringTransferDao.getPendingTransfersFlow(),
    ) { inputs, toolMessages, pendingTransfers ->
        compute(
            inputs.recordings, inputs.items, inputs.lists, inputs.entries,
            inputs.query.trim(), toolMessages, pendingTransfers,
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
            val chips: List<RecordingAction>,
            /** Message from the last failed action (e.g. a missing permission). */
            val actionError: String? = null,
        ) : Entry() {
            override val uniqueKey: String get() = "rec-${recording.id}"
        }

        /** A ring transfer still in flight (or queued for processing) that has
         *  no [LocalRecording] yet — rendered as a "Transferring…" bubble. */
        data class PendingTransfer(val transfer: RingTransfer) : Entry() {
            override val uniqueKey: String get() = "transfer-${transfer.id}"
        }
    }

    companion object {
        internal fun compute(
            recordings: List<LocalRecording>,
            items: List<CachedItem>,
            lists: List<CachedList>,
            entries: List<RecordingEntryEntity>,
            query: String,
            toolMessages: List<RecordingToolMessage> = emptyList(),
            pendingTransfers: List<RingTransfer> = emptyList(),
        ): UiState {
            val q = query.lowercase()
            fun match(text: String?) = q.isEmpty() || (text ?: "").lowercase().contains(q)

            val actionsByRec = recordingActions(toolMessages, lists, items)
            val entriesByRec = entries.groupBy { it.recordingId }

            val sorted = recordings.sortedBy { it.localTimestamp.toEpochMilliseconds() }

            val tz = TimeZone.currentSystemDefault()
            val today = Clock.System.now().toLocalDateTime(tz).date

            val out = mutableListOf<Entry>()
            var lastDay: LocalDate? = null

            fun emitDayDivider(ts: Instant) {
                val day = ts.toLocalDateTime(tz).date
                if (day != lastDay) {
                    out += Entry.DayDivider(
                        label = dayLabel(today, day),
                        ts = ts.toEpochMilliseconds(),
                    )
                    lastDay = day
                }
            }

            val pendingSorted = if (q.isEmpty()) {
                pendingTransfers.sortedBy { it.createdAt.toEpochMilliseconds() }
            } else emptyList()
            var pendingIdx = 0
            fun flushPendingBefore(ts: Instant?) {
                while (pendingIdx < pendingSorted.size &&
                    (ts == null || pendingSorted[pendingIdx].createdAt < ts)
                ) {
                    val t = pendingSorted[pendingIdx++]
                    emitDayDivider(t.createdAt)
                    out += Entry.PendingTransfer(t)
                }
            }

            for (r in sorted) {
                val recordingEntries = entriesByRec[r.id].orEmpty()
                    .sortedWith(compareBy<RecordingEntryEntity> { it.timestamp }.thenBy { it.id })
                val latestEntry = recordingEntries.lastOrNull()
                val transcription = latestEntry?.transcription?.takeIf { it.isNotBlank() }
                    ?: recordingEntries.firstOrNull()?.transcription.orEmpty()
                val retryEntry = latestEntry?.takeIf { it.isRetryableTranscriptionFailure() }
                val matchedRec = match(r.assistantTitle) || match(transcription)
                val actions = actionsByRec[r.id].orEmpty().filterNot { it.assistive }
                val matchedAction = actions.any { match(it.label) || match(it.searchText) }
                if (q.isNotEmpty() && !matchedRec && !matchedAction) continue

                flushPendingBefore(r.localTimestamp)
                emitDayDivider(r.localTimestamp)
                out += Entry.RecordingRow(
                    recording = r,
                    transcription = transcription,
                    retryEntry = retryEntry,
                    assistantReply = null, // wire when we mirror assistantReply onto LocalRecording
                    chips = actions.filterNot { it.failed }.take(8),
                    // A retryable transcription failure means the agent never ran
                    // this time round; any stored failure is from an earlier
                    // attempt, so don't surface it.
                    actionError = actions.lastOrNull()?.errorMessage?.takeIf { retryEntry == null },
                )
            }
            flushPendingBefore(null)
            return UiState(entries = out)
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
