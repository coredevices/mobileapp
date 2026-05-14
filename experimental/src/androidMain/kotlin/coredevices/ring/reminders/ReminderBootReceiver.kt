package coredevices.ring.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import co.touchlab.kermit.Logger
import coredevices.ring.database.room.dao.LocalReminderDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.time.Clock
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class ReminderBootReceiver : BroadcastReceiver(), KoinComponent {
    companion object {
        private val logger = Logger.withTag(ReminderBootReceiver::class.simpleName!!)
    }

    private val scope = CoroutineScope(Dispatchers.Default)
    private val localReminderDao: LocalReminderDao by inject()

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_LOCKED_BOOT_COMPLETED &&
            intent.action != "android.intent.action.QUICKBOOT_POWERON"
        ) {
            return
        }
        logger.d { "Re-scheduling reminder alarms after boot" }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            logger.w { "Cannot re-schedule reminders: exact alarm permission not granted" }
            return
        }

        val pendingResult = goAsync()
        scope.launch {
            try {
                val now = Clock.System.now()
                val reminders = localReminderDao.getAllReminders()
                for (reminder in reminders) {
                    val time = reminder.time ?: continue
                    if (time <= now) continue
                    val alarmIntent = Intent(context, ReminderReceiver::class.java).apply {
                        putExtra(ReminderReceiver.EXTRA_REMINDER_ID, reminder.id)
                    }
                    val pendingIntent = PendingIntent.getBroadcast(
                        context,
                        reminder.id,
                        alarmIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    val info = AlarmManager.AlarmClockInfo(time.toEpochMilliseconds(), pendingIntent)
                    alarmManager.setAlarmClock(info, pendingIntent)
                }
                logger.d { "Re-scheduled ${reminders.size} reminder(s) after boot" }
            } catch (t: Throwable) {
                logger.e(t) { "Failed to re-schedule reminders after boot" }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
