package coredevices.ring.service

import android.app.KeyguardManager
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.atomic.AtomicInteger

class InferenceForegroundService : Service() {
    companion object {
        private const val NOTIFICATION_ID = 7631
        private const val CHANNEL_ID = "inference_fg"

        private val _running = MutableStateFlow(false)
        val running: StateFlow<Boolean> = _running

        private val _activityRunning = MutableStateFlow(false)
        val activityRunning: StateFlow<Boolean> = _activityRunning

        private val refCount = AtomicInteger(0)

        fun acquire(context: Context): Boolean {
            val isOuter = refCount.getAndIncrement() == 0
            if (isOuter) {
                val serviceIntent = Intent(context, InferenceForegroundService::class.java)
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                } catch (_: Exception) { }
                val keyguard = context.getSystemService(KeyguardManager::class.java)
                val screenLocked = keyguard?.isKeyguardLocked == true
                if (!screenLocked) {
                    try {
                        context.startActivity(
                            Intent(context, InferenceActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION
                            }
                        )
                    } catch (_: Exception) { }
                }
            }
            return isOuter
        }

        fun hasActiveSessions() = refCount.get() > 0

        fun release(context: Context) {
            if (refCount.decrementAndGet() == 0) {
                // Post to main thread to ensure onStartCommand/startForeground runs first
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    if (refCount.get() == 0) {
                        context.stopService(Intent(context, InferenceForegroundService::class.java))
                    }
                }
            }
        }

        internal fun onActivityRunning(running: Boolean) {
            _activityRunning.value = running
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val channel = NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManager.IMPORTANCE_LOW)
            .setName("Speech Processing")
            .build()
        NotificationManagerCompat.from(this).createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Processing speech…")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                this, NOTIFICATION_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        _running.value = true
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        _running.value = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
