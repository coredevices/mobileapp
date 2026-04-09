package coredevices.ring.ui.viewmodel

import CoreNav
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coredevices.ring.data.entity.room.reminders.LocalReminderData
import coredevices.ring.database.room.dao.LocalReminderDao
import coredevices.ring.ui.UITimeUtil
import coredevices.ring.ui.navigation.RingRoutes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.days

data class RemindersAlarmsEntry(
    val id: Int,
    val title: String,
    val type: Type,
    val triggerDetail: String
) {
    enum class Type {
        Reminder,
        LocationReminder,
        Alarm
    }
}

enum class RemindersAlarmsFilter {
    Upcoming
}

class NotesViewModel(private val reminderDao: LocalReminderDao, private val coreNav: CoreNav): ViewModel() {
    sealed class RemindersAlarmsState {
        data object Loading: RemindersAlarmsState()
        data object Error: RemindersAlarmsState()
        data class Loaded(val items: List<RemindersAlarmsEntry>): RemindersAlarmsState()
    }

    private val _remindersAlarmsState = MutableStateFlow<RemindersAlarmsState>(RemindersAlarmsState.Loading)
    val remindersAlarmsState = _remindersAlarmsState.asStateFlow()
    private val _remindersAlarmsFilter = MutableStateFlow(RemindersAlarmsFilter.Upcoming)
    val remindersAlarmsFilter = _remindersAlarmsFilter.asStateFlow()

    init {
        refresh()
        reminderDao.getAllRemindersFlow().onEach {
            refresh(true)
        }.launchIn(viewModelScope)
    }

    companion object {
        const val UPCOMING_FILTER_MAX_DAYS = 7
    }

    private suspend fun getFilteredReminders(): List<LocalReminderData> {
        return when (remindersAlarmsFilter.value) {
            RemindersAlarmsFilter.Upcoming -> {
                val timeZone = TimeZone.currentSystemDefault()
                val now = Clock.System.now()
                val end = (Clock.System.now() + UPCOMING_FILTER_MAX_DAYS.days).toLocalDateTime(timeZone).let {
                    LocalDateTime(it.date, LocalTime(23, 59, 59)) // End of the day
                }.toInstant(timeZone)
                reminderDao.getAllRemindersInRange(now, end)
            }
        }
    }

    private suspend fun loadRemindersAlarms(silent: Boolean = false) {
        if (!silent) {
            _remindersAlarmsState.value = RemindersAlarmsState.Loading
        }
        try {
            val timeZone = TimeZone.currentSystemDefault()
            val reminders = getFilteredReminders().sortedBy { it.time }
            val today = Clock.System.now().toLocalDateTime(timeZone)
            val shortDateFormat = UITimeUtil.shortDateFormat()
            val timeFormat = UITimeUtil.timeFormat()
            val items = reminders.map {
                val dateTime = it.time?.toLocalDateTime(timeZone)
                RemindersAlarmsEntry(
                    id = it.id,
                    title = it.message,
                    type = RemindersAlarmsEntry.Type.Reminder,
                    triggerDetail = dateTime?.let {
                        buildString {
                            if (today.date != dateTime.date) {
                                append(dateTime.date.format(shortDateFormat))
                                append(" ")
                            }
                            append(dateTime.time.format(timeFormat))
                        }
                    } ?: "No time"
                )
            }
            _remindersAlarmsState.value = RemindersAlarmsState.Loaded(items)
        } catch (e: Exception) {
            _remindersAlarmsState.value = RemindersAlarmsState.Error
        }
    }

    fun refresh(silent: Boolean = false) {
        viewModelScope.launch {
            loadRemindersAlarms()
        }
    }

    fun openDetails(id: Int) {
        coreNav.navigateTo(RingRoutes.ReminderDetails(id))
    }
}