package coredevices.util.transcription

import co.touchlab.kermit.Logger
import com.cactus.CactusInitParams
import com.cactus.CactusSTT
import com.cactus.CactusTranscriptionParams
import com.cactus.CactusTranscriptionResult
import com.cactus.TranscriptionMode
import com.cactus.WisprConversationContext
import com.cactus.WisprConversationMessage
import com.cactus.WisprFlowConfig
import coredevices.util.AudioEncoding
import coredevices.util.CommonBuildKonfig
import coredevices.util.CoreConfigFlow
import coredevices.util.models.CactusSTTMode
import coredevices.util.writeWavHeader
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.io.Buffer
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import kotlinx.io.readByteArray
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

class CactusTranscriptionService(private val coreConfigFlow: CoreConfigFlow): TranscriptionService {
    companion object {
        private val logger = Logger.withTag("CactusTranscriptionService")
        private val nonSpeechRegex = "\\[[^\\]]*\\]|\\([^)]*\\)".toRegex()
    }
    private var sttModel = CactusSTT()
    private var initJob: Job? = null
    private var lastInitedModel: String? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    private val cacheDir = Path(SystemTemporaryDirectory, "cactus_stt")

    // Expose some internal state for debugging
    val lastModelUsed get() = lastInitedModel
    val isModelReady get() = sttModel.isReady()
    val configuredMode get() = sttConfig.value.mode
    val configuredModel get() = sttConfig.value.modelName

    private fun getCacheFilePath(): Path {
        SystemFileSystem.createDirectories(cacheDir, mustCreate = false)
        val fileName = "cactus_stt_${Uuid.random()}.wav"
        return Path(cacheDir, fileName)
    }

    private val sttConfig = coreConfigFlow.flow.map { it.sttConfig }.stateIn(
        scope,
        started = kotlinx.coroutines.flow.SharingStarted.Lazily,
        initialValue = coreConfigFlow.value.sttConfig
    )

    init {
        sttConfig.onEach {
            logger.i { "Cactus STT config changed: $it" }
            if (it.modelName != lastInitedModel) {
                initJob = performInit()
            }
        }.launchIn(scope)
    }

    private suspend fun initIfNeeded() {
        val config = sttConfig.value
        when (config.mode) {
            CactusSTTMode.RemoteOnly -> {
                CommonBuildKonfig.WISPR_KEY?.let {
                    sttModel.warmUpWispr(it)
                } ?: logger.e { "WISPR API key is not set, cannot use RemoteOnly mode" }
            }
            CactusSTTMode.LocalOnly, CactusSTTMode.RemoteFirst -> {
                if (!(config.modelName?.let { sttModel.isModelDownloaded(it) } ?: false)) {
                    logger.e { "Cactus STT model '${config.modelName}' is not downloaded, cannot initialize local model" }
                    return
                }
                val start = Clock.System.now()
                if (config.modelName != lastInitedModel) {
                    sttModel = CactusSTT()
                }
                if (!sttModel.isReady()) {
                    sttModel.initializeModel(CactusInitParams(model = config.modelName))
                    val initDuration = Clock.System.now() - start
                    logger.d { "Cactus STT model initialized successfully in $initDuration" }
                    lastInitedModel = config.modelName
                }
                if (config.mode == CactusSTTMode.RemoteFirst) {
                    CommonBuildKonfig.WISPR_KEY?.let {
                        sttModel.warmUpWispr(it)
                    }
                }
            }
        }
    }

    private fun performInit(): Job {
        return scope.launch(Dispatchers.IO) {
            try {
                initIfNeeded()
            } catch (e: Exception) {
                logger.e(e) { "Cactus STT model initialization failed: ${e.message}" }
            }
            if (!sttModel.isReady()) {
                logger.e { "Cactus STT model is not ready after initialization" }
            }
        }
    }

    override suspend fun isAvailable(): Boolean = sttModel.isReady()

