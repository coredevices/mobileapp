@file:OptIn(ExperimentalTime::class)

package coredevices.ring.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coredevices.indexai.data.entity.ListDocument
import coredevices.ring.data.entity.room.indexfeed.CachedItem
import coredevices.ring.data.entity.room.indexfeed.CachedList
import coredevices.ring.data.entity.room.indexfeed.kind
import coredevices.ring.database.room.repository.ItemRepository
import coredevices.ring.database.room.repository.ListRepository
import coredevices.ring.service.indexfeed.DefaultListsBootstrap.Companion.LIST_TODOS_ID
import coredevices.ring.service.indexfeed.DefaultListsBootstrap.Companion.SEED_TODOS
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Backs [coredevices.ring.ui.screens.indexfeed.AllLists]. Exposes every
 * non-deleted list (excluding the system Todos) sorted by `updatedAt`
 * descending, alongside their child note items, optionally filtered by
 * a search query.
 */
class AllListsViewModel(
    private val listRepo: ListRepository,
    itemRepo: ItemRepository,
) : ViewModel() {

    val query = MutableStateFlow("")

    val state: StateFlow<UiState> = combine(
        listRepo.getAllFlow(),
        itemRepo.getAllFlow(),
        query,
    ) { lists, items, q ->
        compute(lists, items, q.trim())
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = UiState(emptyList()),
    )

    fun setQuery(q: String) { query.value = q }

    /** Creates a fresh user list and invokes [onCreated] with the new
     *  doc id so the screen can navigate into rename mode. The id is
     *  generated locally from the timestamp; the next "Sync now" will
     *  push it to Firestore alongside everything else. */
    fun newList(onCreated: (String) -> Unit) {
        viewModelScope.launch {
            val now = Clock.System.now()
            val newId = "list_${now.toEpochMilliseconds()}"
            listRepo.setList(
                newId,
                ListDocument(
                    createdAt = now,
                    updatedAt = now,
                    title = "New list",
                    icon = "📝",
                    listKind = "note",
                ),
            )
            onCreated(newId)
        }
    }

    data class UiState(val lists: List<Entry>) {
        data class Entry(val list: CachedList, val items: List<CachedItem>)
    }

    companion object {
        internal fun compute(
            lists: List<CachedList>,
            items: List<CachedItem>,
            query: String,
        ): UiState {
            val q = query.lowercase()
            fun match(s: String?) = q.isEmpty() || (s ?: "").lowercase().contains(q)

            val notesByList = items
                .asSequence()
                .filter { !it.deleted && it.kind == "note" }
                .sortedByDescending { it.createdAt }
                .toList()
                .groupBy { it.parentListIds().firstOrNull().orEmpty() }

            // Sort by *effective* updatedAt: max(list.updatedAt, newest
            // child.updatedAt). list.updatedAt only changes on rename, so
            // without rolling up child timestamps a list the user actively
            // adds to would never float to the top — same fix as the home
            // Notes grid's compute().
            val visible = lists
                .asSequence()
                .filter { !it.deleted }
                .filter { it.seed != SEED_TODOS && it.firestoreId != LIST_TODOS_ID }
                .sortedByDescending { l ->
                    val ownTs = l.updatedAt.toEpochMilliseconds()
                    val childTs = (notesByList[l.firestoreId] ?: emptyList())
                        .maxOfOrNull { it.updatedAt.toEpochMilliseconds() } ?: 0L
                    maxOf(ownTs, childTs)
                }
                .map { l -> UiState.Entry(l, notesByList[l.firestoreId].orEmpty()) }
                .let { all ->
                    if (q.isEmpty()) all.toList()
                    else all
                        .filter { e -> match(e.list.title) || e.items.any { match(it.title) } }
                        .toList()
                }

            return UiState(visible)
        }
    }
}
