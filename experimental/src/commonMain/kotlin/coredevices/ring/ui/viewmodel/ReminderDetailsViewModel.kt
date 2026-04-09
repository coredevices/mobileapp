package coredevices.ring.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import coredevices.ring.data.entity.room.reminders.LocalReminderData
import coredevices.ring.database.room.dao.LocalReminderDao
import coredevices.ring.agent.builtin_servlets.reminders.nativeReminderFromData
import coredevices.ring.ui.UITimeUtil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.toLocalDateTime

data class ReminderViewData(
    val title: String,
    val time: String?,
    val date: String?,
    val repeat: ReminderRepeat,
    val notification: Set<ReminderNotification>,
    val notes: String?
)

enum class ReminderRepeat {
    Once
}

enum class ReminderNotification {
    App
}

class ReminderDetailsViewModel(private val localReminderDao: LocalReminderDao, private val onBack: () -> Unit, private val itemId: Int): ViewModel() {
    companion object {
        private val logger = Logger.withTag(ReminderDetailsViewModel::class.simpleName!!)
    }
    sealed class ItemState {
        data object Loading: ItemState()
        data object Error: ItemState()
        data class Loaded(val item: ReminderViewData, internal val dbItem: LocalReminderData): ItemState()
    }

    private val _itemState = MutableStateFlow<ItemState>(ItemState.Loading)
    val itemState = _itemState.asStateFlow()

    init {
        viewModelScope.launch {
            loadItem(itemId)
        }
    }

    private suspend fun loadItem(id: Int) {
        _itemState.value = ItemState.Loading
        localReminderDao.getReminder(id)?.let {
            val timezone = TimeZone.currentSystemDefault()
            val localTime = it.time?.toLocalDateTime(timezone)
            _itemState.value = ItemState.Loaded(
                ReminderViewData(
                    title = it.message,
                    time = localTime?.time?.format(UITimeUtil.timeFormat()),
                    date = localTime?.let { UITimeUtil.humanDate(localTime.date) },
                    repeat = ReminderRepeat.Once,
                    notification = setOf(ReminderNotification.App),
                    notes = null
                ),
                it
            )
        } ?: run {
            logger.e { "Reminder $id not found" }
            _itemState.value = ItemState.Error
        }
    }

    fun deleteReminder() {
        viewModelScope.launch {
            if (_itemState.value is ItemState.Loaded) {
                nativeReminderFromData((_itemState.value as ItemState.Loaded).dbItem).let {
                    it.cancel()
                    onBack()
                }
            }
        }
    }
}