package coredevices.ring.ui.components.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import co.touchlab.kermit.Logger
import coredevices.libindex.di.LibIndexCoroutineScope
import coredevices.ring.service.recordings.RecordingProcessingQueue
import coredevices.ring.storage.RecordingStorage
import coredevices.ring.util.AudioRecorder
import coredevices.util.Permission
import coredevices.util.PermissionRequester
import coredevices.util.PermissionResult
import coredevices.util.rememberUiContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.io.buffered
import org.koin.compose.getKoin
import org.koin.compose.koinInject
import kotlin.uuid.Uuid

/**
 * Self-contained Index ComposeBar — wraps [ChatInput] with recording state
 * (mic permission, audio file capture, queueLocalAudioProcessing) so any
 * screen that needs the prototype's "Type or hold to record…" pill can
 * just drop this in and pass an [onTextSubmit].
 *
 * Lifted out of `FeedTabContents` so the new IndexFeed home + FullFeed
 * (and any future screen) reuses the same battle-tested mic path.
 */
@Composable
fun IndexComposeBarHost(
    modifier: Modifier = Modifier,
    onTextSubmit: ((String) -> Unit)? = null,
    onPermissionDenied: (() -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    val koin = getKoin()
    val appScope = koinInject<LibIndexCoroutineScope>()
    val recordingStorage = koinInject<RecordingStorage>()
    val recordingQueue = koinInject<RecordingProcessingQueue>()
    val permissionRequester = koinInject<PermissionRequester>()
    val uiContext = rememberUiContext()

    var isRecording by remember { mutableStateOf(false) }
    var recordingJob by remember { mutableStateOf<Job?>(null) }
    var currentRecorder by remember { mutableStateOf<AudioRecorder?>(null) }
    var currentFileId by remember { mutableStateOf<String?>(null) }
    val logger = remember { Logger.withTag("IndexComposeBar") }

    fun startRecording() {
        recordingJob = scope.launch {
            if (!permissionRequester.hasPermission(Permission.RecordAudio)) {
                if (uiContext == null) {
                    logger.w { "uiContext null, can't request mic permission" }
                    onPermissionDenied?.invoke()
                    return@launch
                }
                if (permissionRequester.requestPermission(Permission.RecordAudio, uiContext) != PermissionResult.Granted) {
                    logger.w { "Mic permission denied" }
                    onPermissionDenied?.invoke()
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
                    val sink = recordingStorage.openOriginalRecordingSink(fileId, rec.sampleRate, "audio/raw")
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
        // appScope: once the user taps stop, saving + queueing the finished
        // recording must survive this composable leaving composition.
        appScope.launch {
            val fileId = currentFileId ?: return@launch
            currentRecorder?.stopRecording()
            recordingJob?.join()
            logger.i { "Stopped recording, queueing: $fileId" }
            withContext(Dispatchers.IO) {
                val (source, info) = recordingStorage.openRecordingSource(fileId, useOriginalAudio = true)
                val processedSink = recordingStorage.openRecordingSink(
                    fileId, info.cachedMetadata.sampleRate, info.cachedMetadata.mimeType,
                )
                source.use { src ->
                    processedSink.buffered().use { dst ->
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

    ChatInput(
        modifier = modifier,
        isRecording = isRecording,
        onMicClick = ::startRecording,
        onStopClick = ::stopAndProcess,
        onCancelClick = ::cancelRecording,
        onTextSubmit = onTextSubmit,
    )
}
