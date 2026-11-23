package coredevices.ring.clock

import coreapp.ring.generated.resources.Res
import coreapp.ring.generated.resources.local_clock_alarm
import coreapp.ring.generated.resources.timers_alarms_duration_timer
import coreapp.ring.generated.resources.timers_alarms_timer
import coredevices.ring.data.entity.room.clock.LocalClockEntry
import coredevices.ring.ui.navigation.RingRoutes
import coredevices.ring.ui.viewmodel.formatDuration
import coredevices.util.clock.NativeAlarmKitBridge
import kotlinx.datetime.toNSDate
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.time.Duration.Companion.milliseconds
import org.jetbrains.compose.resources.getString

/** Fires timers and alarms through AlarmKit (iOS 26+) */
class IosLocalClockNotifier : LocalClockNotifier {
    override suspend fun schedule(entry: LocalClockEntry) {
        val schedule = checkNotNull(NativeAlarmKitBridge.schedule) { "AlarmKit bridge not registered" }
        val title = alarmTitle(entry)
        val error = suspendCoroutine { continuation ->
            schedule(entry.id, title, entry.fireAt.toNSDate(), RingRoutes.TIMERS_ALARMS_DEEP_LINK) { error ->
                continuation.resume(error)
            }
        }
        check(error == null) { "Failed to schedule ${entry.kind}: $error" }
    }

    override suspend fun cancel(id: String) {
        NativeAlarmKitBridge.cancel?.invoke(id)
    }

    /** The AlarmKit alert shows a single title; the system adds the time. */
    private suspend fun alarmTitle(entry: LocalClockEntry): String {
        entry.label?.let { return it }
        return when (entry.kind) {
            LocalClockEntry.Kind.Timer ->
                entry.durationMillis?.let { getString(Res.string.timers_alarms_duration_timer, formatDuration(it.milliseconds)) }
                    ?: getString(Res.string.timers_alarms_timer)
            LocalClockEntry.Kind.Alarm -> getString(Res.string.local_clock_alarm)
        }
    }
}
