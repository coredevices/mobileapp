package io.rebble.libpebblecommon.plugin

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import androidx.core.content.ContextCompat
import co.touchlab.kermit.Logger
import io.rebble.libpebblecommon.connection.AppContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val CHANNEL_ID = "find_my_phone"
private const val NOTIFICATION_ID = 74901
private const val ACTION_STOP_RINGING = "io.rebble.libpebblecommon.STOP_FIND_MY_PHONE_RINGING"

actual class PhoneSoundPlayer(
    private val appContext: AppContext,
) {
    private val logger = Logger.withTag("PhoneSoundPlayer")
    private val _isRinging = MutableStateFlow(false)
    actual val isRinging: Flow<Boolean> = _isRinging.asStateFlow()

    private var mediaPlayer: MediaPlayer? = null
    private var previousAlarmVolume: Int? = null
    private var stopReceiver: BroadcastReceiver? = null

    actual fun startRinging() {
        if (_isRinging.value) return
        val context = appContext.context
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        try {
            // Can throw SecurityException under DND without notification-policy access; the
            // alarm usage below still plays at whatever the current alarm volume is.
            previousAlarmVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
            audioManager.setStreamVolume(
                AudioManager.STREAM_ALARM,
                audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM),
                0,
            )
        } catch (e: SecurityException) {
            logger.w(e) { "Unable to raise alarm volume" }
        }
        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(context, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = true
                // Ring out the phone's own speaker even when a headset or Bluetooth sink is
                // connected — a lost phone often still has earbuds paired.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                        .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                        ?.let { setPreferredDevice(it) }
                }
                prepare()
                start()
            }
            _isRinging.value = true
            showStopNotification(context)
        } catch (e: Exception) {
            logger.w(e) { "Failed to start ringing" }
            cleanUp()
        }
    }

    actual fun stopRinging() {
        if (!_isRinging.value) return
        cleanUp()
    }

    private fun showStopNotification(context: Context) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Find My Phone",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            // The alarm sound is already playing; the notification itself stays silent.
            setSound(null, null)
        }
        notificationManager.createNotificationChannel(channel)

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                stopRinging()
            }
        }
        stopReceiver = receiver
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(ACTION_STOP_RINGING),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        val stopIntent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(ACTION_STOP_RINGING).setPackage(context.packageName),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Finding your phone")
            .setContentText("Ringing from your watch")
            .setCategory(Notification.CATEGORY_ALARM)
            .setLocalOnly(true)
            .setDeleteIntent(stopIntent)
            .addAction(Notification.Action.Builder(null, "Stop", stopIntent).build())
            .build()
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun cleanUp() {
        val context = appContext.context
        mediaPlayer?.let { player ->
            try {
                player.stop()
            } catch (e: IllegalStateException) {
                logger.w(e) { "MediaPlayer already stopped" }
            }
            player.release()
        }
        mediaPlayer = null
        stopReceiver?.let { receiver ->
            try {
                context.unregisterReceiver(receiver)
            } catch (e: IllegalArgumentException) {
                logger.w(e) { "Receiver already unregistered" }
            }
        }
        stopReceiver = null
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .cancel(NOTIFICATION_ID)
        previousAlarmVolume?.let { volume ->
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            try {
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, volume, 0)
            } catch (e: SecurityException) {
                logger.w(e) { "Unable to restore alarm volume" }
            }
        }
        previousAlarmVolume = null
        _isRinging.value = false
    }
}
