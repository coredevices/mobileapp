package coredevices.pebble.firmware

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import coredevices.util.R
import io.rebble.libpebblecommon.connection.AppContext

actual fun postWatchFullyChargedNotification(appContext: AppContext, watchName: String) {
    val context = appContext.context
    context.createBatteryNotificationChannel()
    val builder = NotificationCompat.Builder(context, BATTERY_CHANNEL_ID)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle("Watch Fully Charged")
        .setContentText("$watchName is fully charged")
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
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
