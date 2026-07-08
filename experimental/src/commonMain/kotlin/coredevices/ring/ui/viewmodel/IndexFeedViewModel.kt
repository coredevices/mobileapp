@file:OptIn(ExperimentalTime::class)

package coredevices.ring.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coredevices.indexai.data.entity.LocalRecording
import coredevices.indexai.data.entity.RecordingEntryEntity
import coredevices.indexai.data.entity.RecordingEntryStatus
import coredevices.libindex.database.dao.RingTransferDao
import coredevices.libindex.di.LibIndexCoroutineScope
import coredevices.ring.data.entity.room.indexfeed.CachedItem
import coredevices.ring.data.entity.room.indexfeed.CachedList
import coredevices.ring.data.entity.room.indexfeed.fields
import coredevices.ring.data.entity.room.indexfeed.kind
import coredevices.ring.database.room.repository.ItemRepository
import coredevices.ring.database.room.repository.ListRepository
import coredevices.ring.database.room.repository.RecordingRepository
import coredevices.ring.service.indexfeed.DefaultListsBootstrap.Companion.LIST_NOTES_SELF_ID
import coredevices.ring.service.indexfeed.DefaultListsBootstrap.Companion.LIST_TODOS_ID
import coredevices.ring.service.indexfeed.DefaultListsBootstrap.Companion.SEED_TODOS
import coredevices.ring.service.recordings.RecordingProcessingQueue
import coredevices.ring.ui.viewmodel.IndexFeedViewModel.Companion.STRIKE_THROUGH_MS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Single source for everything the Index home screen needs: recordings,
 * todos, the notes-list grid, recent answers, and a search query.
 *
 * All flows derive from the same combined upstream so the UI gets a single
 * coherent snapshot per emission instead of cascading recompositions.
 */
