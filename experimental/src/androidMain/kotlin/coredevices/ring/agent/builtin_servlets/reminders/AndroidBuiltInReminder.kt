package coredevices.ring.agent.builtin_servlets.reminders

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import co.touchlab.kermit.Logger
import coredevices.ring.data.entity.room.reminders.LocalReminderData
import coredevices.ring.database.room.RingDatabase
import coredevices.ring.reminders.ReminderReceiver
import coredevices.util.AndroidPlatform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlin.time.Clock
import kotlin.time.Instant
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class AndroidBuiltInReminder(
    override val time: Instant?,
    override val message: String
): ListAssignableReminder, KoinComponent {
    private val context: Context by inject()
    private val db: RingDatabase by inject()
    private val scope = CoroutineScope(Dispatchers.Default)

    private var _reminderId: Int? = null
    val reminderId: Int? get() = _reminderId
    override val listTitle: String? = null

    private constructor(time: Instant?, message: String, workId: Int): this(time, message) {
        _reminderId = workId
    }

    override suspend fun schedule(): String {
        require(time == null || time > Clock.System.now()) { "Time must be in the future" }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                val result = scope.async { AndroidPlatform.notificationPermResults.first() }
                AndroidPlatform.triggerNotificationPermRequest()
                check(result.await()) { "Notification permission not granted" }
            }
        }
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            check(alarmManager.canScheduleExactAlarms()) { "No permissions to schedule reminders, tell user to check under 'Special app access > Alarms and reminders' in Settings app." }
        }

        val reminder = LocalReminderData(0, time, message)

        val id = db.localReminderDao().insertReminder(reminder).toInt()
        _reminderId = id

        time?.let {
            val intent = Intent(context, ReminderReceiver::class.java).apply {
                putExtra(ReminderReceiver.EXTRA_REMINDER_ID, id)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                id,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val info = AlarmManager.AlarmClockInfo(time.toEpochMilliseconds(), pendingIntent)

            alarmManager.setAlarmClock(info, pendingIntent)
            alarmManager.nextAlarmClock?.let {
                Logger.d { "Next alarm: ${it.triggerTime}" }
            } ?: Logger.d { "No next alarm" }
        }
        return id.toString()
    }

    override suspend fun cancel() {
        val reminderId = _reminderId ?: return

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(ReminderReceiver.EXTRA_REMINDER_ID, reminderId)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminderId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        pendingIntent?.let(alarmManager::cancel)

        db.localReminderDao().deleteReminder(reminderId)
        _reminderId = null
    }

    override suspend fun scheduleToList(listName: String): String {
        return schedule()
    }

    companion object {
        fun fromData(data: LocalReminderData): AndroidBuiltInReminder {
            return AndroidBuiltInReminder(data.time, data.message, data.id)
        }
    }
}
