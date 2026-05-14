package coredevices.pebble.firmware

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_MUTABLE
import android.app.PendingIntent.FLAG_UPDATE_CURRENT
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import co.touchlab.kermit.Logger
import com.eygraber.uri.toAndroidUri
import coredevices.pebble.RealPebbleDeepLinkHandler.Companion.NOTIFICATION_INTENT_URI_SHOW_WATCHES
import coredevices.pebble.RealPebbleDeepLinkHandler.Companion.updateNowUri
import coredevices.util.R
import io.rebble.libpebblecommon.connection.AppContext
import io.rebble.libpebblecommon.connection.LibPebble
import io.rebble.libpebblecommon.connection.PebbleIdentifier
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

actual fun notifyFirmwareUpdate(
    appContext: AppContext,
    title: String,
    body: String,
    key: Int,
    identifier: PebbleIdentifier,
) {
    val context = appContext.context
    context.createFwupNotificationChannel()

    val viewIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
    viewIntent?.setData(NOTIFICATION_INTENT_URI_SHOW_WATCHES.toAndroidUri())
    val viewPendingIntent = PendingIntent.getActivity(
        context,
        0,
        viewIntent,
        PendingIntent.FLAG_IMMUTABLE
    )
    val updatePhoneIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
    updatePhoneIntent?.setData(updateNowUri(identifier).toAndroidUri())
    updatePhoneIntent?.putExtra(EXTRA_WATCH_IDENTIFIER, identifier.asString)
    val updatePhonePendingIntent = PendingIntent.getActivity(
        context,
        0,
        updatePhoneIntent,
        PendingIntent.FLAG_IMMUTABLE
    )
    val broadcastIntent = Intent(appContext.context, UpdateActionReceiver::class.java)
    broadcastIntent.putExtra(EXTRA_WATCH_IDENTIFIER, identifier.asString)
    val updateIntentWatch: PendingIntent =
        PendingIntent.getBroadcast(
            appContext.context,
            0,
            broadcastIntent,
            FLAG_MUTABLE or FLAG_UPDATE_CURRENT
        )
    val builder = NotificationCompat.Builder(
        context,
        CHANNEL_ID,
    )
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle(title)
        .setContentText(body)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setContentIntent(viewPendingIntent)
        .setAutoCancel(true)
        .addAction(
            NotificationCompat.Action.Builder(null, "Update Now", updatePhonePendingIntent)
                .setShowsUserInterface(true)
                .build()
        )
        .extend(
            NotificationCompat.WearableExtender()
                .addAction(
                    NotificationCompat.Action.Builder(null, "Update Now", updateIntentWatch).build()
                )
        )
    val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    notificationManager.notify(key, builder.build())
}

private const val CHANNEL_ID = "firmware_update_channel"

private fun Context.createFwupNotificationChannel() {
    val channel = NotificationChannel(
        CHANNEL_ID,
        "Firmware Updates",
        NotificationManager.IMPORTANCE_DEFAULT
    ).apply {
        description = "Firmware udpate notifications"
    }
    val manager = getSystemService(NotificationManager::class.java)
    manager.createNotificationChannel(channel)
}

private const val EXTRA_WATCH_IDENTIFIER = "IDENTIFIER"

class UpdateActionReceiver : BroadcastReceiver(), KoinComponent {
    private val logger = Logger.withTag("UpdateActionReceiver")

    override fun onReceive(context: Context, intent: Intent) {
        val identifier = intent.getStringExtra(EXTRA_WATCH_IDENTIFIER)
        logger.d { "Update action received for identifier=$identifier" }
        if (identifier == null) {
            return
        }
        val libPebble = get<LibPebble>()
        val firmwareUpdateUiTracker: FirmwareUpdateUiTracker = get()
        firmwareUpdateUiTracker.updateWatchNow(libPebble, identifier)
    }
}

actual fun removeFirmwareUpdateNotification(appContext: AppContext, key: Int) {
    val notificationManager =
        appContext.context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    notificationManager.cancel(key)
}
