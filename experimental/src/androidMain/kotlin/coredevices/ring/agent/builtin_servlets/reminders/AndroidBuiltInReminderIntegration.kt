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
import coredevices.ring.database.room.repository.ListRepository
import coredevices.ring.reminders.ReminderReceiver
import kotlinx.coroutines.flow.first
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class AndroidBuiltInReminderIntegration : BuiltInReminderIntegration, KoinComponent {
    private val context: Context by inject()
    private val db: RingDatabase by inject()
    private val feedItems: BuiltInReminderFeedItems by inject()
    private val listRepository: ListRepository by inject()

    private val alarmManager get() = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val notificationManager get() = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            check(alarmManager.canScheduleExactAlarms()) { "No permissions to schedule reminders, check under 'Special app access > Alarms and reminders' in Settings app." }
        }

        val itemId = feedItems.newItemId()
        scheduleAlarms(itemId, deadline, notifyBefore)
        db.localReminderDao().upsert(LocalReminderData(itemId, deadline, title, notifyBefore?.inWholeMilliseconds))
        try {
            feedItems.createFeedItem(itemId, title, deadline, listId, notifyBefore, source)
        } catch (e: Exception) {
            cancelAlarms(itemId)
            db.localReminderDao().deleteReminder(itemId)
            throw e
        }
        return itemId
    }

    override suspend fun searchForList(listName: String): List<ReminderListEntry> =
        feedItems.searchForList(listName)

    override suspend fun cancelReminder(itemId: String) {
        cancelAlarms(itemId)
        notificationManager.cancel(itemId, ReminderReceiver.NOTIF_ID_DUE)
        notificationManager.cancel(itemId, ReminderReceiver.NOTIF_ID_PRE)
        db.localReminderDao().deleteReminder(itemId)
    }

    override suspend fun syncReminder(itemId: String, message: String, time: Instant?, notifyBefore: Duration?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            Logger.w { "Skipping reminder sync: notification permission not granted" }
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            Logger.w { "Skipping reminder sync: exact alarm permission not granted" }
            return
        }
        cancelAlarms(itemId)
        notificationManager.cancel(itemId, ReminderReceiver.NOTIF_ID_DUE)
        notificationManager.cancel(itemId, ReminderReceiver.NOTIF_ID_PRE)
        // Schedule before recording the row, so a scheduling failure leaves the ledger stale and
        // the next reconcile retries rather than believing this reminder is armed.
        scheduleAlarms(itemId, time, notifyBefore)
        db.localReminderDao().upsert(LocalReminderData(itemId, time, message, notifyBefore?.inWholeMilliseconds))
    }

    override suspend fun cancelExtraNotification(itemId: String) {
        cancelAlarm(itemId, isPreNotification = true)
        notificationManager.cancel(itemId, ReminderReceiver.NOTIF_ID_PRE)
        db.localReminderDao().clearNotifyBefore(itemId)
    }

    // AlarmManager exposes no way to enumerate old int-keyed PendingIntents; a legacy alarm that
    // still fires reaches ReminderReceiver with an int (not string) extra, so it no-ops rather than
    // double-firing. Nothing to purge.
    override suspend fun purgeLegacySchedules() {}

    private fun scheduleAlarms(itemId: String, time: Instant?, notifyBefore: Duration?) {
        val fireTime = time ?: return
        if (fireTime <= Clock.System.now()) return
        scheduleAlarm(alarmManager, context, itemId, fireTime, isPreNotification = false)
        notifyBefore?.let { lead ->
            val preTime = fireTime - lead
            if (preTime > Clock.System.now()) {
                scheduleAlarm(alarmManager, context, itemId, preTime, isPreNotification = true)
            }
        }
    }

    private fun cancelAlarms(itemId: String) {
        cancelAlarm(itemId, isPreNotification = false)
        cancelAlarm(itemId, isPreNotification = true)
    }

    private fun cancelAlarm(itemId: String, isPreNotification: Boolean) =
        alarmManager.cancel(alarmPendingIntent(context, itemId, isPreNotification))

    override suspend fun getAllLists(): List<ReminderListEntry> {
        val lists = listRepository.getAllFlow().first()
        return lists.map { ReminderListEntry(it.firestoreId, it.title) }
    }

    // Built-in reminders need no account; permissions are checked when scheduling.
    override suspend fun signIn(uiContext: PlatformUiContext): Boolean = true
    override suspend fun unlink() {}
    override suspend fun isAuthorized(): Boolean = true

    companion object {
        private fun alarmPendingIntent(context: Context, itemId: String, isPreNotification: Boolean): PendingIntent {
            val intent = Intent(context, ReminderReceiver::class.java).apply {
                if (isPreNotification) action = ReminderReceiver.ACTION_PRE_NOTIFICATION
                // Distinct data per (item, kind) so colliding hashCodes never share a PendingIntent.
                data = ReminderReceiver.reminderUri(itemId, if (isPreNotification) "pre" else "due")
                putExtra(ReminderReceiver.EXTRA_REMINDER_ID, itemId)
            }
            return PendingIntent.getBroadcast(
                context,
                itemId.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        fun scheduleAlarm(alarmManager: AlarmManager, context: Context, itemId: String, triggerTime: Instant, isPreNotification: Boolean) {
            val pendingIntent = alarmPendingIntent(context, itemId, isPreNotification)
            val info = AlarmManager.AlarmClockInfo(triggerTime.toEpochMilliseconds(), pendingIntent)
            alarmManager.setAlarmClock(info, pendingIntent)
        }
    }
}
