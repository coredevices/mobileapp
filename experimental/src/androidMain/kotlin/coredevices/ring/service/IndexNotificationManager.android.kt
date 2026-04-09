package coredevices.ring.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.PendingIntentCompat
import coredevices.ring.service.RingService.Companion.DEBUG_NOTIFICATION_CHANNEL_ID
import androidx.core.net.toUri
import androidx.glance.action.action
import coredevices.ExperimentalDevices
import kotlin.math.roundToInt

actual class PlatformIndexNotificationManager(
    private val context: Context,
) {
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private fun buildDebugNotification() =
        NotificationCompat.Builder(context, DEBUG_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setGroup("ring_debug")
            .setCategory(NotificationCompat.CATEGORY_STATUS)
    actual fun notify(notification: GenericNotification) {
        val uriIntent = notification.deepLink?.let {
            Intent(context, Class.forName("coredevices.coreapp.MainActivity")).apply {
                data = it.toUri()
                action = Intent.ACTION_VIEW
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        }
        val notif = buildDebugNotification()
            .setContentTitle(notification.title)
            .setContentText(notification.contentText)
            .apply {
                if (notification.contentText?.contains("\n") == true) {
                    setStyle(NotificationCompat.BigTextStyle())
                }
                when (notification.inProgress) {
                    is NotificationProgress.Indeterminate -> setProgress(0, 0, true)
                    is NotificationProgress.Determinate -> {
                        val progress = notification.inProgress.progress
                        setProgress(100, (progress*100).roundToInt(), false)
                    }
                    null -> {}
                }
                uriIntent?.let {
                    val pendingIntent = PendingIntentCompat.getActivity(
                        context,
                        0,
                        it,
                        PendingIntent.FLAG_UPDATE_CURRENT,
                        false
                    )
                    setContentIntent(pendingIntent)
                }
                notification.actions.forEach { act ->
                    addAction(
                        0,
                        act.title,
                        PendingIntentCompat.getActivity(
                            context,
                            0,
                            Intent(context, Class.forName("coredevices.coreapp.MainActivity")).apply {
                                data = act.deepLink.toUri()
                                action = Intent.ACTION_VIEW
                                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                            },
                            PendingIntent.FLAG_UPDATE_CURRENT,
                            false
                        )
                    )
                }
            }
            .build()
        notificationManager.notify(notification.id, notif)
    }

    actual fun cancel(notificationId: Int) {
        notificationManager.cancel(notificationId)
    }
}