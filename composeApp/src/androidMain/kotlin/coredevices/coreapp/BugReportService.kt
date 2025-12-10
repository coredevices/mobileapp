package coredevices.coreapp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlin.coroutines.CoroutineContext

class BugReportService : Service(), CoroutineScope {
    private val job = SupervisorJob()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.IO + job

    private val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("Bug Report")
        .setSmallIcon(android.R.drawable.ic_dialog_info)

    companion object {
        private const val NOTIFICATION_ID_ONGOING = 1001
        private const val NOTIFICATION_ID_FINISHED = 1002
        private const val CHANNEL_ID = "bug_report_channel"
        private const val MESSAGE = "message"
        private const val COMMAND = "command"
        private const val COMMAND_START = 1
        private const val COMMAND_MESSAGE = 2
        private const val COMMAND_STOP = 3
        private val logger = Logger.withTag("BugReportService")
        private var lastMessage: String? = null

        fun startBugReport(
            context: Context,
        ) {
            val intent = Intent(context, BugReportService::class.java)
            intent.putExtra(COMMAND, COMMAND_START)
            context.startForegroundService(intent)
        }

        fun updateBugReport(
            context: Context,
            message: String,
        ) {
            val intent = Intent(context, BugReportService::class.java)
            intent.putExtra(COMMAND, COMMAND_MESSAGE)
            intent.putExtra(MESSAGE, message)
            context.startService(intent)
        }

        fun stopBugReport(
            context: Context,
        ) {
            val intent = Intent(context, BugReportService::class.java)
            intent.putExtra(COMMAND, COMMAND_STOP)
            context.startService(intent)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        val command = intent.getIntExtra(COMMAND, 0)
        when (command) {
            COMMAND_START -> {
                logger.v { "startForeground" }
                val notification = createNotification("Creating bug report...")
                try {
                    ServiceCompat.startForeground(
                        this,
                        NOTIFICATION_ID_ONGOING,
                        notification,
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                        } else {
                            0
                        }
                    )
                } catch (_: SecurityException) {
                    logger.w { "Failed to start bugreport service in foreground" }
                }
            }
            COMMAND_MESSAGE -> {
                updateNotification(intent.getStringExtra(MESSAGE) ?: "")
            }
            COMMAND_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                lastMessage?.let { postFinishedNotification(it) }
            }
        }

        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Bug Report Processing",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Shows progress while processing bug reports"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun createNotification(text: String): Notification {
        notificationBuilder.setContentText(text)
        notificationBuilder.setOngoing(true)
        notificationBuilder.setProgress(0, 0, true)
        return notificationBuilder.build()
    }

    private fun updateNotification(text: String) {
        lastMessage = text
        val notification = createNotification(text)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID_ONGOING, notification)
    }

    private fun postFinishedNotification(text: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Bug Report")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(false)
            .build()
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID_FINISHED, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        cancel()
    }
}
