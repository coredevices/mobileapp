package coredevices.ring.reminders

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import co.touchlab.kermit.Logger
import coredevices.ring.data.entity.room.reminders.LocalReminderData
import coredevices.ring.database.room.dao.LocalReminderDao
import coredevices.ring.database.room.repository.GeofenceLocationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class ReminderBootReceiver : BroadcastReceiver(), KoinComponent {
    companion object {
        private val logger = Logger.withTag(ReminderBootReceiver::class.simpleName!!)
    }

    private val scope = CoroutineScope(Dispatchers.Default)
    private val localReminderDao: LocalReminderDao by inject()
    private val scheduler: ReminderScheduler by inject()
    private val geofenceLocations: GeofenceLocationRepository by inject()

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_LOCKED_BOOT_COMPLETED &&
            intent.action != "android.intent.action.QUICKBOOT_POWERON"
        ) {
            return
        }
        logger.d { "Re-scheduling reminder alarms after boot" }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val canScheduleAlarms = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        if (!canScheduleAlarms) {
            logger.w { "Cannot re-schedule timed reminders: exact alarm permission not granted" }
        }

        val pendingResult = goAsync()
        scope.launch {
            try {
                val reminders = localReminderDao.getAllReminders()
                for (reminder in reminders) {
                    if (reminder.geofenceLocationId != null) {
                        rearmGeofence(reminder)
                    } else if (canScheduleAlarms) {
                        scheduler.scheduleReminder(
                            reminder.itemId,
                            reminder.message,
                            reminder.time,
                            reminder.notifyBeforeMillis?.milliseconds,
                        )
                    }
                }
                logger.d { "Re-scheduled ${reminders.size} reminder(s) after boot" }
            } catch (t: Throwable) {
                logger.e(t) { "Failed to re-schedule reminders after boot" }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun rearmGeofence(reminder: LocalReminderData) {
        val location = reminder.geofenceLocationId?.let { geofenceLocations.getSavedById(it) } ?: return
        try {
            scheduler.scheduleGeofenceReminder(
                reminder.itemId,
                reminder.message,
                location.name,
                location.lat,
                location.long,
                location.radiusMeters,
            )
        } catch (e: Exception) {
            logger.e(e) { "Failed to re-arm geofence reminder ${reminder.itemId}" }
        }
    }
}
