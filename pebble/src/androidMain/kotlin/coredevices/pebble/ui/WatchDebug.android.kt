package coredevices.pebble.ui

import android.app.Notification
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_MUTABLE
import android.app.PendingIntent.FLAG_UPDATE_CURRENT
import android.app.RemoteInput
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import co.touchlab.kermit.Logger
import io.rebble.libpebblecommon.connection.AppContext
import java.time.LocalTime
import java.time.format.DateTimeFormatter


actual fun postTestNotification(appContext: AppContext) {
    val context = appContext.context
    val title = "Test Notification"
    val currentTime = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
    val body = "Test @ $currentTime"
    val notificationId = 1000
    val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
    val notificationChannel = android.app.NotificationChannel(
        "test_channel",
        "Test Channel",
        android.app.NotificationManager.IMPORTANCE_DEFAULT
    )
    notificationManager.createNotificationChannel(notificationChannel)
    val broadcastIntent = Intent(appContext.context, TestActionReceiver::class.java)

    val pendingIntent: PendingIntent =
        PendingIntent.getBroadcast(appContext.context, 0, broadcastIntent, FLAG_MUTABLE or FLAG_UPDATE_CURRENT)

    fun action(title: String, semanticAction: Int? = null): Notification.Action {
        val builder = Notification.Action.Builder(null, title, pendingIntent)
        if (semanticAction != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            builder.setSemanticAction(semanticAction)
        }
        return builder.build()
    }

    val replyAction = Notification.Action.Builder(null, "Reply", pendingIntent)
        .addRemoteInput(
            RemoteInput.Builder(REPLY_KEY)
                .setLabel("Reply")
                .setChoices(arrayOf("Choice 1", "Choice 2", "Choice 3"))
                .build()
        )
        .apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                setSemanticAction(Notification.Action.SEMANTIC_ACTION_REPLY)
            }
        }
        .build()

    val actions = mutableListOf(action("Test"))
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        actions += action("Like", Notification.Action.SEMANTIC_ACTION_THUMBS_UP)
        actions += action("Dislike", Notification.Action.SEMANTIC_ACTION_THUMBS_DOWN)
        actions += action("Mark read", Notification.Action.SEMANTIC_ACTION_MARK_AS_READ)
        actions += action("Mark unread", Notification.Action.SEMANTIC_ACTION_MARK_AS_UNREAD)
        actions += action("Delete", Notification.Action.SEMANTIC_ACTION_DELETE)
        actions += action("Archive", Notification.Action.SEMANTIC_ACTION_ARCHIVE)
        actions += action("Mute", Notification.Action.SEMANTIC_ACTION_MUTE)
        actions += action("Unmute", Notification.Action.SEMANTIC_ACTION_UNMUTE)
        actions += action("Call", Notification.Action.SEMANTIC_ACTION_CALL)
    }
    actions += replyAction

    val notificationBuilder = Notification.Builder(context, "test_channel")
        .setContentTitle(title)
        .setContentText(body)
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setAutoCancel(true)
        .setColor(0xFF00FF00.toInt())

    actions.forEach { notificationBuilder.addAction(it) }

    notificationManager.notify(notificationId, notificationBuilder.build())
}


private val REPLY_KEY = "key_text_reply"

class TestActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val results = RemoteInput.getResultsFromIntent(intent) ?: return
        val reply = results.getCharSequence(REPLY_KEY)
        Logger.d("TestActionReceiver got $intent with reply '$reply'")
        Toast.makeText(context, "Reply: $reply", Toast.LENGTH_LONG).show()
    }
}
