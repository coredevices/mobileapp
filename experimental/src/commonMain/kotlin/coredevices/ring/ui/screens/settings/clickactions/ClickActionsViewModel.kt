package coredevices.ring.ui.screens.settings.clickactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coredevices.ring.data.entity.room.ClickActionBinding
import coredevices.ring.database.Preferences
import coredevices.ring.database.reservedClickCounts
import coredevices.ring.database.room.repository.ClickActionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class ClickCountAvailability {
    Available,

    /** Media control dispatches first, so a binding here would never fire. */
    UsedByMediaControl,
    UsedByAnotherAction,
}

data class ClickCountOption(val count: Int, val availability: ClickCountAvailability) {
    val selectable: Boolean get() = availability == ClickCountAvailability.Available
}

/**
 * Every click count in range, each labelled with why it can or can't be claimed. Unavailable
 * counts are still returned so the UI can show the reason rather than silently omitting them.
 */
fun clickCountOptions(
    existing: List<ClickActionBinding>,
    reserved: Set<Int>,
    editingId: Long,
): List<ClickCountOption> = ClickActionBinding.CLICK_COUNT_RANGE.map { count ->
    val availability = when {
        count in reserved -> ClickCountAvailability.UsedByMediaControl
        existing.any { it.clickCount == count && it.id != editingId } ->
            ClickCountAvailability.UsedByAnotherAction
        else -> ClickCountAvailability.Available
    }
    ClickCountOption(count, availability)
}

fun List<ClickCountOption>.firstSelectable(): Int? =
    firstOrNull { it.selectable }?.count

class ClickActionsViewModel(
    private val repository: ClickActionRepository,
    preferences: Preferences,
) : ViewModel() {

    val bindings = repository.bindingsFlow().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    val reservedClickCounts = preferences.musicControlMode.map { it.reservedClickCounts() }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptySet(),
    )

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    fun save(binding: ClickActionBinding, onSaved: () -> Unit) {
        viewModelScope.launch {
            when (val result = repository.save(binding)) {
                is ClickActionRepository.SaveResult.Saved -> onSaved()
                ClickActionRepository.SaveResult.ClickCountTaken ->
                    _error.value = "${binding.clickCount} clicks is already bound to another action."
                ClickActionRepository.SaveResult.ClickCountOutOfRange ->
                    _error.value = "Pick between ${ClickActionBinding.MIN_CLICK_COUNT} and " +
                        "${ClickActionBinding.MAX_CLICK_COUNT} clicks."
                is ClickActionRepository.SaveResult.Failed ->
                    _error.value = result.message
            }
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch { repository.delete(id) }
    }

    fun clearError() {
        _error.value = null
    }
}
