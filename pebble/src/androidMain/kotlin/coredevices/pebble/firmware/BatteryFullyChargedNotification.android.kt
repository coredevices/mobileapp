package coredevices.pebble.firmware

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import com.eygraber.uri.toAndroidUri
import coredevices.pebble.RealPebbleDeepLinkHandler.Companion.NOTIFICATION_INTENT_URI_SHOW_WATCHES
import coredevices.util.R
import io.rebble.libpebblecommon.connection.AppContext

actual fun postWatchFullyChargedNotification(appContext: AppContext, watchName: String) {
    val context = appContext.context
    context.createBatteryNotificationChannel()
    val viewIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
    viewIntent?.setData(NOTIFICATION_INTENT_URI_SHOW_WATCHES.toAndroidUri())
    val viewPendingIntent = PendingIntent.getActivity(
        context,
        0,
        viewIntent,
        PendingIntent.FLAG_IMMUTABLE
    )
    val builder = NotificationCompat.Builder(context, BATTERY_CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle("Watch Fully Charged")
        .setContentText("$watchName is fully charged")
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setContentIntent(viewPendingIntent)
        .setAutoCancel(true)
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    notificationManager.notify(WATCH_FULLY_CHARGED_NOTIFICATION_ID, builder.build())
}

private const val BATTERY_CHANNEL_ID = "battery_fully_charged_channel"
private const val WATCH_FULLY_CHARGED_NOTIFICATION_ID = 1001

private fun Context.createBatteryNotificationChannel() {
    val channel = NotificationChannel(
        BATTERY_CHANNEL_ID,
        "Battery",
        NotificationManager.IMPORTANCE_DEFAULT
    ).apply {
        description = "Watch fully charged notifications"
    }
    val manager = getSystemService(NotificationManager::class.java)
    manager.createNotificationChannel(channel)
}