    private fun makeWisprConfig(
        language: STTLanguage,
        conversationContext: STTConversationContext?,
        dictionaryContext: List<String>?,
        contentContext: String?
    ): WisprFlowConfig {
        checkNotNull(CommonBuildKonfig.WISPR_KEY) { "WISPR API key is not set, cannot use Wispr" }
        val nameSplit = Firebase.auth.currentUser?.displayName?.split(" ", limit = 2)
        return WisprFlowConfig(
            apiKey = CommonBuildKonfig.WISPR_KEY,
            languages = when (language) {
                is STTLanguage.Automatic -> null
                is STTLanguage.Specific -> language.languageCodes.toList()
            },
            appName = "Core Devices",
            appType = "other",
            dictionaryContext = dictionaryContext ?: emptyList(),
            userFirstName = nameSplit?.firstOrNull(),
            userLastName = nameSplit?.lastOrNull(),
            contentsContext = contentContext,
            conversationContext = conversationContext?.let {
                WisprConversationContext(
                    id = it.id,
                    participants = it.participants,
                    messages = it.messages.map { msg ->
                        WisprConversationMessage(
                            role = when (msg.role) {
                                STTConvoRole.User -> "user"
                                STTConvoRole.Human -> "human"
                                STTConvoRole.Assistant -> "assistant"
                            },
                            content = msg.content
                        )
                    }
                )
            }.takeIf { !it?.messages.isNullOrEmpty() || !it?.participants.isNullOrEmpty() }
        )
    }

    override fun earlyInit() {
        if (initJob == null || !sttModel.isReady() || lastInitedModel != sttConfig.value.modelName) {
            if (initJob?.isActive == true) {
                logger.d { "Cactus STT model initialization already in progress" }
                return
            }
            initJob = performInit()
        }
    }

    private suspend fun cactusTranscribe(
        audio: ByteArray,
        sampleRate: Int,
        language: STTLanguage,
        conversationContext: STTConversationContext?,
        dictionaryContext: List<String>?,
        contentContext: String?,
        timeout: Duration = 30.seconds
    ): CactusTranscriptionResult? {
        val params = CactusTranscriptionParams(maxTokens = 384)
        val path = getCacheFilePath()
        withContext(Dispatchers.IO) {
            SystemFileSystem.sink(path).buffered().use { sink ->
                sink.writeWavHeader(sampleRate, audioSize = audio.size)
                sink.write(audio)
            }
        }
        try {
            logger.d { "Using transcription mode ${sttConfig.value.mode}" }
            return when (val sttMode = sttConfig.value.mode) {
                CactusSTTMode.RemoteOnly -> sttModel.transcribe(
                    filePath = path.toString(),
                    params = params,
                    mode = sttMode.cactusValue,
                    wisprConfig = makeWisprConfig(
                        language,
                        conversationContext,
                        dictionaryContext,
                        contentContext
                    )
                )
                CactusSTTMode.LocalOnly -> {
                    if (!(sttConfig.value.modelName?.let { sttModel.isModelDownloaded(it) } ?: false)) {
                        logger.e { "Cactus STT model '${sttConfig.value.modelName}' is not downloaded" }
                        throw TranscriptionException.TranscriptionRequiresDownload("Model not downloaded")
                    }
                    sttModel.transcribe(
                        filePath = path.toString(),
                        params = params,
                        mode = sttMode.cactusValue
                    )
                }
                CactusSTTMode.RemoteFirst -> {
                    try {
                        withTimeout(timeout) {
                            val result = sttModel.transcribe(
                                filePath = path.toString(),
                                params = params,
                                mode = TranscriptionMode.REMOTE,
                                wisprConfig = makeWisprConfig(
                                    language,
                                    conversationContext,
                                    dictionaryContext,
                                    contentContext
                                )
                            )
                            if (result == null) {
                                error("Remote transcription returned null")
                            }
                            result
                        }
                    } catch (e: Exception) {
                        logger.w(e) { "Remote transcription failed, falling back to local: ${e.message}" }
                        sttModel.transcribe(
                            filePath = path.toString(),
                            params = params,
                            mode = TranscriptionMode.LOCAL
                        )
                    }
                }
            }
        } finally {
            try {
                SystemFileSystem.delete(path)
            } catch (e: Exception) {
                logger.w(e) { "Failed to delete temp file $path: ${e.message}" }
            }
        }
    }

