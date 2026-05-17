package coredevices.coreapp

import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
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
import coredevices.ring.storage.RecordingStorage
import coredevices.ring.util.AudioRecorder
import coredevices.util.R
import kotlinx.coroutines.Dispatchers
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
import kotlinx.coroutines.withContext
import kotlinx.io.buffered
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.component.inject
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class PebbleService: Service(), KoinComponent {
    companion object {
        const val NOTIFICATION_CHANNEL_ID = "pebble"
        const val NOTIFICATION_CHANNEL_NAME = "Pebble Service"
        const val ACTION_STOP = "STOP"
        const val ACTION_START_RECORDING = "coredevices.coreapp.ACTION_START_RECORDING"
        const val ACTION_STOP_RECORDING = "coredevices.coreapp.ACTION_STOP_RECORDING"

        private val VIBRATE_START = longArrayOf(0, 100)
        private val VIBRATE_STOP = longArrayOf(0, 80, 60, 80)

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
    private val recordingStorage: RecordingStorage by inject()
    private val commonPrefs: Preferences by inject()
    private var ringObserverJob: Job? = null
    private var firstRingRun: Boolean = true
    private var phoneRecordingJob: Job? = null
    private var currentPhoneRecorder: AudioRecorder? = null
    private var currentPhoneFileId: String? = null

    @OptIn(ExperimentalUuidApi::class)
    private fun handleIntent(intent: Intent) {
        when (intent.action) {
            ACTION_STOP -> {
                logger.i { "Stopping service due to intent request" }
                stopSelf()
            }
            ACTION_START_RECORDING -> {
                if (phoneRecordingJob?.isActive == true) {
                    logger.w { "Phone recording already in progress, ignoring start" }
                    return
                }
                val fileId = "manual_recording-${Uuid.random()}"
                currentPhoneFileId = fileId
                logger.i { "Starting phone recording: $fileId" }
                vibrate(VIBRATE_START)
                phoneRecordingJob = scope.launch {
                    val recorder = get<AudioRecorder>()
                    currentPhoneRecorder = recorder
                    try {
                        recorder.use { rec ->
                            val source = rec.startRecording()
                            val sink = recordingStorage.openRecordingSink(fileId, rec.sampleRate, "audio/raw")
                            withContext(Dispatchers.IO) {
                                source.use { sink.use { source.buffered().transferTo(sink) } }
                            }
                        }
                    } finally {
                        currentPhoneRecorder = null
                    }
                }
            }
            ACTION_STOP_RECORDING -> {
                val fileId = currentPhoneFileId ?: run {
                    logger.w { "No phone recording in progress, ignoring stop" }
                    return
                }
                logger.i { "Stopping phone recording: $fileId" }
                scope.launch {
                    currentPhoneRecorder?.stopRecording()
                    phoneRecordingJob?.join()
                    withContext(Dispatchers.IO) {
                        val (source, info) = recordingStorage.openRecordingSource(fileId)
                        val cleanSink = recordingStorage.openCleanRecordingSink(
                            fileId, info.cachedMetadata.sampleRate, info.cachedMetadata.mimeType
                        )
                        source.use { src -> cleanSink.buffered().use { dst -> src.transferTo(dst) } }
                    }
                    recordingProcessingQueue.queueLocalAudioProcessing(fileId = fileId)
                    currentPhoneFileId = null
                    vibrate(VIBRATE_STOP)
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun vibrate(pattern: LongArray) {
        getSystemService(Vibrator::class.java)
            ?.vibrate(VibrationEffect.createWaveform(pattern, -1))
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
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
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
