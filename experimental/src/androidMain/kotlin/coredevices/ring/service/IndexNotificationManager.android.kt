package coredevices.ring.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.PendingIntentCompat
import androidx.core.net.toUri
import kotlin.math.roundToInt

const val INDEX_TRANSFER_NOTIFICATION_CHANNEL_ID = "index_transfer"
const val INDEX_TRANSFER_NOTIFICATION_CHANNEL_NAME = "Index Recordings"
const val INDEX_ACTION_NOTIFICATION_CHANNEL_ID = "index_action"
const val INDEX_ACTION_NOTIFICATION_CHANNEL_NAME = "Index Action"

private fun IndexNotificationChannel.channelId() = when (this) {
    IndexNotificationChannel.Default -> INDEX_TRANSFER_NOTIFICATION_CHANNEL_ID
    IndexNotificationChannel.IndexAction -> INDEX_ACTION_NOTIFICATION_CHANNEL_ID
}

actual class PlatformIndexNotificationManager(
    private val context: Context,
) {
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private var channelsCreated = false

    // Notifications are also posted while the ring service is down, so create the channels here.
    private fun ensureChannels() {
        if (channelsCreated) return
        val manager = NotificationManagerCompat.from(context)
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(INDEX_TRANSFER_NOTIFICATION_CHANNEL_ID, NotificationManager.IMPORTANCE_DEFAULT)
                .setName(INDEX_TRANSFER_NOTIFICATION_CHANNEL_NAME)
                .build()
        )
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(INDEX_ACTION_NOTIFICATION_CHANNEL_ID, NotificationManager.IMPORTANCE_DEFAULT)
                .setName(INDEX_ACTION_NOTIFICATION_CHANNEL_NAME)
                .build()
        )
        channelsCreated = true
    }

    private fun buildDebugNotification(channelId: String) =
        NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setGroup("index_transfer")
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
    actual fun notify(notification: GenericNotification) {
        ensureChannels()
        val uriIntent = notification.deepLink?.let {
            Intent(context, Class.forName("coredevices.coreapp.MainActivity")).apply {
                data = it.toUri()
                action = Intent.ACTION_VIEW
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        }
        val notif = buildDebugNotification(notification.channel.channelId())
            .setContentTitle(notification.title)
            .setContentText(notification.contentText)
            .apply {
                notification.timeoutAfter?.let { setTimeoutAfter(it.inWholeMilliseconds) }
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
                        NotificationCompat.Action.Builder(0, act.title, actionPendingIntent(act))
                            .setShowsUserInterface(!act.background)
                            .build()
                    )
                }
            }
            .build()
        notificationManager.notify(notification.id, notif)
    }

    private fun actionPendingIntent(act: NotificationAction): PendingIntent? = if (act.background) {
        PendingIntentCompat.getBroadcast(
            context,
            0,
            Intent(context, IndexNotificationActionReceiver::class.java).apply {
                data = act.deepLink.toUri()
                action = Intent.ACTION_VIEW
            },
            PendingIntent.FLAG_UPDATE_CURRENT,
            false
        )
    } else {
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
    }

    actual fun cancel(notificationId: Int) {
        notificationManager.cancel(notificationId)
    }
}