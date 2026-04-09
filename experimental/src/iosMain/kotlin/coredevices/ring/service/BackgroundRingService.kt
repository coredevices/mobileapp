package coredevices.ring.service

import co.touchlab.kermit.Logger
import coredevices.haversine.KMPHaversineSatelliteManager
import coredevices.ring.database.room.repository.RecordingRepository
import coredevices.ring.service.recordings.RecordingProcessingQueue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import kotlin.time.Instant

class BackgroundRingService(
    private val ringSync: RingSync,
    private val satelliteManager: KMPHaversineSatelliteManager,
    private val recordingProcessingQueue: RecordingProcessingQueue,
    private val indexNotificationManager: IndexNotificationManager,
    private val scope: RecordingBackgroundScope,
) {
    private var ringSyncJob: Job? = null
    private val logger = Logger.withTag("BackgroundRingService")

    private val _isRunning = MutableStateFlow(ringSyncJob?.isActive == true)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()
    private var recordingDebugNotificationJob: Job? = null
    private var firstRun: Boolean = true

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun startRecordingDebugNotificationJob() {
        recordingDebugNotificationJob?.cancel()
        recordingDebugNotificationJob = scope.launch {
            indexNotificationManager.startNotificationProcessingJob(scope)
        }
    }

    fun startRingSyncJob() {
        if (firstRun) {
            logger.i { "Starting ring sync job for the first time, resuming pending recording processing tasks" }
            firstRun = false
            recordingProcessingQueue.resumePendingTasks()
        }
        ringSync.startSyncJob(satelliteManager)
        startRecordingDebugNotificationJob()
    }

    fun stopRingSyncJob() {
        logger.i { "Stopping ring sync job" }
        scope.launch {
            ringSync.stop()
            recordingDebugNotificationJob?.cancel()
            recordingDebugNotificationJob = null
        }
    }
}