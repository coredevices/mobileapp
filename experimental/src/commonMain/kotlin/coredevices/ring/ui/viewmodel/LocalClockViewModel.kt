package coredevices.ring.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coredevices.ring.clock.LocalClock
import coredevices.ring.data.entity.room.clock.LocalClockEntry
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** Backs [coredevices.ring.ui.screens.clock.TimersAndAlarms]: pending entries with live countdowns. */
class LocalClockViewModel(
    private val localClock: LocalClock,
    private val clock: Clock = Clock.System,
) : ViewModel() {

    private val ticker = flow {
        while (true) {
            emit(clock.now())
            delay(1.seconds)
        }
    }

    val state: StateFlow<UiState> = combine(localClock.entries, ticker) { entries, now ->
        if (entries.any { it.fireAt <= now }) localClock.pruneFired()
        compute(entries, now)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = UiState(),
    )

    fun cancel(id: String) {
        viewModelScope.launch { localClock.cancel(id) }
    }

    data class TimerRow(val entry: LocalClockEntry, val remaining: Duration)

    data class UiState(
        val timers: List<TimerRow> = emptyList(),
        val alarms: List<LocalClockEntry> = emptyList(),
    ) {
        val isEmpty get() = timers.isEmpty() && alarms.isEmpty()
    }

    companion object {
        internal fun compute(entries: List<LocalClockEntry>, now: Instant): UiState {
            val pending = entries.filter { it.fireAt > now }.sortedBy { it.fireAt }
            return UiState(
                timers = pending
                    .filter { it.kind == LocalClockEntry.Kind.Timer }
                    .map { TimerRow(it, it.fireAt - now) },
                alarms = pending.filter { it.kind == LocalClockEntry.Kind.Alarm },
            )
        }

        /** "1:02:05", "12:05", "0:07". */
        fun formatCountdown(remaining: Duration): String {
            val total = remaining.inWholeSeconds.coerceAtLeast(0)
            val h = total / 3600
            val m = (total % 3600) / 60
            val s = total % 60
            val ss = s.toString().padStart(2, '0')
            return if (h > 0) "$h:${m.toString().padStart(2, '0')}:$ss" else "$m:$ss"
        }
    }
}
