package coredevices.util.transcription

import co.touchlab.kermit.Logger
import com.cactus.Cactus
import com.cactus.TranscriptionResult
import coredevices.util.AudioEncoding
import coredevices.util.CommonBuildKonfig
import coredevices.util.CoreConfigFlow
import coredevices.util.models.CactusSTTMode
import coredevices.util.writeWavHeader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlin.uuid.Uuid

expect suspend fun withHighPriorityThread(block: suspend () -> Unit)
expect suspend fun getFreeMemoryMB(): Long
expect val PLATFORM_MIN_TRANSCRIPTION_MEMORY_MB: Long

class CactusTranscriptionService(
    private val coreConfigFlow: CoreConfigFlow,
    private val wisprFlow: WisprFlowTranscriptionService,
    private val modelProvider: CactusModelPathProvider,
    private val inferenceBoost: InferenceBoost = NoOpInferenceBoost()
): TranscriptionService {
    companion object {
        private val logger = Logger.withTag("CactusTranscriptionService")
        private val nonSpeechRegex = "\\[[^\\]]*\\]|\\([^)]*\\)".toRegex()
    }

    private val transcriptionMutex = Mutex()
    private var model: Cactus? = null
    private var initJob: Job? = null
    private var lastInitedModel: String? = null
    private val scope = CoroutineScope(Dispatchers.Default)
    private val cacheDir = Path(SystemTemporaryDirectory, "cactus_stt")

    /**
     * Run cactus.transcribe() with cancellation support.
     * Since the native transcribe call is blocking and can't be interrupted by coroutine
     * cancellation, we monitor the calling coroutine's Job and call cactus.stop() if it
     * gets cancelled while the native call is in progress.
     */
    private suspend fun cancellableTranscribe(cactus: Cactus, audioPath: String): TranscriptionResult {
        val freeMemory = try {
            getFreeMemoryMB()
        } catch (e: Exception) {
            logger.w(e) { "Failed to get free memory" }
            0L
        }
        if (freeMemory < PLATFORM_MIN_TRANSCRIPTION_MEMORY_MB) {
            logger.e { "Low free memory ($freeMemory MB), skipping local transcription" }
            throw TranscriptionException.NotEnoughMemory(modelUsed = sttConfig.value.modelName)
        }
        val callerJob = kotlin.coroutines.coroutineContext[Job]
        val completionHandle = callerJob?.invokeOnCompletion { cause ->
            if (cause != null) {
                logger.d { "Calling cactus.stop() due to cancellation: ${cause.message}" }
                cactus.stop()
            }
        }
        return try {
            cactus.transcribe(audioPath = audioPath)
        } finally {
            completionHandle?.dispose()
        }
    }

    val lastModelUsed get() = lastInitedModel
    val isModelReady get() = model != null
    val configuredMode get() = sttConfig.value.mode
    val configuredModel get() = sttConfig.value.modelName
    private var _lastSuccessfulMode: CactusSTTMode? = null
    val lastSuccessfulMode get() = _lastSuccessfulMode
    override val onInitialized = Channel<Boolean>(Channel.RENDEZVOUS)

    private fun getCacheFilePath(): Path {
        SystemFileSystem.createDirectories(cacheDir, mustCreate = false)
        return Path(cacheDir, "cactus_stt_${Uuid.random()}.wav")
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

    private var lastTranscriptionAt: TimeMark? = null
    private val warmupMutex = Mutex()
    private val silentPcm = ByteArray(32_000) // 1s, 16kHz, int16 mono

    private suspend fun warmUpIfIdle() {
        // Warm up only when we haven't recently warmed up / transcribed
        if ((lastTranscriptionAt?.elapsedNow() ?: Duration.INFINITE) < 2.minutes) {
            lastTranscriptionAt = TimeSource.Monotonic.markNow()
            return
        }
        logger.d { "Warming up Cactus STT model with silent audio" }
        val freeMemory = try {
            getFreeMemoryMB()
        } catch (e: Exception) {
            logger.w(e) { "Failed to get free memory" }
            0L
        }
        if (freeMemory < PLATFORM_MIN_TRANSCRIPTION_MEMORY_MB) {
            logger.w { "Low free memory ($freeMemory MB), skipping warmup" }
            return
        }
        lastTranscriptionAt = TimeSource.Monotonic.markNow()
        warmupMutex.withLock {
            val cactus = model ?: return
            withHighPriorityThread {
                withTimeout(2.seconds) {
                    cactus.transcribe(pcmData = silentPcm)
                }
            }
        }
    }

    private suspend fun initIfNeeded() {
        val config = sttConfig.value
        if (config.mode == CactusSTTMode.RemoteOnly) return
        val sttModelName = coredevices.util.CommonBuildKonfig.CACTUS_STT_MODEL
        if (!modelProvider.isModelDownloaded(sttModelName)) {
            logger.w { "STT model '$sttModelName' not downloaded, skipping init" }
            return
        }
        val start = Clock.System.now()
        if (config.modelName != lastInitedModel) {
            model?.close()
            model = null
        }
        if (model == null) {
            val modelPath = modelProvider.getSTTModelPath()
            model = Cactus.create(modelPath)
            lastInitedModel = config.modelName
            val initDuration = Clock.System.now() - start
            logger.d { "Cactus STT model initialized in $initDuration" }
        }
    }


    private fun modelExists(): Boolean = modelProvider.isModelDownloaded(CommonBuildKonfig.CACTUS_STT_MODEL)

    private fun performInit(): Job {
        return scope.launch(Dispatchers.IO) {
            try {
                initIfNeeded()
                warmUpIfIdle()
                onInitialized.trySend(model != null || sttConfig.value.mode == CactusSTTMode.RemoteOnly)
            } catch (e: Throwable) {
                logger.e(e) { "Cactus STT model initialization failed: ${e.message}" }
                onInitialized.trySend(false)
            }
        }
    }

    override suspend fun isAvailable(): Boolean {
        return when (configuredMode) {
            CactusSTTMode.RemoteOnly -> wisprFlow.isAvailable()
            CactusSTTMode.LocalOnly -> model != null || modelExists()
            CactusSTTMode.RemoteFirst, CactusSTTMode.LocalFirst -> wisprFlow.isAvailable() || model != null
        }
    }

    override fun earlyInit() {
        if (initJob == null || model == null || lastInitedModel != sttConfig.value.modelName) {
            if (initJob?.isActive == true) {
                logger.d { "Cactus STT model initialization already in progress" }
                return
            }
            initJob = performInit()
        } else {
            scope.launch {
                warmUpIfIdle()
                onInitialized.trySend(true)
            }
        }
    }

    private data class LocalTranscriptionResult(
        val text: String?,
        val modeUsed: CactusSTTMode,
        val modelUsed: String?
    )

    private suspend fun cactusTranscribe(
        audio: ByteArray,
        sampleRate: Int,
        language: STTLanguage,
        conversationContext: STTConversationContext?,
        dictionaryContext: List<String>?,
        contentContext: String?,
        timeout: Duration,
    ): LocalTranscriptionResult {
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
                CactusSTTMode.RemoteOnly -> {
                    val result = wisprFlow.transcribe(
                        audioStreamFrames = flowOf(audio),
                        sampleRate = sampleRate,
                        language = language,
                        conversationContext = conversationContext,
                        dictionaryContext = dictionaryContext,
                        contentContext = contentContext,
                        timeout = timeout
                    ).filterIsInstance<TranscriptionSessionStatus.Transcription>().first()
                    LocalTranscriptionResult(
                        text = result.text,
                        modeUsed = sttMode,
                        modelUsed = result.modelUsed
                    )
                }
                CactusSTTMode.LocalOnly -> {
                    val cactus = model ?: throw TranscriptionException.TranscriptionRequiresDownload("Model not initialized")
                    inferenceBoost.acquire()
                    val result: TranscriptionResult = try {
                        withTimeout(timeout) {
                            cancellableTranscribe(cactus, path.toString())
                        }
                    } finally {
                        inferenceBoost.release()
                    }
                    LocalTranscriptionResult(
                        text = result.text,
                        modeUsed = sttMode,
                        modelUsed = sttConfig.value.modelName
                    )
                }
                CactusSTTMode.RemoteFirst -> {
                    try {
                        val result = wisprFlow.transcribe(
                            audioStreamFrames = flowOf(audio),
                            sampleRate = sampleRate,
                            language = language,
                            conversationContext = conversationContext,
                            dictionaryContext = dictionaryContext,
                            contentContext = contentContext,
                            timeout = timeout
                        ).filterIsInstance<TranscriptionSessionStatus.Transcription>().first()
                        LocalTranscriptionResult(
                            text = result.text,
                            modeUsed = sttMode,
                            modelUsed = result.modelUsed
                        )
                    } catch (e: Exception) {
                        logger.w(e) { "Remote transcription failed, falling back to local: ${e.message}" }
                        val cactus = model ?: throw TranscriptionException.TranscriptionRequiresDownload("Model not initialized")
                        inferenceBoost.acquire()
                        val result: TranscriptionResult = try {
                            withTimeout(timeout) {
                                cancellableTranscribe(cactus, path.toString())
                            }
                        } finally {
                            inferenceBoost.release()
                        }
                        LocalTranscriptionResult(
                            text = result.text,
                            modeUsed = CactusSTTMode.LocalOnly,
                            modelUsed = sttConfig.value.modelName
                        )
                    }
                }
                CactusSTTMode.LocalFirst -> {
                    try {
                        val cactus = model ?: throw TranscriptionException.TranscriptionRequiresDownload("Model not initialized")
                        inferenceBoost.acquire()
                        val result: TranscriptionResult = try {
                            withTimeout(timeout) {
                                cancellableTranscribe(cactus, path.toString())
                            }
                        } finally {
                            inferenceBoost.release()
                        }
                        LocalTranscriptionResult(
                            text = result.text,
                            modeUsed = sttMode,
                            modelUsed = sttConfig.value.modelName
                        )
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        logger.w(e) { "Local transcription failed, falling back to remote: ${e.message}" }
                        val result = wisprFlow.transcribe(
                            audioStreamFrames = flowOf(audio),
                            sampleRate = sampleRate,
                            language = language,
                            conversationContext = conversationContext,
                            dictionaryContext = dictionaryContext,
                            contentContext = contentContext,
                            timeout = timeout
                        ).filterIsInstance<TranscriptionSessionStatus.Transcription>().first()
                        LocalTranscriptionResult(
                            text = result.text,
                            modeUsed = CactusSTTMode.RemoteOnly,
                            modelUsed = result.modelUsed
                        )
                    }
                }
            }
        } finally {
            try { SystemFileSystem.delete(path) } catch (e: Exception) {
                logger.w(e) { "Failed to delete temp file $path" }
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
        encoding: AudioEncoding,
        timeout: Duration,
    ): Flow<TranscriptionSessionStatus> = flow {
        logger.d { "CactusTranscriptionService.transcribe() called" }
        if (initJob == null || model == null || lastInitedModel != sttConfig.value.modelName) {
            if (initJob?.isActive != true) {
                initJob = performInit()
            }
        }
        emit(TranscriptionSessionStatus.Open)

        if (audioStreamFrames == null) return@flow

        val buffer = Buffer()
        var audioSize = 0
        audioStreamFrames.collect { chunk ->
            buffer.write(chunk)
            audioSize += chunk.size
        }
        logger.d { "Audio collection complete: $audioSize bytes, ${audioSize / (sampleRate * 2.0)}s" }

        if (buffer.size == 0L || audioSize / (sampleRate * 2.0) < 0.1) {
            throw TranscriptionException.NoSpeechDetected("No audio data received")
        }

        try {
            withTimeout(20.seconds) { initJob?.join() }
            val start = Clock.System.now()
            val (text, modeUsed, modelUsed) = transcriptionMutex.withLock {
                cactusTranscribe(
                    audio = buffer.readByteArray(),
                    sampleRate = sampleRate,
                    language = language,
                    conversationContext = conversationContext,
                    dictionaryContext = dictionaryContext,
                    contentContext = contentContext,
                    timeout = timeout
                )
            }
            if (text != null) _lastSuccessfulMode = modeUsed
            val duration = Clock.System.now() - start
            logger.d { "Transcription completed in $duration" }

            when {
                text.isNullOrBlank() ->
                    throw TranscriptionException.NoSpeechDetected("empty_result", modelUsed = modelUsed)
                text.length < 2 ->
                    throw TranscriptionException.NoSpeechDetected("too_short", modelUsed = modelUsed)
                text.replace(nonSpeechRegex, "").isBlank() ->
                    throw TranscriptionException.NoSpeechDetected("non_speech_tokens", modelUsed = modelUsed)
                text.replace("s*", "").lowercase().count { it.isLetterOrDigit() } < 2 ->
                    throw TranscriptionException.NoSpeechDetected("stutters_or_noise", modelUsed = modelUsed)
            }

            logger.d { "Transcription text: '$text' (${text?.length} chars)" }
            emit(TranscriptionSessionStatus.Transcription(
                text?.ifBlank { null }
                    ?: throw TranscriptionException.NoSpeechDetected("Failed to understand audio", modelUsed = modelUsed),
                modelUsed
            ))
        } catch (e: TimeoutCancellationException) {
            logger.e(e) { "Timeout during model init" }
            throw TranscriptionException.TranscriptionServiceUnavailable(modelUsed = sttConfig.value.modelName)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.e(e) { "Transcription failed: ${e.message}" }
            throw e
        }
    }
}
