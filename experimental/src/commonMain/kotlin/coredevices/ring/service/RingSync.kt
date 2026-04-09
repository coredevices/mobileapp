package coredevices.ring.service

import co.touchlab.kermit.Logger
import com.juul.kable.Bluetooth
import coredevices.analytics.CoreAnalytics
import coredevices.haversine.DataDecodeException
import coredevices.haversine.KMPHaversineSatellite
import coredevices.haversine.KMPHaversineSatelliteManager
import coredevices.haversine.SatelliteStatus
import coredevices.haversine.TransferStatus
import coredevices.haversine.removeDCBias
import coredevices.util.transcription.TranscriptionService
import coredevices.resampler.Resampler
import coredevices.ring.data.entity.room.RingTransferStatus
import coredevices.ring.data.entity.room.TraceEventData
import coredevices.ring.database.Preferences
import coredevices.ring.database.room.repository.RingTransferRepository
import coredevices.ring.service.recordings.RecordingProcessingQueue
import coredevices.ring.storage.RecordingStorage
import coredevices.ring.util.trace.RingTraceSession
import coredevices.util.Platform
import coredevices.util.isIOS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import kotlinx.io.writeShortLe
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToInt
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

private suspend fun waitForBluetoothAvailable() {
    Bluetooth.availability.first { it is Bluetooth.Availability.Available }
}

expect fun onPlayPause()
expect fun onNextTrack()

sealed interface RingEvent {
    val ringId: String
    abstract class Transfer : RingEvent {
        data class InProgress(
            override val ringId: String,
            val transferId: Long?,
            val progress: Float
        ) : Transfer()

        data class Failure(
            override val ringId: String,
            val transferId: Long?,
            val collectionIndex: Int?
        ) : Transfer()
    }

    interface FirmwareUpdate: RingEvent {
        val newVersion: String
        val isFailsafe: Boolean
        data class Started(
            override val ringId: String,
            override val newVersion: String,
            override val isFailsafe: Boolean
        ) : FirmwareUpdate

        data class Failed(
            override val ringId: String,
            override val newVersion: String,
            override val isFailsafe: Boolean
        ) : FirmwareUpdate

        data class Success(
            override val ringId: String,
            override val newVersion: String,
            override val isFailsafe: Boolean
        ) : FirmwareUpdate
    }
}

