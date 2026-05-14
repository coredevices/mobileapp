package coredevices.coreapp

import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import co.touchlab.kermit.Logger
import coredevices.haversine.KMPHaversineSatelliteManager
import coredevices.ring.database.Preferences
import coredevices.ring.service.IndexNotificationManager
import coredevices.ring.service.PEBBLE_DEBUG_NOTIFICATION_CHANNEL_ID
import coredevices.ring.service.PEBBLE_DEBUG_NOTIFICATION_CHANNEL_NAME
import coredevices.ring.service.RecordingBackgroundScope
import coredevices.ring.service.RingSync
import coredevices.ring.service.recordings.RecordingProcessingQueue
import coredevices.util.R
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class PebbleService: Service(), KoinComponent {
    companion object {
        const val NOTIFICATION_CHANNEL_ID = "pebble"
        const val NOTIFICATION_CHANNEL_NAME = "Pebble Service"
        const val ACTION_STOP = "STOP"

        private val logger = Logger.withTag("PebbleService")
    }

    private val satelliteManager: KMPHaversineSatelliteManager by inject()
    private lateinit var notificationManagerCompat: NotificationManagerCompat
    private val scope: RecordingBackgroundScope by inject()
    private var recordingDebugNotificationJob: Job? = null
    private var ringSyncJob: Job? = null
    private val ringSync: RingSync by inject()
    private val pebbleBackgroundManager: PebbleBackgroundManager by inject()
    private val indexNotificationManager: IndexNotificationManager by inject()
    private val recordingProcessingQueue: RecordingProcessingQueue by inject()
    private val commonPrefs: Preferences by inject()
    private var ringObserverJob: Job? = null
    private var firstRingRun: Boolean = true

    private fun handleIntent(intent: Intent) {
        when (intent.action) {
            ACTION_STOP -> {
                logger.i { "Stopping service due to intent request" }
                stopSelf()
            }
        }
    }

    private fun startRecordingDebugNotificationJob() {
        recordingDebugNotificationJob?.cancel()
        recordingDebugNotificationJob = scope.launch {
            val notificationChannel = NotificationChannelCompat.Builder(
                PEBBLE_DEBUG_NOTIFICATION_CHANNEL_ID,
                NotificationManager.IMPORTANCE_DEFAULT)
                .setName(PEBBLE_DEBUG_NOTIFICATION_CHANNEL_NAME)
                .build()
            notificationManagerCompat.createNotificationChannel(notificationChannel)

            indexNotificationManager.startNotificationProcessingJob(scope)
        }
    }

    private fun startRingSyncJob() {
        if (firstRingRun) {
            logger.i { "Starting ring sync job for the first time, resuming pending recording processing tasks" }
            firstRingRun = false
            recordingProcessingQueue.resumePendingTasks()
        }
        if (ringSyncJob?.isActive == true) {
            logger.w { "Ring sync job is already running" }
            return
        }
        ringSyncJob = scope.launch {
            ringSync.startSyncJob(satelliteManager)
        }
    }

    private fun stopRingJobs() {
        runBlocking {
            recordingDebugNotificationJob?.cancelAndJoin()
            recordingDebugNotificationJob = null
            ringSync.stop()
            ringSyncJob?.cancelAndJoin()
            ringSyncJob = null
        }
    }

    private fun observeRingPaired() {
        if (ringObserverJob?.isActive == true) return
        ringObserverJob = commonPrefs.ringPaired
            .map { it != null }
            .distinctUntilChanged()
            .onEach { ringPaired ->
                logger.d { "ringPaired changed: $ringPaired" }
                if (ringPaired) {
                    startRingSyncJob()
                    startRecordingDebugNotificationJob()
                } else {
                    stopRingJobs()
                }
            }
            .launchIn(GlobalScope)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        logger.v { "onStartCommand()" }
        if (intent != null) {
            handleIntent(intent)
        }
        notificationManagerCompat = NotificationManagerCompat.from(this)
        val notificationChannel = NotificationChannelCompat.Builder(
            NOTIFICATION_CHANNEL_ID,
            NotificationManager.IMPORTANCE_MIN)
        .setName(NOTIFICATION_CHANNEL_NAME)
        .build()
        notificationManagerCompat.createNotificationChannel(notificationChannel)

        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Pebble")
            .setContentText("Keeping Pebble connection alive")
            .setOngoing(true)
            .setSmallIcon(R.mipmap.ic_launcher)
            .build()
        ServiceCompat.startForeground(
            this,
            1,
            notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            } else {
                0
            }
        )
        observeRingPaired()
        pebbleBackgroundManager.onServiceStarted()
        return START_STICKY
    }

    override fun onDestroy() {
        pebbleBackgroundManager.onServiceStopped()
        ringObserverJob?.cancel()
        ringObserverJob = null
        stopRingJobs()
        scope.cancel("Service destroyed")
        notificationManagerCompat.cancel(1)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
