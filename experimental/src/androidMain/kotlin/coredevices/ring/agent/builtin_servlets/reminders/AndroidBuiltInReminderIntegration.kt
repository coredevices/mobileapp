package coredevices.ring.agent.builtin_servlets.reminders

import PlatformUiContext
import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import co.touchlab.kermit.Logger
import coredevices.ring.agent.integrations.ItemSource
import coredevices.ring.agent.integrations.ReminderListEntry
import coredevices.ring.data.entity.room.reminders.LocalReminderData
import coredevices.ring.database.room.RingDatabase
import coredevices.ring.reminders.ReminderReceiver
import coredevices.util.AndroidPlatform
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class AndroidBuiltInReminderIntegration : BuiltInReminderIntegration, KoinComponent {
    private val context: Context by inject()
    private val db: RingDatabase by inject()
    private val feedItems: BuiltInReminderFeedItems by inject()

    override suspend fun createReminder(
        title: String,
        deadline: Instant?,
        listId: String?,
        notifyBefore: Duration?,
        source: ItemSource?,
    ): String {
        require(deadline == null || deadline > Clock.System.now()) { "Time must be in the future" }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            check(context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                "Notification permission not granted, enable notifications for the app in Settings."
            }
        }
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            check(alarmManager.canScheduleExactAlarms()) { "No permissions to schedule reminders, check under 'Special app access > Alarms and reminders' in Settings app." }
        }

        val id = db.localReminderDao().insertReminder(
            LocalReminderData(
                0,
                deadline,
                title,
                recordingId = source?.recordingFirestoreId,
                notifyBeforeMillis = notifyBefore?.inWholeMilliseconds)
        ).toInt()

        deadline?.let { time ->
            scheduleAlarm(alarmManager, context, id, time, isPreNotification = false)
            // The early heads-up alarm is only scheduled when its trigger time hasn't already passed.
            notifyBefore?.let { lead ->
                val preTime = time - lead
                if (preTime > Clock.System.now()) {
                    scheduleAlarm(alarmManager, context, id, preTime, isPreNotification = true)
                }
            }
            alarmManager.nextAlarmClock?.let {
                Logger.d { "Next alarm: ${it.triggerTime}" }
            } ?: Logger.d { "No next alarm" }
        }
        try {
            feedItems.createFeedItem(id, title, deadline, listId, notifyBefore, source)
        } catch (e: Exception) {
            // If the feed item creation fails, cancel the scheduled alarms and delete the reminder.
            cancelAlarm(alarmManager, context, id, isPreNotification = false)
            cancelAlarm(alarmManager, context, id, isPreNotification = true)
            db.localReminderDao().deleteReminder(id)
            throw e
        }
        return id.toString()
    }

    override suspend fun searchForList(listName: String): List<ReminderListEntry> =
        feedItems.searchForList(listName)

    override suspend fun cancelReminder(reminderId: Int) {
        db.localReminderDao().getReminder(reminderId) ?: return

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        cancelAlarm(alarmManager, context, reminderId, isPreNotification = false)
        cancelAlarm(alarmManager, context, reminderId, isPreNotification = true)

        // Also dismiss the notifications if they already fired (mirrors iOS removeDeliveredNotifications).
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(AndroidPlatform.NOTIFICATION_ID_BASE_REMINDER + reminderId)
        notificationManager.cancel(ReminderReceiver.preNotificationId(reminderId))

        db.localReminderDao().deleteReminder(reminderId)
    }

    override suspend fun cancelExtraNotification(reminderId: Int) {
        db.localReminderDao().getReminder(reminderId) ?: return
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        cancelAlarm(alarmManager, context, reminderId, isPreNotification = true)
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .cancel(ReminderReceiver.preNotificationId(reminderId))
        db.localReminderDao().clearNotifyBefore(reminderId)
    }

    // Built-in reminders need no account; permissions are checked when scheduling.
    override suspend fun signIn(uiContext: PlatformUiContext): Boolean = true
    override suspend fun unlink() {}
    override suspend fun isAuthorized(): Boolean = true

    companion object {
        /**
         * Builds the [PendingIntent] for a reminder's alarm. The early heads-up alarm and the due
         * alarm share a request code but carry different actions, so they resolve to distinct
         * PendingIntents (extras are not part of PendingIntent identity).
         */
        private fun alarmPendingIntent(context: Context, reminderId: Int, isPreNotification: Boolean): PendingIntent {
            val intent = Intent(context, ReminderReceiver::class.java).apply {
                if (isPreNotification) action = ReminderReceiver.ACTION_PRE_NOTIFICATION
                putExtra(ReminderReceiver.EXTRA_REMINDER_ID, reminderId)
            }
            return PendingIntent.getBroadcast(
                context,
                reminderId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        fun scheduleAlarm(alarmManager: AlarmManager, context: Context, reminderId: Int, triggerTime: Instant, isPreNotification: Boolean) {
            val pendingIntent = alarmPendingIntent(context, reminderId, isPreNotification)
            val info = AlarmManager.AlarmClockInfo(triggerTime.toEpochMilliseconds(), pendingIntent)
            alarmManager.setAlarmClock(info, pendingIntent)
        }

        private fun cancelAlarm(alarmManager: AlarmManager, context: Context, reminderId: Int, isPreNotification: Boolean) {
            alarmManager.cancel(alarmPendingIntent(context, reminderId, isPreNotification))
        }
    }
}
