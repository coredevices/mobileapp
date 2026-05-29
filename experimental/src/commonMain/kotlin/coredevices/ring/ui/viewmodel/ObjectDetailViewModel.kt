@file:OptIn(ExperimentalTime::class)

package coredevices.ring.ui.viewmodel

import androidx.compose.material3.SnackbarHostState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coredevices.indexai.data.entity.ItemDocument
import coredevices.indexai.data.entity.LocalRecording
import coredevices.ring.data.entity.room.indexfeed.CachedItem
import coredevices.ring.data.entity.room.indexfeed.CachedList
import coredevices.ring.data.entity.room.indexfeed.kind
import coredevices.ring.data.entity.room.indexfeed.metadataForKind
import coredevices.ring.database.room.repository.ItemRepository
import coredevices.ring.database.room.repository.ListRepository
import coredevices.ring.database.room.repository.RecordingRepository
import coredevices.ring.service.indexfeed.DefaultListsBootstrap.Companion.LIST_NOTES_SELF_ID
import coredevices.ring.service.indexfeed.DefaultListsBootstrap.Companion.LIST_TODOS_ID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Backs [coredevices.ring.ui.screens.indexfeed.ObjectDetail]. Handles either an
 * item (note/reminder/scheduled/message/answer/action_log) or a list — the
 * caller passes a Firestore doc id and we resolve it.
 *
 * The screen subscribes to a single [state] flow that flips between
 * `Loading`, `Item`, `List`, and `NotFound`. Toggling done / delete are
 * suspend operations that round-trip through the repository (Room first,
 * Firestore best-effort).
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ObjectDetailViewModel(
    private val objectId: String,
    private val itemRepo: ItemRepository,
    private val listRepo: ListRepository,
    private val recordingRepo: RecordingRepository,
    private val snackbarHostState: SnackbarHostState,
) : ViewModel() {

    /** Local search/sort/done-toggle state for list views. */
    val listSearch = MutableStateFlow("")
    /** Three-state sort cycle: `Newest` (createdAt desc) → `Oldest`
     *  (createdAt asc) → `DueDate` (most-overdue / soonest-due first;
     *  items without dueAt sink to the bottom). The Todos list defaults
     *  to DueDate so the most urgent items show up at the top; every
     *  other list defaults to Newest. */
    val listSort = MutableStateFlow(
        if (objectId == LIST_TODOS_ID) ListSort.DueDate else ListSort.Newest
    )
    val showDone = MutableStateFlow(false)

    enum class ListSort { Newest, Oldest, DueDate }

    /** Child item ids that were just toggled to done and should linger
     *  with strikethrough + faded opacity in the *active* bucket of the
     *  checklist for [STRIKE_THROUGH_MS] before moving to the done
     *  bucket. Mirrors the prototype `details.jsx` `animatingDoneIds`. */
    val animatingDoneIds = MutableStateFlow<Set<String>>(emptySet())
    /** Per-id removal jobs so a rapid done → undone → done sequence
     *  cancels the prior delay rather than letting it remove the id
     *  out from under the new animation. */
    private val animatingDoneJobs = mutableMapOf<String, kotlinx.coroutines.Job>()

    /** All non-deleted lists for the multi-list membership picker in
     *  ObjectDetail item edit mode. Excludes nothing — including the
     *  system Todos list — so a reminder can move out of Todos if the
     *  user really wants. */
    val allLists: StateFlow<List<CachedList>> = listRepo.getAllFlow()
        .map { lists -> lists.filter { !it.deleted }.sortedByDescending { it.updatedAt.toEpochMilliseconds() } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val state: StateFlow<UiState> = combine(
        itemRepo.getByIdFlow(objectId),
        listRepo.getByIdFlow(objectId),
    ) { item, list -> item to list }
        .flatMapLatest { (item, list) ->
            when {
                list != null -> listRepo.getAllFlow().let { _ ->
                    // List body needs the children too. Combine with list's items.
                    combine(
                        flowOf(list),
                        itemRepo.getByListFlow(objectId),
                        listSearch,
                        listSort,
                        showDone,
                    ) { l, children, q, sort, done ->
                        UiState.ListView(
                            list = l,
                            children = children
                                .filter { !it.deleted }
                                .let { all ->
                                    val filtered = if (q.isBlank()) all
                                    else all.filter { it.title.contains(q, ignoreCase = true) }
                                    when (sort) {
                                        ListSort.Newest -> filtered.sortedByDescending { it.createdAt }
                                        ListSort.Oldest -> filtered.sortedBy { it.createdAt }
                                        // Due-date sort mirrors the home
                                        // Todos preview: overdue / due
                                        // within 24h, then undated, then
                                        // later scheduled todos.
                                        ListSort.DueDate -> filtered.sortedWith(
                                            todoComparator(),
                                        )
                                    }
                                },
                            query = q,
                            sort = sort,
                            showDone = done,
                        )
                    }
                }
                item != null -> {
                    val recId = item.sourceRecordingId
                    val recFlow = when {
                        recId.isNullOrBlank() -> flowOf<LocalRecording?>(null)
                        recId.startsWith("local:") -> {
                            // Auto-ingest writes "local:<roomId>" when the
                            // recording hasn't synced to Firestore yet —
                            // resolve those via Room's primary key directly.
                            val localId = recId.removePrefix("local:").toLongOrNull()
                            if (localId == null) flowOf<LocalRecording?>(null)
                            else recordingRepo.getRecordingFlow(localId)
                        }
                        else -> recordingRepo.getAllRecordings().map { all ->
                            all.firstOrNull { it.firestoreId == recId }
                        }
                    }
                    val parentId = item.parentListIds().firstOrNull()
                    val parentFlow = if (parentId.isNullOrBlank()) flowOf<CachedList?>(null)
                        else listRepo.getByIdFlow(parentId)
                    recFlow.flatMapLatest { rec ->
                        val transcriptFlow = if (rec == null) flowOf("")
                            else recordingRepo.getRecordingEntriesFlow(rec.id).map { entries ->
                                entries.firstOrNull()?.transcription.orEmpty()
                            }
                        combine(flowOf(item), flowOf(rec), parentFlow, transcriptFlow) { it, r, parent, transcript ->
                            UiState.ItemView(
                                item = it,
                                sourceRecording = r,
                                sourceTranscription = transcript,
                                parentList = parent,
                            )
                        }
                    }
                }
                else -> flowOf(UiState.NotFound)
            }
        }.stateIn(
            // Eagerly so the upstream flow stays alive for the lifetime of
            // this per-screen viewmodel — prevents the Loading-flicker users
            // saw when the lifecycle paused (e.g. dropdown menu auto-dismiss,
            // backgrounded keyboard) and the WhileSubscribed timeout expired.
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = UiState.Loading,
        )

    fun toggleDone() {
        val s = state.value as? UiState.ItemView ?: return
        val it = s.item
        viewModelScope.launch {
            val updated = it.toDocument().copy(
                done = !it.done,
                updatedAt = Clock.System.now(),
            )
            itemRepo.setItem(it.firestoreId, updated)
        }
    }

    fun toggleChildDone(child: CachedItem) {
        val wasDone = child.done
        val id = child.firestoreId
        // Mark animating BEFORE the DB write — otherwise the flow re-emits
        // with `done=true` and the row briefly drops from the active
        // bucket before the linger logic catches it, causing a flicker.
        if (!wasDone) {
            animatingDoneJobs.remove(id)?.cancel()
            animatingDoneIds.value = animatingDoneIds.value + id
        } else {
            animatingDoneJobs.remove(id)?.cancel()
            animatingDoneIds.value = animatingDoneIds.value - id
        }
        viewModelScope.launch {
            val updated = child.toDocument().copy(
                done = !child.done,
                updatedAt = Clock.System.now(),
            )
            itemRepo.setItem(id, updated)
            if (!wasDone) {
                animatingDoneJobs[id] = viewModelScope.launch {
                    kotlinx.coroutines.delay(STRIKE_THROUGH_MS)
                    animatingDoneIds.value = animatingDoneIds.value - id
                    animatingDoneJobs.remove(id)
                }
            }
        }
    }

    companion object {
        const val STRIKE_THROUGH_MS = 600L
    }

    fun setListQuery(q: String) { listSearch.value = q }
    fun setListSort(sort: ListSort) { listSort.value = sort }
    /** Cycle Newest → Oldest → DueDate → Newest. */
    fun toggleSort() {
        listSort.value = when (listSort.value) {
            ListSort.Newest -> ListSort.Oldest
            ListSort.Oldest -> ListSort.DueDate
            ListSort.DueDate -> ListSort.Newest
        }
    }
    fun setShowDone(show: Boolean) { showDone.value = show }

    fun deleteThis(onAfter: () -> Unit) {
        viewModelScope.launch {
            when (val s = state.value) {
                is UiState.ItemView -> {
                    itemRepo.softDelete(s.item.firestoreId)
                    snackbarHostState.showSnackbar(
                        "Deleted ${kindLabel(s.item.kind).lowercase()}"
                    )
                }
                is UiState.ListView -> {
                    listRepo.softDelete(s.list.firestoreId)
                    snackbarHostState.showSnackbar("Deleted list")
                }
                else -> return@launch
            }
            onAfter()
        }
    }

    fun renameList(newTitle: String, newIcon: String? = null) {
        val s = state.value as? UiState.ListView ?: return
        val title = newTitle.trim().ifBlank { return }
        viewModelScope.launch {
            val updated = s.list.toDocument().copy(
                title = title,
                icon = newIcon?.trim() ?: s.list.icon,
                updatedAt = Clock.System.now(),
            )
            listRepo.setList(s.list.firestoreId, updated)
        }
    }

    /** Patch an item — used by the per-kind edit mode in ObjectDetail.
     *  `dueAt = NoChange` keeps the existing dueAt; pass any [Instant?] (or
     *  `null`) to overwrite, including clearing.
     *  `parentListIds = null` keeps existing membership; pass a list to
     *  overwrite the item's parent-list set. */
    fun patchItem(
        title: String?,
        body: String?,
        kind: String? = null,
        createdAt: Instant? = null,
        dueAt: DueAtChange = DueAtChange.NoChange,
        parentListIds: List<String>? = null,
    ) {
        val s = state.value as? UiState.ItemView ?: return
        val it = s.item
        viewModelScope.launch {
            val nextKind = kind ?: it.kind
            val nextDueAt = when (dueAt) {
                DueAtChange.NoChange -> it.dueAt
                is DueAtChange.Set -> dueAt.value
            }
            val nextParents = normalizeParentLists(
                kind = nextKind,
                requestedParents = parentListIds,
                currentParents = it.parentListIds(),
            )
            val updated = it.toDocument().copy(
                createdAt = createdAt ?: it.createdAt,
                title = (title ?: it.title).trim().ifBlank { it.title },
                body = body ?: it.body,
                metadata = metadataForKind(nextKind, existing = it.metadata),
                dueAt = nextDueAt,
                parentListIds = nextParents,
                updatedAt = Clock.System.now(),
            )
            itemRepo.setItem(it.firestoreId, updated)
        }
    }

    fun patchChildItem(childId: String, title: String?, body: String? = null, kind: String? = null) {
        viewModelScope.launch {
            val existing = itemRepo.getById(childId) ?: return@launch
            val trimmed = title?.trim()
            if (trimmed != null && trimmed.isBlank()) return@launch
            val nextKind = kind ?: existing.kind
            val updated = existing.toDocument().copy(
                title = trimmed ?: existing.title,
                body = body ?: existing.body,
                metadata = metadataForKind(nextKind, existing = existing.metadata),
                parentListIds = normalizeParentLists(
                    kind = nextKind,
                    requestedParents = null,
                    currentParents = existing.parentListIds(),
                ),
                updatedAt = Clock.System.now(),
            )
            itemRepo.setItem(childId, updated)
        }
    }

    fun createChildItem(
        title: String,
        kind: String = "note",
        onCreated: (String) -> Unit = {},
    ) {
        val s = state.value as? UiState.ListView ?: return
        val cleanTitle = title.trim().ifBlank { return }
        viewModelScope.launch {
            val now = Clock.System.now()
            val id = "local-item-${Uuid.random()}"
            itemRepo.setItem(
                id,
                ItemDocument(
                    createdAt = now,
                    updatedAt = now,
                    metadata = metadataForKind(kind),
                    title = cleanTitle,
                    parentListIds = normalizeParentLists(
                        kind = kind,
                        requestedParents = listOf(s.list.firestoreId),
                        currentParents = emptyList(),
                    ),
                ),
            )
            onCreated(id)
        }
    }

    fun deleteChildItem(childId: String) {
        viewModelScope.launch {
            itemRepo.softDelete(childId)
        }
    }

    sealed class DueAtChange {
        data object NoChange : DueAtChange()
        data class Set(val value: kotlin.time.Instant?) : DueAtChange()
    }

    sealed class UiState {
        data object Loading : UiState()
        data object NotFound : UiState()
        data class ItemView(
            val item: CachedItem,
            val sourceRecording: LocalRecording?,
            val sourceTranscription: String,
            val parentList: CachedList?,
        ) : UiState()
        data class ListView(
            val list: CachedList,
            val children: List<CachedItem>,
            val query: String,
            val sort: ListSort,
            val showDone: Boolean,
        ) : UiState()
    }
}

internal fun kindLabel(kind: String): String = when (kind) {
    "reminder" -> "Reminder"
    "scheduled" -> "Scheduled"
    "checklist" -> "Checklist"
    "note" -> "Note"
    "answer" -> "Answer"
    "message" -> "Message"
    "action_log" -> "Action"
    "list" -> "List"
    else -> kind.replaceFirstChar { it.uppercase() }
}

private fun normalizeParentLists(
    kind: String,
    requestedParents: List<String>?,
    currentParents: List<String>,
): List<String> {
    if (kind == "reminder" || kind == "scheduled") return listOf(LIST_TODOS_ID)

    val source = requestedParents ?: currentParents
    val nonTodoParents = source
        .filter { it != LIST_TODOS_ID }
        .distinct()
    return nonTodoParents.ifEmpty { listOf(LIST_NOTES_SELF_ID) }
}

private fun todoComparator(): Comparator<CachedItem> {
    val nowMs = Clock.System.now().toEpochMilliseconds()
    val urgentCutoffMs = nowMs + 24L * 60L * 60L * 1000L
    return compareBy<CachedItem> { task ->
        val dueMs = task.dueAt?.toEpochMilliseconds()
        when {
            dueMs != null && dueMs <= urgentCutoffMs -> 0
            dueMs == null -> 1
            else -> 2
        }
    }
        .thenBy { task ->
            val dueMs = task.dueAt?.toEpochMilliseconds()
            if (dueMs != null && dueMs <= urgentCutoffMs) dueMs else Long.MAX_VALUE
        }
        .thenByDescending { it.createdAt.toEpochMilliseconds() }
        .thenBy { it.title.lowercase() }
}
