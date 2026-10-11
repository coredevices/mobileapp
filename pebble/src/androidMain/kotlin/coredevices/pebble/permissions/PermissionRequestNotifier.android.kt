package coredevices.pebble.permissions

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import co.touchlab.kermit.Logger
import coredevices.util.R
import io.rebble.libpebblecommon.connection.AppContext
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import kotlin.uuid.Uuid

actual fun notifyPermissionRequest(appContext: AppContext, notification: PermissionRequestNotification) {
    val context = appContext.context
    context.createPermissionChannel()
    val open = context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
        it.data = notification.deepLink.toUri()
        PendingIntent.getActivity(context, notification.key, it, PendingIntent.FLAG_IMMUTABLE)
    }
    val all = if (notification.permissions.size > 1) " all" else ""
    fun answer(granted: Boolean) = NotificationCompat.Action.Builder(
        null,
        (if (granted) "Allow" else "Deny") + all,
        PendingIntent.getBroadcast(
            context,
            notification.key * 2 + if (granted) 1 else 0,
            Intent(context, PermissionRequestReceiver::class.java)
                .putExtra(EXTRA_APP_UUID, notification.appUuid.toString())
                .putExtra(EXTRA_PERMISSIONS, notification.permissions.toTypedArray())
                .putExtra(EXTRA_GRANTED, granted),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        ),
    ).setShowsUserInterface(false).build()
    val builder = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(notification.title)
        .setContentText(notification.body)
        .setStyle(NotificationCompat.BigTextStyle().bigText(notification.body))
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setContentIntent(open)
        .setOnlyAlertOnce(true)
        .addAction(answer(granted = false))
        .addAction(answer(granted = true))
    context.getSystemService(NotificationManager::class.java).notify(notification.key, builder.build())
}

actual fun removePermissionRequestNotification(appContext: AppContext, key: Int) {
    appContext.context.getSystemService(NotificationManager::class.java).cancel(key)
}

private const val CHANNEL_ID = "app_permission_requests"
private const val EXTRA_APP_UUID = "app_uuid"
private const val EXTRA_PERMISSIONS = "permissions"
private const val EXTRA_GRANTED = "granted"

private fun Context.createPermissionChannel() {
    getSystemService(NotificationManager::class.java).createNotificationChannel(
        NotificationChannel(CHANNEL_ID, "App permission requests", NotificationManager.IMPORTANCE_HIGH)
    )
}

/** Records the answer given on the notification, without opening the app. */
class PermissionRequestReceiver : BroadcastReceiver(), KoinComponent {
    override fun onReceive(context: Context, intent: Intent) {
        val appUuid = intent.getStringExtra(EXTRA_APP_UUID)?.let(Uuid::parse) ?: return
        val permissions = intent.getStringArrayExtra(EXTRA_PERMISSIONS)?.toList() ?: return
        val granted = intent.getBooleanExtra(EXTRA_GRANTED, false)
        Logger.withTag("PermissionRequestReceiver").d { "$appUuid $permissions granted=$granted" }
        context.getSystemService(NotificationManager::class.java)
            .cancel(PermissionRequestNotifier.keyOf(appUuid))
        val pending = goAsync()
        GlobalScope.launch {
            try {
                answerPermissionRequests(get(), appUuid, permissions, granted)
            } finally {
                pending.finish()
            }
        }
    }
}