class RingSync(
    private val prefs: Preferences,
    private val recordingStorage: RecordingStorage,
    private val buttonSequenceRecorder: IndexButtonSequenceRecorder,
    private val buttonActionHandler: IndexButtonActionHandler,
    private val recordingProcessingQueue: RecordingProcessingQueue,
    private val indexNotificationManager: IndexNotificationManager,
    private val ringTransferRepository: RingTransferRepository,
    private val coreAnalytics: CoreAnalytics,
    private val scope: RecordingBackgroundScope,
    private val trace: RingTraceSession,
): KoinComponent {
    companion object {
        private val logger = Logger.withTag("RingSync")
        const val TARGET_SAMPLE_RATE = 16000
        private val SCAN_INTERVAL = 3.seconds
        val SATELLITE_HW_VER = Pair(11, 0)
        val badCollectionsDir: Path = Path(SystemTemporaryDirectory, "bad_collections").also {
            SystemFileSystem.createDirectories(it, mustCreate = false)
        }
    }
    private var syncJob: Job? = null
    private val _lastRing: MutableStateFlow<KMPHaversineSatellite?> = MutableStateFlow(null)
    val lastRing = _lastRing.asStateFlow()

    private fun resample(samples: ShortArray, sampleRate: Int): ShortArray {
        val resampler = Resampler(sampleRate, TARGET_SAMPLE_RATE)
        return resampler.process(samples)
    }

    private fun handleButtonPressSequence(sequence: String?) {
        sequence?.let { buttonSequenceRecorder.recordSequence(sequence) } ?:
            buttonSequenceRecorder.recordNoSequence()
    }

    private val _ringEvents = MutableSharedFlow<RingEvent>(replay = 1, extraBufferCapacity = 50, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val ringEvents = _ringEvents.asSharedFlow()
    private val _lifetimeTransferCount = MutableStateFlow(null)
    val lifetimeTransferCount = _lifetimeTransferCount.asStateFlow()

    private fun logTransferEvent(
        latency: Long?,
        rssi: Int?,
        serialNumber: String?,
        audioDuration: Duration,
        transferStartIndex: Int,
        transferEndIndex: Int,
    ) {
        coreAnalytics.logEvent(
            "ring.transfer_complete",
            buildMap {
                put("ring_serial", serialNumber ?: "<none>")
                put("recording_duration_ms", audioDuration.inWholeMilliseconds)
                latency?.let {
                    put("adv_to_button_press_latency_ms", latency)
                }
                rssi?.let {
                    put("rssi", rssi)
                }
                put("transfer_start_index", transferStartIndex)
                put("transfer_end_index_inclusive", transferEndIndex)
            }
        )
    }

    private fun saveBadCollectionData(data: ByteArray): String {
        val filename = "bad_collection_${Clock.System.now()}.bin"
        scope.launch(Dispatchers.IO) {
            SystemFileSystem.sink(Path(SystemTemporaryDirectory, "bad_collections", filename)).buffered().use {
                it.write(data)
            }
        }
        return filename
    }

    fun startSyncJob(satelliteManager: KMPHaversineSatelliteManager) {
        logger.d { "startSyncJob()" }
        syncJob?.cancel()
        if (get<Platform>().isIOS) {
            //XXX: Pre-initialize transcription service to reduce latency on first use for demo
            val transcriptionService = get<TranscriptionService>()
            transcriptionService.earlyInit()
        }

        syncJob = scope.launch {
            launch {
                buttonActionHandler.handleButtonActions()
            }
            launch {
                indexNotificationManager.processRingSyncTransferNotifications(ringEvents)
            }
            satelliteManager.lastRing.onEach {
                _lastRing.value = it
            }.launchIn(this)
            prefs.ringPaired.collectLatest { paired ->
                if (paired != null) {
                    var lastIdx: Int = -1
                    var transferRange: IntRange? = null
                    logger.d { "Paired is true, starting scan/sync job" }
                    while (isActive) {
                        logger.d { "Waiting for Bluetooth to become available..." }
                        trace.markEvent("wait_for_bluetooth")
                        waitForBluetoothAvailable()
                        trace.markEvent("bluetooth_available")
                        logger.d { "Bluetooth is available, starting Ring sync job" }
                        try {
                            satelliteManager.startScanning()
                                .flowOn(Dispatchers.IO)
                                .catch {
                                    logger.e(it) { "Error during satellite scanning: ${it.message}" }
                                }.collect { satelliteStatus ->
                                    val t = Clock.System.now()
                                    when (satelliteStatus) {
                                        is SatelliteStatus.Transferring -> {
                                            logger.d { "Status ${satelliteStatus.transferStatus} $t lastRSSI = ${satelliteStatus.satellite.lastAdvertisement?.rssi}" }
                                            val transferStatus = satelliteStatus.transferStatus
                                            if (transferStatus is TransferStatus.TransferComplete) {
                                                withContext(Dispatchers.IO) {
                                                    removeDCBias(transferStatus.samples)
                                                }
                                            }
                                            try {
                                                var id: String? = null
                                                when (transferStatus) {
                                                    is TransferStatus.TransferStarted -> {
                                                        logger.i { "Transfer started for ${transferStatus.satellite.id}" }
                                                        trace.markEvent("transfer_started",
                                                            TraceEventData.TransferStarted(
                                                                transferStatus.satellite.id,
                                                                transferStatus.rollover
                                                            )
                                                        )
                                                        if (transferStatus.rollover) {
                                                            logger.i { "Rollover detected, marking previous transfers as old index iteration" }
                                                            withContext(Dispatchers.IO) {
                                                                ringTransferRepository.markTransfersAsPreviousIndexIteration()
                                                            }
                                                        }
                                                        transferRange = if (transferRange == null) {
                                                            transferStatus.willTransferRange
                                                        } else {
                                                            // Extend the range to include the new end
                                                            transferRange!!.first..transferStatus.willTransferRange.last
                                                        }
                                                        trace.markEvent("stt_early_init_start")
                                                        val transcriptionService =
                                                            get<TranscriptionService>()
                                                        launch {
                                                            if (transcriptionService.onInitialized.receive()) {
                                                                trace.markEvent("stt_early_init_success")
                                                            } else {
                                                                trace.markEvent("stt_early_init_failed")
                                                            }
                                                        }
                                                        transcriptionService.earlyInit()
                                                    }

                                                    is TransferStatus.TransferTypeDetermined -> {
                                                        trace.markEvent("transfer_type_determined",
                                                            TraceEventData.TransferTypeDetermined(
                                                                satellite = transferStatus.satellite.id,
                                                                isAudio = transferStatus.isAudio,
                                                                buttonSequence = transferStatus.buttonSequence,
                                                                collectionStartIndex = transferStatus.collectionStartIndex,
                                                                collectionIndex = transferStatus.collectionIndex,
                                                                final = transferStatus.final,
                                                                advertisementReceivedTimestamp = transferStatus.advertisementReceivedTimestamp,
                                                                lifetimeCollectionCount = transferStatus.lifetimeCollectionCount?.toInt()
                                                            )
                                                        )
                                                        logger.i { "Transfer type determined for ${transferStatus.collectionIndex}: collectionStartIndex = ${transferStatus.collectionStartIndex}, isAudio = ${transferStatus.isAudio}, sequence = ${transferStatus.buttonSequence}, final = ${transferStatus.final}" }
                                                        logger.i { "Lifetime collection count: ${transferStatus.lifetimeCollectionCount}" }
                                                        transferStatus.satellite.state.value?.serialNumber?.let { serial ->
                                                            transferStatus.lifetimeCollectionCount?.let { count ->
                                                                coreAnalytics.updateRingLifetimeCollectionCount(serial, count.toInt())
                                                            } ?: logger.w {
                                                                "No lifetime collection count available to update for serial $serial"
                                                            }
                                                        } ?: logger.w {
                                                            "No serial number available in satellite state to update lifetime collection count"
                                                        }
                                                        if (transferStatus.collectionStartIndex == null || transferStatus.final) { // skip handling button presses for long transfers until they're final
                                                            handleButtonPressSequence(transferStatus.buttonSequence)
                                                        } else {
                                                            handleButtonPressSequence(null)
                                                        }

                                                        if (transferStatus.isAudio) {
                                                            val idx =
                                                                transferStatus.collectionStartIndex
                                                                    ?: transferStatus.collectionIndex
                                                            if (lastIdx != idx) {
                                                                logger.d { "$lastIdx != $idx, creating new transfer entry" }
                                                                val transfer = withContext(Dispatchers.IO) {
                                                                    ringTransferRepository.getLastValidTransferByStartIndex(
                                                                        idx
                                                                    )
                                                                }
                                                                transfer?.let {
                                                                    // If we never received any data for this transfer, mark it as failed
                                                                    if (transfer.status == RingTransferStatus.Started) {
                                                                        trace.markEvent("past_transfer_failed",
                                                                            TraceEventData.PastTransferFailed(
                                                                                satellite = transferStatus.satellite.id,
                                                                                transferId = transfer.id
                                                                            )
                                                                        )
                                                                        withContext(Dispatchers.IO) {
                                                                            logger.i {
                                                                                "Seeing started transfer for current idx, marking past transfer " +
                                                                                        "${transfer.id} (start idx ${transfer.transferInfo?.collectionStartIndex}) as failed"
                                                                            }
                                                                            ringTransferRepository.updateTransferStatus(
                                                                                transfer.id,
                                                                                RingTransferStatus.Failed
                                                                            )
                                                                        }
                                                                    }
                                                                }

                                                                lastIdx = idx
                                                                val id = withContext(Dispatchers.IO) {
                                                                    ringTransferRepository.createRingTransfer(
                                                                        advertisementReceived = transferStatus.advertisementReceivedTimestamp,
                                                                        startIndex = idx,
                                                                        endIndex = if (transferStatus.final) idx else null,
                                                                    )
                                                                }
                                                                logger.d {
                                                                    "Created new ring transfer with id $id for start index $idx"
                                                                }
                                                            }
                                                        }
                                                    }

                                                    is TransferStatus.TransferFailed -> {
                                                        transferRange = null
                                                        trace.markEvent("transfer_dropped_recoverable",
                                                            TraceEventData.TransferDroppedRecoverable(
                                                                satellite = transferStatus.satellite.id,
                                                                collectionIndex = transferStatus.collectionIndex,
                                                            )
                                                        )
                                                        logger.e(transferStatus.exception) { "Transfer dropped: ${transferStatus.collectionIndex}" }
                                                    }

                                                    is TransferStatus.IrrecoverableDataDetected -> {
                                                        transferRange = null
                                                        logger.e(transferStatus.exception) {
                                                            buildString {
                                                                val e = transferStatus.exception
                                                                append("Irrecoverable data detected for ${transferStatus.satellite.id}: ${transferStatus.exception?.message}")
                                                                if (e is DataDecodeException) {
                                                                    e.data?.let {
                                                                        val filename = saveBadCollectionData(it)
                                                                        append(" (invalid data size = ${e.data?.size} bytes, will save to $filename)")
                                                                    } ?: append(" (no data available to persist)")
                                                                }
                                                            }
                                                        }
                                                        val tid = withContext(Dispatchers.IO) {
                                                            val transfer = ringTransferRepository.getLastValidTransferByStartIndex(
                                                                transferStatus.collection?.startIndex
                                                                    ?: -1
                                                            )
                                                            transfer?.let {
                                                                ringTransferRepository.updateTransferStatus(
                                                                    transfer.id,
                                                                    RingTransferStatus.Failed
                                                                )
                                                            } ?: logger.w {
                                                                "No pending transfer found for irrecoverable transfer w/ start index ${transferStatus.collection?.startIndex}."
                                                            }
                                                            transfer?.id
                                                        }
                                                        trace.markEvent("transfer_dropped_unrecoverable",
                                                            TraceEventData.TransferDroppedUnrecoverable(
                                                                satellite = transferStatus.satellite.id,
                                                                transferId = tid,
                                                                indices = transferStatus.collection?.indices?.toList()
                                                            )
                                                        )
                                                        _ringEvents.emit(
                                                            RingEvent.Transfer.Failure(
                                                                ringId = transferStatus.satellite.id,
                                                                transferId = tid,
                                                                collectionIndex = transferStatus.collection?.startIndex
                                                            )
                                                        )
                                                        sendBugReportPrompt()
                                                    }

                                                    is TransferStatus.TransferInProgress -> {
                                                        val range = transferRange
                                                        if (range != null) {
                                                            val progress =
                                                                (transferStatus.currentCollectionIndex - range.first).toFloat() /
                                                                        (range.last - range.first + 1).toFloat()
                                                            logger.d {
                                                                "Transfer in progress for ${transferStatus.satellite.id}, index ${transferStatus.currentCollectionIndex - range.first} / ${range.last - range.first + 1}, progress: $progress"
                                                            }
                                                            trace.markEvent("transfer_progress",
                                                                TraceEventData.TransferProgress(
                                                                    transferId = withContext(Dispatchers.IO) {
                                                                        ringTransferRepository.getLastValidTransferByStartIndex(
                                                                            transferStatus.collectionStartIndex
                                                                        )?.id ?: -1L
                                                                    },
                                                                    startIndex = range.first,
                                                                    endIndex = range.last,
                                                                    reportedProgress = progress
                                                                )
                                                            )
                                                            _ringEvents.emit(
                                                                RingEvent.Transfer.InProgress(
                                                                    ringId = transferStatus.satellite.id,
                                                                    transferId = withContext(Dispatchers.IO) {
                                                                        ringTransferRepository.getLastValidTransferByStartIndex(
                                                                            transferStatus.collectionStartIndex
                                                                        )?.id
                                                                    },
                                                                    progress = progress
                                                                )
                                                            )
                                                        }
                                                    }

                                                    is TransferStatus.TransferComplete -> {
                                                        val range = transferRange
                                                        trace.markEvent("transfer_completed",
                                                            TraceEventData.TransferCompleted(
                                                                transferId = withContext(Dispatchers.IO) {
                                                                    ringTransferRepository.getLastValidTransferByStartIndex(
                                                                        transferStatus.collectionStartCount.toInt()
                                                                    )?.id ?: -1L
                                                                },
                                                                audioDurationSeconds = transferStatus.samples.size / transferStatus.sampleRate.toFloat(),
                                                                buttonReleaseTimestamp = transferStatus.buttonReleaseTimestamp
                                                            )
                                                        )
                                                        if (range != null) {
                                                            val progress =
                                                                (transferStatus.collectionIndex - range.first + 1).toFloat() /
                                                                        (range.last - range.first + 1).toFloat()
                                                            _ringEvents.emit(
                                                                RingEvent.Transfer.InProgress(
                                                                    ringId = transferStatus.satellite.id,
                                                                    transferId = withContext(Dispatchers.IO) {
                                                                        ringTransferRepository.getLastValidTransferByStartIndex(
                                                                            transferStatus.collectionStartCount.toInt()
                                                                        )?.id
                                                                    },
                                                                    progress = progress
                                                                )
                                                            )
                                                        }
                                                        if (transferStatus.collectionIndex == transferRange?.last) {
                                                            logger.d { "Transfer complete index matches expected end index ${transferRange?.last}" }
                                                            transferRange = null
                                                        }
                                                        val audioDuration =
                                                            transferStatus.samples.size / transferStatus.sampleRate.toDouble()
                                                        val ringRxIndex =
                                                            transferStatus.collectionStartCount.toInt()
                                                                .takeIf { v -> v >= 0 }
                                                                ?: transferStatus.collectionIndex
                                                        val buttonReleaseTimestamp =
                                                            transferStatus.buttonReleaseTimestamp
                                                        val transferCompleteTimestamp =
                                                            transferStatus.transferCompleteTimestamp
                                                        logger.i {
                                                            "Transfer complete for ${transferStatus.satellite.id}, " +
                                                                    "button release: $buttonReleaseTimestamp, " +
                                                                    "transfer complete: $transferCompleteTimestamp, " +
                                                                    "audio duration: $audioDuration seconds"
                                                        }
                                                        withContext(Dispatchers.IO) {
                                                            coreAnalytics.updateRingTransferDurationMetric(
                                                                audioDuration.seconds
                                                            )
                                                        }
                                                        val transfer = withContext(Dispatchers.IO) {
                                                            ringTransferRepository.getLastValidTransferByStartIndex(
                                                                ringRxIndex
                                                            )
                                                        }
                                                        val latency = buttonReleaseTimestamp
                                                            ?.let {
                                                                val pressT = it - audioDuration.seconds
                                                                transfer?.transferInfo?.advertisementReceived?.let { advT ->
                                                                    (advT - pressT.toEpochMilliseconds())
                                                                }
                                                            }
                                                        logTransferEvent(
                                                            latency,
                                                            transferStatus.satellite.lastAdvertisement?.rssi?.roundToInt(),
                                                            transferStatus.satellite.state.value?.serialNumber,
                                                            audioDuration.seconds,
                                                            transferStatus.collectionStartCount.toInt(),
                                                            transferStatus.collectionIndex
                                                        )
                                                        transfer ?: error("Expected to find existing transfer for start index $ringRxIndex")
                                                        val transferInfo = transfer.transferInfo!!.copy(
                                                            collectionEndIndex = transferStatus.collectionIndex,
                                                            buttonPressed = buttonReleaseTimestamp?.let { it - audioDuration.seconds }?.toEpochMilliseconds(),
                                                            buttonReleased = buttonReleaseTimestamp?.toEpochMilliseconds(),
                                                            transferCompleted = transferCompleteTimestamp.toEpochMilliseconds(),
                                                            buttonReleaseAdvertisementLatencyMs = buttonReleaseTimestamp
                                                                ?.let { transfer.transferInfo.advertisementReceived - it.toEpochMilliseconds() },
                                                        )
                                                        withContext(Dispatchers.IO) {
                                                            ringTransferRepository.updateTransferInfo(
                                                                transfer.id,
                                                                transferInfo
                                                            )
                                                            logger.d { "Saving transfer..." }
                                                            id = "ring_${transferStatus.satellite.id}-${transferStatus.collectionIndex}-${Uuid.random()}"
                                                            if (audioDuration >= 0.5) {
                                                                trace.markEvent("saving_recording_start")
                                                                val samplesResampled = resample(
                                                                    transferStatus.samples,
                                                                    transferStatus.sampleRate.toInt()
                                                                )
                                                                val nwSampleRate = TARGET_SAMPLE_RATE
                                                                listOf(
                                                                    async(Dispatchers.IO) {
                                                                        recordingStorage.openRecordingSink(
                                                                            id,
                                                                            nwSampleRate,
                                                                            "audio/raw"
                                                                        ).use { sink ->
                                                                            samplesResampled.forEach(sink::writeShortLe)
                                                                        }
                                                                    },
                                                                    async(Dispatchers.IO) {
                                                                        recordingStorage.openCleanRecordingSink(
                                                                            id,
                                                                            transferStatus.sampleRate.toInt(),
                                                                            "audio/raw"
                                                                        ).use { sink ->
                                                                            transferStatus.samples.forEach(
                                                                                sink::writeShortLe
                                                                            )
                                                                        }
                                                                    }
                                                                ).awaitAll()
                                                                trace.markEvent("saving_recording_end")

                                                                withContext(Dispatchers.IO) {
                                                                    ringTransferRepository.markTransferCompleteAndSetFileId(
                                                                        transfer.id,
                                                                        id
                                                                    )
                                                                    recordingProcessingQueue.queueAudioProcessing(
                                                                        transfer.id,
                                                                        transferStatus.buttonSequence,
                                                                    )
                                                                }
                                                            } else {
                                                                logger.i { "Discarding transfer due to short duration: $audioDuration seconds" }
                                                                trace.markEvent("transfer_discarded")
                                                                withContext(Dispatchers.IO) {
                                                                    ringTransferRepository.updateTransferStatus(transfer.id, RingTransferStatus.Discarded)
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            } catch (e: Exception) {
                                                if (e is CancellationException) throw e
                                                logger.e(e) { "Error during transfer: ${e.message}" }
                                                sendBugReportPrompt()
                                            }
                                        }

                                        is SatelliteStatus.FirmwareUpdating -> {
                                            val isFailsafe = satelliteStatus.satellite.state.value?.isInFailsafeMode ?: false
                                            when (satelliteStatus) {
                                                is SatelliteStatus.FirmwareUpdating.Started -> {
                                                    logger.i {
                                                        "Satellite ${satelliteStatus.satellite.id} started firmware update to version ${satelliteStatus.newVersion} isFailsafe = $isFailsafe"
                                                    }
                                                    if (isFailsafe && transferRange != null) {
                                                        logger.e {
                                                            "Satellite is in failsafe mode but we have an active transfer range, transfer might be interrupted. Marking current transfer as failed and clearing transfer range."
                                                        }
                                                        withContext(Dispatchers.IO) {
                                                            val pending = ringTransferRepository.getPendingTransfersByRange(transferRange!!)
                                                            pending.forEach { transfer ->
                                                                ringTransferRepository.updateTransferStatus(transfer.id, RingTransferStatus.Failed)
                                                            }
                                                            if (pending.isNotEmpty()) {
                                                                logger.w {
                                                                    "Marked ${pending.size} transfers as failed due to satellite entering failsafe mode during active transfer. Transfer range was ${transferRange!!.first} to ${transferRange!!.last}"
                                                                }
                                                                sendBugReportPrompt()
                                                            }
                                                        }
                                                        transferRange = null
                                                    }
                                                    _ringEvents.emit(
                                                        RingEvent.FirmwareUpdate.Started(
                                                            ringId = satelliteStatus.satellite.id,
                                                            newVersion = satelliteStatus.newVersion,
                                                            isFailsafe = isFailsafe
                                                        )
                                                    )
                                                }

                                                is SatelliteStatus.FirmwareUpdating.Success -> {
                                                    logger.i {
                                                        "Satellite ${satelliteStatus.satellite.id} firmware update to version ${satelliteStatus.newVersion} succeeded"
                                                    }
                                                    _ringEvents.emit(
                                                        RingEvent.FirmwareUpdate.Success(
                                                            ringId = satelliteStatus.satellite.id,
                                                            newVersion = satelliteStatus.newVersion,
                                                            isFailsafe = isFailsafe
                                                        )
                                                    )
                                                }

                                                is SatelliteStatus.FirmwareUpdating.Failed -> {
                                                    logger.e {
                                                        "Satellite ${satelliteStatus.satellite.id} firmware update to version ${satelliteStatus.newVersion} failed"
                                                    }
                                                    _ringEvents.emit(
                                                        RingEvent.FirmwareUpdate.Failed(
                                                            ringId = satelliteStatus.satellite.id,
                                                            newVersion = satelliteStatus.newVersion,
                                                            isFailsafe = isFailsafe,
                                                        )
                                                    )
                                                }
                                            }
                                        }

                                        is SatelliteStatus.ProgrammingUserId -> {
                                            logger.i {
                                                "Satellite ${satelliteStatus.satellite.id} programming user ID"
                                            }
                                        }
                                    }
                                    //logger.d { "Handled satellite status ${satelliteStatus::class.simpleName} in $dur" }
                                }
                            delay(SCAN_INTERVAL)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            logger.e(e) { "Error in Ring sync collector: ${e.message}" }
                        }
                    }
                } else {
                    logger.d { "Paired is false" }
                }
            }
        }
    }

    fun sendBugReportPrompt() {
        indexNotificationManager.sendBugReportPrompt(
            "Index ran into a problem",
            """
                We detected a data transfer error with a recent recording from your Index 01.
                Sorry about that! Please help us improve by sending a bug report.
            """.trimIndent(),
        )
    }

    suspend fun stop() {
        syncJob?.cancelAndJoin()
    }

    fun lastRingSummary(): String? = lastRing.value?.let {
        val state = it.state.value
        buildString {
            appendLine()
            appendLine("Ring Summary")
            appendLine("ID: ${it.id}")
            appendLine("Serial: ${state?.serialNumber}")
            appendLine("Name: ${it.name}")
            appendLine("Last Seen: ${it.lastAdvertisement?.timestamp}")
            appendLine("Last RSSI: ${it.lastAdvertisement?.rssi}")
            appendLine("isInCollectionState: ${state?.isInCollectionState}")
            appendLine("isNearby: ${state?.isNearby}")
            appendLine("isInFailsafeMode: ${state?.isInFailsafeMode}")
            appendLine("firmwareVersion: ${state?.firmwareVersion}")
            appendLine("truncatedCollectionCount: ${state?.truncatedCollectionCount}")
        }
    }
}
