package coredevices.ring.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import coredevices.ring.agent.AgentFactory
import coredevices.ring.agent.ChatMode
import coredevices.ring.agent.McpSessionFactory
import coredevices.ring.database.room.repository.McpSandboxRepository
import coredevices.ring.database.room.repository.RecordingRepository
import coredevices.ring.service.recordings.RecordingProcessingQueue
import coredevices.ring.service.recordings.RecordingProcessor
import coredevices.ring.service.recordings.button.RecordingOperationFactory
import coredevices.ring.storage.RecordingStorage
import coredevices.ring.util.AudioRecorder
import coredevices.util.Permission
import coredevices.util.PermissionRequester
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlinx.io.Buffer
import kotlinx.io.buffered
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.component.inject
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

class ListenDialogViewModel(private val dismiss: () -> Unit): ViewModel(), KoinComponent {
    companion object {
        private val logger = Logger.withTag(ListenDialogViewModel::class.simpleName!!)
    }
    sealed class ManualTranscriptionState {
        data object Inactive: ManualTranscriptionState()
        data class Recording(val partial: String? = null): ManualTranscriptionState()
        data object Processing: ManualTranscriptionState()
        data class Error(val message: String): ManualTranscriptionState()
    }
    private val _manualTranscriptionState = MutableStateFlow<ManualTranscriptionState>(
        ManualTranscriptionState.Inactive
    )
    val manualTranscriptionState = _manualTranscriptionState.asStateFlow()
    private var recordingJob: Job? = null
    private val recordingStorage: RecordingStorage by inject()
    private val longLivingScope = CoroutineScope(Dispatchers.Default)
    private var currentRecorder: AudioRecorder? = null
    private val permissionRequester: PermissionRequester by inject()
    private val recordingProcessor: RecordingProcessor by inject()
    private val recordingRepository: RecordingRepository by inject()
    private val mcpSessionFactory: McpSessionFactory by inject()
    private val mcpSandboxRepository: McpSandboxRepository by inject()
    private val recordingProcessingQueue: RecordingProcessingQueue by inject()
    private val agent by lazy {
        get<AgentFactory>().createForChatMode(ChatMode.Normal)
    }

    fun beginManualRecording() {
        //TODO: Replace with recording operation pattern once we have a way to get the state updates out
        recordingJob = viewModelScope.launch {
            val mcpSession = mcpSessionFactory.createForSandboxGroup(
                mcpSandboxRepository.getDefaultGroupId(),
                this
            )
            mcpSession.openSession()
            if (!permissionRequester.hasPermission(Permission.RecordAudio)) {
                onRecordingError("Microphone permission denied")
                return@launch
            }
            val fileName = "manual_recording-${Uuid.random()}"
            currentRecorder = get<AudioRecorder>()
            currentRecorder!!.use { recorder ->
                onRecordingStarted()
                val source = recorder.startRecording()
                val sink = recordingStorage.openRecordingSink(fileName, recorder.sampleRate, "audio/raw")
                withContext(Dispatchers.IO) {
                    source.use {
                        sink.use {
                            source.buffered().transferTo(sink)
                        }
                    }
                }
                recordingProcessingQueue.queueLocalAudioProcessing(fileId = fileName)
                longLivingScope.launch(Dispatchers.IO) { // Persist recording in background, not using viewModelScope to avoid cancellation when viewmodel is cleared
                    recordingStorage.persistRecording(fileName)
                    logger.i { "Persisted recording $fileName" }
                }
                onRecordingCompleted()
            }
            withTimeout(3.seconds) {
                mcpSession.closeSession()
            }
        }
    }

    fun completeManualRecording() {
        logger.d { "completeManualRecording() called" }
        viewModelScope.launch {
            try {
                // Stop the audio recording first
                currentRecorder?.stopRecording()
                logger.d { "stopRecording() completed" }
                
                // The stopping of recording should naturally complete the transcription flow
                // and trigger the AgentRunning status through the InProgressRecording flow
                logger.d { "Manual recording completion requested" }
            } catch (e: Exception) {
                logger.e(e) { "Error during manual recording completion" }
                onRecordingError("Failed to complete recording: ${e.message}")
            }
        }
    }

    private fun onRecordingStarted() {
        _manualTranscriptionState.value = ManualTranscriptionState.Recording()
    }

    private fun onRecordingError(error: String) {
        _manualTranscriptionState.value = ManualTranscriptionState.Error(error)
    }

    private suspend fun onRecordingCompleted() {
        // Yield to allow the current composition frame to complete before dismissing.
        // Dismissing synchronously can dispose the Dialog's UIKitComposeSceneLayer during
        // the parent composition's applyChanges() phase, causing a DepthSortedSet crash on iOS.
        yield()
        dismiss()
    }

    fun cancelManualRecording() {
        viewModelScope.launch {
            recordingJob?.cancelAndJoin()
            onRecordingCompleted()
        }
    }
}