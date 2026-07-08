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
import coredevices.util.AndroidPlatform
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

        /** Notification id for the early heads-up; kept disjoint from the due notification's id. */
        fun preNotificationId(reminderId: Int) =
            AndroidPlatform.NOTIFICATION_ID_BASE_REMINDER_PRE + reminderId

        private val logger = Logger.withTag(ReminderReceiver::class.simpleName!!)
    }

    private val scope = CoroutineScope(Dispatchers.Default)
    private val localReminderDao: LocalReminderDao by inject()
    private val deepLinkResolver: ReminderDeepLinkResolver by inject()

    private fun makeNotification(context: Context, data: LocalReminderData, deepLink: String, isPreNotification: Boolean) =
        NotificationCompat.Builder(context, "reminders")
            .setContentTitle(if (isPreNotification) "Upcoming reminder" else "Reminder")
            .setContentText(data.message)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setGroup("reminders")
            .setAutoCancel(true)
            .setContentIntent(makeContentIntent(context, notificationId(data.id, isPreNotification), deepLink))
            .build()

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

    private fun notificationId(reminderId: Int, isPreNotification: Boolean) =
        if (isPreNotification) preNotificationId(reminderId)
        else AndroidPlatform.NOTIFICATION_ID_BASE_REMINDER + reminderId
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
        val isPreNotification = intent.action == ACTION_PRE_NOTIFICATION
        logger.d { "Received reminder alarm broadcast (pre=$isPreNotification)" }
        val reminderId = intent.getIntExtra(EXTRA_REMINDER_ID, -1)
        if (reminderId == -1) {
            logger.e("Reminder ID not found in input data")
            return
        }
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureChannelCreated(notificationManager)
        scope.launch {
            val reminder = localReminderDao.getReminder(reminderId)
            if (reminder == null) {
                logger.e("Reminder with ID $reminderId not found in database")
                return@launch
            }

            val notification = makeNotification(context, reminder, deepLinkResolver.resolveDeepLink(reminder.id), isPreNotification)
            withContext(Dispatchers.Main) {
                notificationManager.notify(notificationId(reminderId, isPreNotification), notification)
            }
        }
    }
}