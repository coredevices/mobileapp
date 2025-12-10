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
import coredevices.util.R
import io.rebble.libpebblecommon.connection.AppContext
import io.rebble.libpebblecommon.connection.ConnectedPebble
import io.rebble.libpebblecommon.connection.FirmwareUpdateCheckResult
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

    val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
    val activityIntent = PendingIntent.getActivity(
        context,
        0,
        intent,
        PendingIntent.FLAG_IMMUTABLE
    )
    val broadcastIntent = Intent(appContext.context, UpdateActionReceiver::class.java)
    broadcastIntent.putExtra(EXTRA_WATCH_IDENTIFIER, identifier.asString)
    val updateIntent: PendingIntent =
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
        .setContentIntent(activityIntent)
        .setAutoCancel(true)
        .extend(
            NotificationCompat.WearableExtender()
                .addAction(
                    NotificationCompat.Action.Builder(null, "Update Now", updateIntent).build()
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
        val watch =
            libPebble.watches.value.firstOrNull { it.identifier.asString == identifier }
        if (watch == null) {
            logger.w { "No matching connected watch found for $identifier" }
            return
        }
        val update = (watch as? ConnectedPebble.Firmware)?.firmwareUpdateAvailable
        if (update !is FirmwareUpdateCheckResult.FoundUpdate) {
            logger.w { "No update available for $watch" }
            return
        }
        val updater = watch as? ConnectedPebble.Firmware
        if (updater == null) {
            logger.w { "Can't update firmware for $watch" }
            return
        }
        logger.d { "Starting update for $identifier to $update" }
        updater.updateFirmware(update)
    }
}

actual fun removeFirmwareUpdateNotification(appContext: AppContext, key: Int) {
    val notificationManager =
        appContext.context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    notificationManager.cancel(key)
}