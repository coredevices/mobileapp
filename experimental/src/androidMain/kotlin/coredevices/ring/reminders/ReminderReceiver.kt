package coredevices.ring.reminders

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.PendingIntentCompat
import androidx.core.net.toUri
import co.touchlab.kermit.Logger
import coredevices.ring.data.entity.room.reminders.LocalReminderData
import coredevices.ring.database.room.dao.LocalReminderDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class ReminderReceiver: BroadcastReceiver(), KoinComponent {
    companion object {
        const val EXTRA_REMINDER_ID = "reminder_id"

        /** Distinguishes the early heads-up alarm/notification from the due-time one. */
        const val ACTION_PRE_NOTIFICATION = "coredevices.ring.reminders.PRE_NOTIFICATION"

        /** Broadcast action fired by the notification's "Done" button (MOB-8439). */
        const val ACTION_MARK_DONE = "coredevices.ring.reminders.ACTION_MARK_DONE"

        // Notifications are keyed by (tag = itemId, id); the constant ids only separate the due
        // notification from the early heads-up, so no per-item integer is derived and hashed.
        const val NOTIF_ID_DUE = 1
        const val NOTIF_ID_PRE = 2
        fun notifId(isPreNotification: Boolean) = if (isPreNotification) NOTIF_ID_PRE else NOTIF_ID_DUE

        /** Per-reminder Intent data, so two item ids that hash alike never share a PendingIntent
         *  (PendingIntent identity ignores extras but includes data). */
        fun reminderUri(itemId: String, suffix: String) =
            "coredevices-ring-reminder://$itemId/$suffix".toUri()

        private val logger = Logger.withTag(ReminderReceiver::class.simpleName!!)
    }

    private val scope = CoroutineScope(Dispatchers.Default)
    private val localReminderDao: LocalReminderDao by inject()
    private val deepLinkResolver: ReminderDeepLinkResolver by inject()
    private val reminderCompleter: ReminderCompleter by inject()

    private fun makeNotification(context: Context, data: LocalReminderData, deepLink: String, isPreNotification: Boolean) =
        NotificationCompat.Builder(context, "reminders")
            .setContentTitle(if (isPreNotification) "Upcoming reminder" else "Reminder")
            .setContentText(data.message)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setGroup("reminders")
            .setAutoCancel(true)
            .setContentIntent(makeContentIntent(context, data.itemId.hashCode(), deepLink))
            // showsUserInterface must be false (it's a no-UI broadcast) or the watch mirror drops the action
            .addAction(
                NotificationCompat.Action.Builder(0, "Done", makeMarkDonePendingIntent(context, data.itemId))
                    .setShowsUserInterface(false)
                    .build()
            )
            .build()

    private fun makeMarkDonePendingIntent(context: Context, itemId: String): PendingIntent? {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_MARK_DONE
            data = reminderUri(itemId, "done")
            putExtra(EXTRA_REMINDER_ID, itemId)
        }
        return PendingIntentCompat.getBroadcast(
            context,
            itemId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT,
            false
        )
    }

    private fun makeContentIntent(context: Context, requestCode: Int, deepLink: String): PendingIntent? {
        val intent = Intent(context, Class.forName("coredevices.coreapp.MainActivity")).apply {
            data = deepLink.toUri()
            action = Intent.ACTION_VIEW
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntentCompat.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT,
            false
        )
    }

    private fun ensureChannelCreated(notificationManager: NotificationManager) {
        notificationManager.createNotificationChannel(
            NotificationChannel(
                "reminders",
                "Reminders",
                NotificationManager.IMPORTANCE_HIGH
            )
        )
    }

    override fun onReceive(context: Context, intent: Intent) {
        val itemId = intent.getStringExtra(EXTRA_REMINDER_ID)
        if (itemId == null) {
            logger.e("Reminder ID not found in input data")
            return
        }
        if (intent.action == ACTION_MARK_DONE) {
            handleMarkDone(context, itemId)
            return
        }
        val isPreNotification = intent.action == ACTION_PRE_NOTIFICATION
        logger.d { "Received reminder alarm broadcast (pre=$isPreNotification)" }
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureChannelCreated(notificationManager)
        scope.launch {
            val reminder = localReminderDao.getReminder(itemId)
            if (reminder == null) {
                logger.e("Reminder with ID $itemId not found in database")
                return@launch
            }

            val notification = makeNotification(context, reminder, deepLinkResolver.resolveDeepLink(reminder.itemId), isPreNotification)
            withContext(Dispatchers.Main) {
                notificationManager.notify(itemId, notifId(isPreNotification), notification)
            }
        }
    }

    /** Handle the "Done" action: complete the backing feed item (which cancels the reminder and
     *  dismisses its notifications) and, as a fallback, dismiss them even when there is no linked
     *  item to complete. Uses [goAsync] so the work survives the receiver returning. */
    private fun handleMarkDone(context: Context, itemId: String) {
        logger.d { "Received reminder mark-done broadcast for $itemId" }
        val pendingResult = goAsync()
        scope.launch {
            try {
                runCatching { reminderCompleter.markDone(itemId) }
                    .onFailure { logger.e(it) { "Failed to mark reminder $itemId done" } }
                val notificationManager =
                    context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.cancel(itemId, NOTIF_ID_DUE)
                notificationManager.cancel(itemId, NOTIF_ID_PRE)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