    override suspend fun transcribe(
        audioStreamFrames: Flow<ByteArray>?,
        sampleRate: Int,
        language: STTLanguage,
        conversationContext: STTConversationContext?,
        dictionaryContext: List<String>?,
        contentContext: String?,
        encoding: AudioEncoding
    ): Flow<TranscriptionSessionStatus> = flow {
        logger.d { "CactusTranscriptionService.transcribe() called" }
        logger.i { "Transcribing with model ${sttConfig.value.modelName}" }
        if (initJob == null || !sttModel.isReady() || lastInitedModel != sttConfig.value.modelName) { // Ensure model is initialized
            if (initJob?.isActive == true) {
                logger.d { "Cactus STT model initialization already in progress" }
            } else {
                initJob = performInit()
            }
        }
        emit(TranscriptionSessionStatus.Open)

        if (audioStreamFrames == null) {
            return@flow
        }

        // Collect audio and save to temp WAV file
        logger.d { "Starting audio collection..." }
        val buffer = Buffer()
        var audioSize = 0
        var chunkCount = 0
        audioStreamFrames.collect { chunk ->
            buffer.write(chunk)
            audioSize += chunk.size
            chunkCount++
        }
        logger.d { "Audio collection complete: $chunkCount chunks, $audioSize total bytes" }
        logger.d { "Sample rate: $sampleRate Hz, encoding: $encoding" }
        logger.d { "Duration: ${audioSize / (sampleRate * 2.0)}s (assuming 16-bit mono)" }

        try {
            withTimeout(20.seconds) {
                initJob?.join()
            }
            val start = Clock.System.now()
            sttModel.reset()

            logger.d { "Model ready state: ${sttModel.isReady()}" }
            val result = cactusTranscribe(
                audio = buffer.readByteArray(),
                sampleRate = sampleRate,
                language = language,
                conversationContext = conversationContext,
                dictionaryContext = dictionaryContext,
                contentContext = contentContext
            )
            val duration = Clock.System.now() - start
            logger.d { "Transcription call completed in $duration" }

            if (result != null && result.success) {
                val text = result.text
                when {
                    text.isNullOrBlank() -> {
                        logger.w { "Transcription returned empty text" }
                        throw TranscriptionException.NoSpeechDetected("empty_result")
                    }

                    text.length < 2 -> {
                        logger.w { "Transcription result too short: '${result.text}'" }
                        throw TranscriptionException.NoSpeechDetected("too_short")
                    }

                    text.replace(nonSpeechRegex, "").isBlank() -> {
                        logger.w { "Transcription result only contains non-speech tokens: '${result.text}'" }
                        throw TranscriptionException.NoSpeechDetected("non_speech_tokens")
                    }

                    text == "File processed offline" -> {
                        logger.w { "Transcription returned placeholder text indicating demo mode" }
                        throw TranscriptionException.TranscriptionServiceError("Model may be in demo mode")
                    }
                }
                logger.d { "=== TRANSCRIPTION SUCCESS ===" }
                logger.d { "Text: '${result.text}'" } //TODO: Remove for privacy
                logger.d { "Text length: ${result.text?.length} chars" }

                emit(
                    TranscriptionSessionStatus.Transcription(
                        result.text?.ifBlank { null }
                            ?: throw TranscriptionException.NoSpeechDetected("Failed to understand audio")
                    )
                )
            } else if (result != null) {
                logger.e { "Transcription failed with cactus success=false" }
                throw TranscriptionException.TranscriptionServiceError("Transcription failed: ${result.errorMessage ?: "unknown error"}")
            } else {
                logger.e { "transcribeFile returned null" }
                throw TranscriptionException.TranscriptionServiceError("Transcription failed: null result")
            }
        } catch (e: TimeoutCancellationException) {
            logger.e(e) { "Timeout during model init: ${e.message}" }
            error("Transcription timed out waiting for model initialization")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.e(e) { "Transcription failed: ${e.message}" }
            throw e
        }
    }
}