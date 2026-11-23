package coredevices.coreapp.api

import CommonRoutes
import PlatformContext
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.TaskStackBuilder
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.eygraber.uri.toAndroidUri
import coredevices.coreapp.MainActivity
import coredevices.coreapp.ui.navigation.CoreDeepLinkHandler.Companion.asUri
import coredevices.util.R
import coreapp.composeapp.generated.resources.Res
import coreapp.composeapp.generated.resources.support_chat_channel_name
import org.jetbrains.compose.resources.getString

actual suspend fun createNotification(
    platformContext: PlatformContext,
    title: String,
    message: String,
    conversationId: String,
) {
    val route = CommonRoutes.ViewBugReportRoute(conversationId)
    val uri = route.asUri()
    val deepLinkIntent = Intent(
        Intent.ACTION_VIEW,
        uri.toAndroidUri(),
        platformContext.context,
        MainActivity::class.java
    )

    val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    } else {
        PendingIntent.FLAG_UPDATE_CURRENT
    }

    val deepLinkPendingIntent: PendingIntent? = TaskStackBuilder.create(platformContext.context).run {
        addNextIntentWithParentStack(deepLinkIntent)
        getPendingIntent(0, pendingIntentFlags)
    }

    platformContext.context.createChannel(getString(Res.string.support_chat_channel_name))
    val builder = NotificationCompat.Builder(platformContext.context, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(title)
        .setContentText(message)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setContentIntent(deepLinkPendingIntent)
        .setAutoCancel(true)
    val manager = platformContext.context.getSystemService(NotificationManager::class.java)
    manager.notify(NOTIFICATION_ID, builder.build())
}

private fun Context.createChannel(name: String) {
    val channel = NotificationChannel(
        CHANNEL_ID,
        name,
        NotificationManager.IMPORTANCE_DEFAULT
    ).apply {
        description = name
    }
    val manager = getSystemService(NotificationManager::class.java)
    manager.createNotificationChannel(channel)
}

private const val CHANNEL_ID = "support_chat_channel"
private const val ACTION = "action_view_support_chat"
private const val EXTRA_CONVERSATION_ID = "conversation_id"
private const val NOTIFICATION_ID = 3006071