class IndexFeedViewModel(
    recordingRepo: RecordingRepository,
    private val itemRepo: ItemRepository,
    listRepo: ListRepository,
    private val recordingQueue: RecordingProcessingQueue,
    private val ringTransferDao: RingTransferDao,
    private val appScope: LibIndexCoroutineScope,
) : ViewModel() {

    /** What the user typed into the search bar. Empty = not searching. */
    val query = MutableStateFlow("")

    /** Item ids that were just toggled to done and should linger in the
     *  list with strikethrough + faded opacity for [STRIKE_THROUGH_MS]
     *  before being filtered out. Mirrors the prototype's
     *  `animatingDoneIds` set in feeds.jsx / details.jsx. */
    private val animatingDoneIds = MutableStateFlow<Set<String>>(emptySet())
    /** Active strike-through removal jobs keyed by item id, so a rapid
     *  done → undone → done sequence cancels the prior delay instead of
     *  the prior coroutine removing the id while the new animation is
     *  still in flight. */
    private val animatingDoneJobs = mutableMapOf<String, kotlinx.coroutines.Job>()

    /**
     * Snapshot of every input flow plus the live query. The UI reads
     * [state] and renders sections off of [UiState.searching] /
     * [UiState.matches].
     */
    val state: StateFlow<UiState> = combine(
        combine(
            recordingRepo.getAllRecordings(),
            itemRepo.getAllFlow(),
            listRepo.getAllFlow(),
            recordingRepo.getAllEntriesFlow(),
            query,
        ) { recordings, items, lists, entries, q ->
            Quintuple(recordings, items, lists, entries, q)
        },
        animatingDoneIds,
    ) { tuple, animating ->
        compute(
            tuple.recordings, tuple.items, tuple.lists, tuple.entries,
            tuple.query.trim(), animating,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = UiState.empty(),
    )

    fun setQuery(q: String) {
        query.value = q
    }

    fun clearQuery() {
        query.value = ""
    }

    /** Submit a typed prompt the same way the legacy chat input did —
     *  enqueues into the existing recording processing pipeline so the
     *  agent runs against it. */
    fun submitText(text: String) {
        val msg = text.trim().ifBlank { return }
        viewModelScope.launch {
            recordingQueue.queueTextProcessing(msg)
        }
    }

    /** Re-enqueue a recording whose transcription failed. Mirrors
     *  [FullFeedViewModel.retryRecording] / RecordingDetailsViewModel — a
     *  ring transfer retries from the original audio, otherwise we retry the
     *  locally-stored file. */
    fun retryRecording(recordingId: Long, entry: RecordingEntryEntity) {
        viewModelScope.launch {
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

    /** Flip an item's `done` flag. When the toggle goes from undone →
     *  done, the id is added to [animatingDoneIds] *before* the DB write
     *  fires — otherwise the flow re-emits with `done=true` and the row
     *  briefly drops out of the active list before the linger logic
     *  catches it, causing a visible flicker. */
    fun toggleDoneById(itemId: String) {
        val s = state.value
        val item = (s.todosPreview + s.notesLists.flatMap { it.items } + s.answersPreview)
            .firstOrNull { it.firestoreId == itemId } ?: return
        if (item.locked) return // can't toggle a row we can't read
        val wasDone = item.done
        if (!wasDone) {
            // Mark animating BEFORE the DB write so the upcoming flow
            // re-emit doesn't cause a flicker (row leaving + re-entering
            // the active bucket).
            animatingDoneJobs.remove(itemId)?.cancel()
            animatingDoneIds.value += itemId
        } else {
            // Toggling back to undone — cancel any in-flight strike
            // removal so the row doesn't disappear unexpectedly.
            animatingDoneJobs.remove(itemId)?.cancel()
            animatingDoneIds.value -= itemId
        }
        viewModelScope.launch(Dispatchers.IO) {
            val updated = item.toDocument().copy(
                done = !item.done,
                updatedAt = Clock.System.now(),
            )
            itemRepo.setItem(itemId, updated)
            if (!wasDone) {
                animatingDoneJobs[itemId] = viewModelScope.launch {
                    delay(STRIKE_THROUGH_MS)
                    animatingDoneIds.value -= itemId
                    animatingDoneJobs.remove(itemId)
                }
            }
        }
    }

    private data class Quintuple(
        val recordings: List<LocalRecording>,
        val items: List<CachedItem>,
        val lists: List<CachedList>,
        val entries: List<RecordingEntryEntity>,
        val query: String,
    )

    // ── Pure compute ───────────────────────────────────────────────────

    data class UiState(
        val recordings: List<RecordingPeek>,
        /** Tasks shown in the home Todos section, paged horizontally by the UI. */
        val todosPreview: List<CachedItem>,
        /** Total Todos count (for the section header). */
        val totalTodos: Int,
        /** Top-N notes lists (excluding the system Todos list). */
        val notesLists: List<NotesList>,
        /** Total notes-list count for the section header. */
        val totalNotesLists: Int,
        /** Recent answer items (≤ ANSWER_PREVIEW_LIMIT). */
        val answersPreview: List<CachedItem>,
        /** Total answers count for the section header. */
        val totalAnswers: Int,
        val searching: Boolean,
        val matches: Int,
    ) {
        data class NotesList(
            val list: CachedList,
            val items: List<CachedItem>,
        )

        data class RecordingPeek(
            val recording: LocalRecording,
            /** First-entry transcription (the user's spoken text). May be
             *  empty if the recording is still being processed. */
            val transcription: String,
            /** Localized label for the most-relevant extracted item, e.g.
             *  "Added to Notes to self", "Reminder · take out the trash",
             *  "Alarm · 09:00", or "No action taken" when nothing was made. */
            val primaryChip: String,
            val orphan: Boolean,
            /** Latest entry when its transcription failed and can be retried.
             *  Non-null → the peek card shows "Transcription error" + a retry
             *  button instead of the primary action chip. */
            val retryEntry: RecordingEntryEntity? = null,
        )

        companion object {
            fun empty() = UiState(
                recordings = emptyList(),
                todosPreview = emptyList(),
                totalTodos = 0,
                notesLists = emptyList(),
                totalNotesLists = 0,
                answersPreview = emptyList(),
                totalAnswers = 0,
                searching = false,
                matches = 0,
            )
        }
    }

    companion object {
        const val TODO_PAGE_SIZE = 6
        const val NOTES_PAGE_SIZE = 4
        const val ANSWER_PREVIEW_LIMIT = 3
        /** Time a just-completed item lingers with strikethrough + faded
         *  opacity before being filtered out. Matches the prototype. */
        const val STRIKE_THROUGH_MS = 600L

        internal fun compute(
            recordings: List<LocalRecording>,
            items: List<CachedItem>,
            lists: List<CachedList>,
            entries: List<RecordingEntryEntity>,
            query: String,
            animatingDoneIds: Set<String> = emptySet(),
        ): UiState {
            val isSearching = query.isNotEmpty()
            val q = query.lowercase()
            fun match(s: String?) = !isSearching || (s ?: "").lowercase().contains(q)

            val itemsByRecording = items
                .filter { !it.deleted }
                .groupBy { it.sourceRecordingId.orEmpty() }
            val entriesByRec = entries.groupBy { it.recordingId }
            val transcriptionByRec = entriesByRec
                .mapValues { (_, es) -> es.firstOrNull()?.transcription.orEmpty() }
            val recordingsView = (if (isSearching) {
                recordings.filter { match(it.assistantTitle) || match(transcriptionByRec[it.id]) }
            } else {
                recordings
            }).sortedByDescending { it.localTimestamp.toEpochMilliseconds() }
                .map { rec ->
                    val recItems = itemsByRecording[rec.firestoreId.orEmpty()].orEmpty() +
                        itemsByRecording["local:${rec.id}"].orEmpty()
                    val primary = recItems.firstOrNull()
                    // Latest entry decides the error state — a recording can
                    // accrue retries, so only the most recent attempt's status
                    // is meaningful. Matches FullFeedViewModel.
                    val latestEntry = entriesByRec[rec.id].orEmpty()
                        .sortedWith(compareBy<RecordingEntryEntity> { it.timestamp }.thenBy { it.id })
                        .lastOrNull()
                    val retryEntry = latestEntry
                        ?.takeIf { it.status == RecordingEntryStatus.transcription_error }
                    UiState.RecordingPeek(
                        recording = rec,
                        transcription = transcriptionByRec[rec.id].orEmpty(),
                        primaryChip = primary?.let { chipLabel(it, lists) } ?: "No action taken",
                        orphan = primary == null,
                        retryEntry = retryEntry,
                    )
                }

            // Todos: every non-deleted item that lives in the Todos list,
            // regardless of kind (reminder / scheduled / message / etc.) —
            // so the home preview's count and rows match what the user
            // sees when they tap into the Todos list detail page.
            // Skip done unless the id is in animatingDoneIds (mid-
            // strike-through linger).
            val nowMs = Clock.System.now().toEpochMilliseconds()
            val urgentCutoffMs = nowMs + 24L * 60L * 60L * 1000L
            val rawTodos = items
                .asSequence()
                .filter { !it.deleted }
                .filter { it.parentListIds().contains(LIST_TODOS_ID) }
                .filter { !it.done || it.firestoreId in animatingDoneIds }
                .filter { match(it.title) }
                .sortedWith(
                    compareBy<CachedItem> { task ->
                        val dueMs = task.dueAt?.toEpochMilliseconds()
                        when {
                            dueMs != null && dueMs > nowMs && dueMs <= urgentCutoffMs -> 0
                            dueMs != null && dueMs <= nowMs -> 1
                            dueMs == null -> 2
                            else -> 3
                        }
                    }
                        .thenBy { task ->
                            val dueMs = task.dueAt?.toEpochMilliseconds()
                            when {
                                dueMs != null && dueMs > nowMs && dueMs <= urgentCutoffMs -> dueMs
                                dueMs != null && dueMs <= nowMs -> -dueMs
                                dueMs == null -> Long.MAX_VALUE
                                else -> dueMs
                            }
                        }
                        .thenBy { it.createdAt.toEpochMilliseconds() }
                        .thenBy { it.title.lowercase() },
                )
                .toList()
            val todosPreview = rawTodos

            // Notes lists, excluding the Todos list. Sort by *effective*
            // updatedAt — the max of the list's own updatedAt and the
            // newest item's updatedAt. The list-level updatedAt only
            // changes when the list itself is renamed; without rolling
            // up child timestamps, a list users actively add to would
            // never float to the top.
            val itemsByList = items
                .asSequence()
                .filter { !it.deleted }
                // Encrypted-without-key rows have no readable title — keep them
                // out of the notes grid entirely rather than showing placeholders.
                .filter { !it.locked }
                // Notes lists hold both `note` and `checklist` items —
                // a checklist is just a note with a tickable checkbox,
                // and the user can convert between the two via the
                // type dropdown on the item-detail edit hero. Keeping
                // `note` only here used to silently hide an item from
                // its parent list the moment it was converted.
                .filter { it.kind == "note" || it.kind == "checklist" }
                .sortedByDescending { it.createdAt.toEpochMilliseconds() }
                .toList()
                .groupBy { it.parentListIds().firstOrNull() ?: LIST_NOTES_SELF_ID }

            val notesListEntities = lists
                .filter { !it.deleted }
                .filter { it.seed != SEED_TODOS && it.firestoreId != LIST_TODOS_ID }
                .sortedByDescending { l ->
                    val ownTs = l.updatedAt.toEpochMilliseconds()
                    val childTs = (itemsByList[l.firestoreId] ?: emptyList())
                        .maxOfOrNull { it.updatedAt.toEpochMilliseconds() } ?: 0L
                    maxOf(ownTs, childTs)
                }

            val allNotesLists = notesListEntities.map { l ->
                val mine = itemsByList[l.firestoreId] ?: emptyList()
                UiState.NotesList(list = l, items = mine)
            }
            val notesListsFiltered = if (isSearching) {
                allNotesLists.mapNotNull { entry ->
                    val titleMatches = match(entry.list.title)
                    val matchingItems = entry.items.filter { match(it.title) }
                    when {
                        titleMatches -> entry  // show all items
                        matchingItems.isNotEmpty() -> entry.copy(items = matchingItems)
                        else -> null
                    }
                }
            } else {
                allNotesLists
            }

            val rawAnswers = items
                .asSequence()
                .filter { !it.deleted }
                .filter { it.kind == "answer" }
                .filter { match(it.title) || match(it.body) }
                .sortedByDescending { it.createdAt.toEpochMilliseconds() }
                .toList()
            val answersPreview = if (isSearching) rawAnswers else rawAnswers.take(ANSWER_PREVIEW_LIMIT)

            val matches = if (isSearching) {
                recordingsView.size + rawTodos.size +
                    notesListsFiltered.sumOf { it.items.size } +
                    rawAnswers.size
            } else 0

            return UiState(
                recordings = recordingsView,
                todosPreview = todosPreview,
                totalTodos = rawTodos.size,
                notesLists = notesListsFiltered,
                totalNotesLists = if (isSearching) notesListsFiltered.size else allNotesLists.size,
                answersPreview = answersPreview,
                totalAnswers = rawAnswers.size,
                searching = isSearching,
                matches = matches,
            )
        }

        @Suppress("unused")
        private fun Instant?.orMax(): Long = this?.toEpochMilliseconds() ?: Long.MAX_VALUE

        /** Pretty-print the primary action chip for a recording's first
         *  extracted item. Mirrors the prototype's `objectChip`. */
        internal fun chipLabel(item: CachedItem, lists: List<CachedList>): String {
            if (item.locked) return "🔒 Encrypted"
            val fields = item.fields()
            fun strField(key: String): String? = (fields[key] as? JsonPrimitive)?.contentOrNull
            return when (item.kind) {
                "reminder" -> item.title.ifBlank { "Reminder" }
                "scheduled" -> when (strField("fireKind")) {
                    "alarm" -> strField("fireTime")?.let { "Alarm · $it" } ?: item.title.ifBlank { "Alarm" }
                    "timer" -> {
                        // Prefer a formatted "Timer · 20 min" over the raw
                        // ISO that older items have stored in the title.
                        val durRaw = strField("duration")
                        val pretty = formatDuration(durRaw)
                        if (!pretty.isNullOrBlank()) "Timer · $pretty"
                        else item.title.ifBlank { "Timer" }
                    }
                    else -> item.title.ifBlank { "Scheduled" }
                }
                "note" -> {
                    val parentId = item.parentListIds().firstOrNull()
                    val parent = parentId?.let { id -> lists.firstOrNull { it.firestoreId == id } }
                    val parentName = parent?.title?.takeIf { it.isNotBlank() } ?: "Notes to self"
                    "Added to $parentName"
                }
                "answer" -> "Answered"
                "calendar_event" -> item.title.ifBlank { "Event" }
                "message" -> {
                    val raw = strField("recipientName") ?: strField("contact")
                    val name = raw?.let { messageRecipientLabel(it) }
                    if (!name.isNullOrBlank()) "Sent to $name" else "Message sent"
                }
                "action_log" -> item.title.ifBlank { "Action" }
                "delegated" -> {
                    val integration = strField("integration")
                    if (!integration.isNullOrBlank()) "Sent to $integration" else "Sent"
                }
                else -> item.title.ifBlank { "Saved" }
            }
        }

        /** Pretty-print a Beeper / messaging recipient — strips homeserver
         *  suffixes and shortens long room ids the way the prototype does. */
        private fun messageRecipientLabel(raw: String): String {
            if (raw.startsWith("!")) {
                val local = raw.substringBefore(":").drop(1)
                return if (local.length > 12) local.take(12) + "…" else local
            }
            return raw
        }

        /** Turn an ISO-8601 duration (`PT20M`, `PT1H30M`, `PT45S`) or a raw
         *  millisecond value (legacy cached rows) into a human label like
         *  "20 min", "1 hr 30 min", "45 sec". Falls back to the raw string
         *  if parsing fails. Used everywhere we surface a timer's duration. */
        fun formatDuration(raw: String?): String? {
            if (raw.isNullOrBlank()) return null
            // Parse ISO-8601 ("PT3M") or fall back to raw milliseconds for older cached rows
            val d = try {
                kotlin.time.Duration.parseIsoString(raw)
            } catch (e: Throwable) {
                raw.toLongOrNull()?.milliseconds ?: return raw
            }
            val totalSec = d.inWholeSeconds
            val h = totalSec / 3600
            val m = (totalSec % 3600) / 60
            val s = totalSec % 60
            return buildString {
                if (h > 0) append("$h hr ")
                if (m > 0) append("$m min ")
                if (s > 0 && h == 0L) append("$s sec")
            }.trim().ifBlank { raw }
        }
    }
}
