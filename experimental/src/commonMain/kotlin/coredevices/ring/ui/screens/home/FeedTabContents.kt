package coredevices.ring.ui.screens.home

import CoreNav
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateBounds
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.unit.dp
import co.touchlab.kermit.Logger
import coredevices.pebble.ui.TopBarParams
import coredevices.ring.service.RingEvent
import coredevices.ring.service.RingSync
import coredevices.ring.service.recordings.RecordingProcessingQueue
import coredevices.ring.storage.RecordingStorage
import coredevices.ring.ui.components.chat.ChatInput
import coredevices.ring.ui.components.feed.FeedList
import coredevices.ring.ui.components.feed.ProgressChip
import coredevices.ring.ui.navigation.RingRoutes
import coredevices.ring.util.AudioRecorder
import coredevices.util.Permission
import coredevices.util.PermissionRequester
import coredevices.util.PermissionResult
import coredevices.util.rememberUiContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.io.buffered
import org.koin.compose.getKoin
import org.koin.compose.koinInject
import kotlin.time.Duration.Companion.milliseconds
import kotlin.uuid.Uuid

@Composable
fun FeedTabContents(
    topBarParams: TopBarParams?,
    windowInsets: PaddingValues,
    coreNav: CoreNav,
    onAddItem: () -> Unit = {},
    onAddChat: (String) -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val koin = getKoin()
    val recordingStorage = koinInject<RecordingStorage>()
    val recordingQueue = koinInject<RecordingProcessingQueue>()
    val permissionRequester = koinInject<PermissionRequester>()
    var isRecording by remember { mutableStateOf(false) }
    var recordingJob by remember { mutableStateOf<Job?>(null) }
    var currentRecorder by remember { mutableStateOf<AudioRecorder?>(null) }
    var currentFileId by remember { mutableStateOf<String?>(null) }
    val logger = remember { Logger.withTag("FeedMicRecording") }
    val uiContext = rememberUiContext()

    fun startRecording() {
        recordingJob = scope.launch {
            if (!permissionRequester.hasPermission(Permission.RecordAudio)) {
                if (permissionRequester.requestPermission(Permission.RecordAudio, uiContext!!) != PermissionResult.Granted) {
                    logger.w { "Microphone permission denied" }
                    topBarParams?.showSnackbar("Microphone permission is required")
                    return@launch
                }
            }
            val fileId = "manual_recording-${Uuid.random()}"
            currentFileId = fileId
            val recorder = koin.get<AudioRecorder>()
            currentRecorder = recorder
            isRecording = true
            logger.i { "Started recording: $fileId" }
            try {
                recorder.use { rec ->
                    val source = rec.startRecording()
                    val sink = recordingStorage.openRecordingSink(fileId, rec.sampleRate, "audio/raw")
                    withContext(Dispatchers.IO) {
                        source.use {
                            sink.use {
                                source.buffered().transferTo(sink)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                logger.e(e) { "Recording error: ${e.message}" }
            } finally {
                isRecording = false
                currentRecorder = null
            }
        }
    }

    fun stopAndProcess() {
        scope.launch {
            val fileId = currentFileId ?: return@launch
            currentRecorder?.stopRecording()
            recordingJob?.join()
            logger.i { "Stopped recording, saving clean copy and queueing: $fileId" }
            // Save original as -clean before preprocessing overwrites the raw file
            // (same pattern as Ring recordings in RingSync)
            withContext(Dispatchers.IO) {
                val (source, info) = recordingStorage.openRecordingSource(fileId)
                val cleanSink = recordingStorage.openCleanRecordingSink(
                    fileId, info.cachedMetadata.sampleRate, info.cachedMetadata.mimeType
                )
                source.use { src ->
                    cleanSink.buffered().use { dst ->
                        src.transferTo(dst)
                    }
                }
            }
            recordingQueue.queueLocalAudioProcessing(fileId = fileId)
            currentFileId = null
        }
    }

    fun cancelRecording() {
        scope.launch {
            val fileId = currentFileId
            currentRecorder?.stopRecording()
            recordingJob?.cancelAndJoin()
            logger.i { "Cancelled recording${if (fileId != null) ": $fileId" else ""}" }
            isRecording = false
            currentRecorder = null
            currentFileId = null
        }
    }

    Column(modifier = Modifier.padding(bottom = windowInsets.calculateBottomPadding()).fillMaxSize()) {
        LookaheadScope {
            FeedList(
                topBarParams = topBarParams,
                modifier = Modifier
                    .weight(1f)
                    .animateBounds(this@LookaheadScope),
                onItemSelected = { id ->
                    coreNav.navigateTo(RingRoutes.RecordingDetails(id))
                },
            )
            RingSyncIndicator(modifier = Modifier.align(Alignment.End).padding(8.dp))
        }
        Box(modifier = Modifier.padding(8.dp).fillMaxWidth()) {
            ChatInput(
                modifier = Modifier.fillMaxWidth(),
                isRecording = isRecording,
                onMicClick = ::startRecording,
                onStopClick = ::stopAndProcess,
                onCancelClick = ::cancelRecording,
                onTextSubmit = onAddChat
            )
        }
    }
}

@OptIn(FlowPreview::class)
@Composable
fun RingSyncIndicator(modifier: Modifier = Modifier) {
    val status = koinInject<RingSync>().ringEvents
        .filterIsInstance<RingEvent.Transfer>()
        .collectAsState(null)
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(status.value) {
        status.value.let { status ->
            if (status !is RingEvent.Transfer.InProgress || status.progress >= 1.0f) {
                delay(500.milliseconds)
                show = false
            } else {
                show = true
            }
        }
    }
    AnimatedVisibility(
        visible = show,
        modifier = modifier,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        val progress = (status.value as? RingEvent.Transfer.InProgress)?.progress ?: 0f
        ProgressChip(
            text = "Syncing",
            progress = progress,
        )
    }
}
