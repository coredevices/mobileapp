@file:OptIn(ExperimentalTime::class)

package coredevices.ring.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coredevices.ring.data.entity.room.indexfeed.CachedItem
import coredevices.ring.data.entity.room.indexfeed.kind
import coredevices.ring.database.room.repository.ItemRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlin.time.ExperimentalTime

/**
 * Backs [coredevices.ring.ui.screens.indexfeed.AllAnswers]. Exposes every
 * non-deleted `kind=answer` item, newest first, optionally filtered.
 */
class AllAnswersViewModel(
    itemRepo: ItemRepository,
) : ViewModel() {

    val query = MutableStateFlow("")

    val state: StateFlow<UiState> = combine(
        itemRepo.getAllFlow(),
        query,
    ) { items, q -> compute(items, q.trim()) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = UiState(emptyList()),
        )

    fun setQuery(q: String) { query.value = q }

    data class UiState(val answers: List<CachedItem>)

    companion object {
        internal fun compute(items: List<CachedItem>, query: String): UiState {
            val q = query.lowercase()
            fun match(s: String?) = q.isEmpty() || (s ?: "").lowercase().contains(q)
            val out = items
                .asSequence()
                .filter { !it.deleted && it.kind == "answer" }
                .filter { match(it.title) || match(it.body) }
                .sortedByDescending { it.createdAt.toEpochMilliseconds() }
                .toList()
            return UiState(out)
        }
    }
}
