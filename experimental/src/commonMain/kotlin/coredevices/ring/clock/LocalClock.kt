package coredevices.ring.clock

import coredevices.ring.data.entity.room.clock.LocalClockEntry
import coredevices.ring.database.room.dao.LocalClockDao
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * In-app timers and alarms, used where there is no system clock app to hand them to (iOS).
 * Each entry is armed through [LocalClockNotifier] and recorded in [LocalClockDao] so it can be
 * listed and cancelled from the app.
 */
class LocalClock(
    private val dao: LocalClockDao,
    private val notifier: LocalClockNotifier,
    private val clock: Clock = Clock.System,
) {
    /** Every entry on this device, soonest first. Fired entries linger until [pruneFired]. */
    val entries: Flow<List<LocalClockEntry>> get() = dao.getAllFlow()

    suspend fun setTimer(duration: Duration, label: String? = null): LocalClockEntry {
        require(duration.isPositive()) { "Timer duration must be positive" }
        val now = clock.now()
        return arm(
            LocalClockEntry(
                id = Uuid.random().toString(),
                kind = LocalClockEntry.Kind.Timer,
                fireAt = now + duration,
                createdAt = now,
                label = label?.trim()?.takeIf { it.isNotEmpty() },
                durationMillis = duration.inWholeMilliseconds,
            )
        )
    }

    suspend fun setAlarm(hour: Int, minute: Int, label: String? = null): LocalClockEntry {
        require(hour in 0..23 && minute in 0..59) { "Invalid alarm time $hour:$minute" }
        val now = clock.now()
        return arm(
            LocalClockEntry(
                id = Uuid.random().toString(),
                kind = LocalClockEntry.Kind.Alarm,
                fireAt = nextOccurrence(LocalTime(hour, minute), now, TimeZone.currentSystemDefault()),
                createdAt = now,
                label = label?.trim()?.takeIf { it.isNotEmpty() },
            )
        )
    }

    // Arm before recording the row so a scheduling failure doesn't leave an entry that never fires.
    private suspend fun arm(entry: LocalClockEntry): LocalClockEntry {
        notifier.schedule(entry)
        dao.upsert(entry)
        return entry
    }

    suspend fun cancel(id: String) {
        notifier.cancel(id)
        dao.delete(id)
    }

    suspend fun pruneFired() {
        dao.deleteFiredBefore(clock.now())
    }

    companion object {
        /** The next time [time] comes around: today if it hasn't passed yet, otherwise tomorrow. */
        fun nextOccurrence(time: LocalTime, now: Instant, tz: TimeZone): Instant {
            val today = now.toLocalDateTime(tz).date
            val todayAt = today.atTime(time).toInstant(tz)
            if (todayAt > now) return todayAt
            return today.plus(1, DateTimeUnit.DAY).atTime(time).toInstant(tz)
        }
    }
}
