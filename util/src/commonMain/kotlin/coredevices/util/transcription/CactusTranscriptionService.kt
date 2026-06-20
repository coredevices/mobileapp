package coredevices.util.transcription

import co.touchlab.kermit.Logger
import com.cactus.cactusDestroy
import com.cactus.cactusGetLastError
import com.cactus.cactusInit
import com.cactus.cactusStop
import com.cactus.cactusTranscribe
import com.cactus.isCactusSupported
import coredevices.analytics.CoreAnalytics
import coredevices.util.AudioEncoding
import coredevices.util.CloudSTTProvider
import coredevices.util.CommonBuildKonfig
import coredevices.util.CoreConfigFlow
import coredevices.util.models.CactusSTTMode
import coredevices.util.writeWavHeader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlin.uuid.Uuid

expect suspend fun withHighPriorityThread(block: suspend () -> Unit)
expect suspend fun getFreeMemoryMB(): Long
expect val PLATFORM_MIN_TRANSCRIPTION_MEMORY_MB: Long

class CactusTranscriptionService(
    private val coreConfigFlow: CoreConfigFlow,
    private val wisprFlow: WisprFlowTranscriptionService,
    private val kirinki: KirinkiTranscriptionService,
    private val openAi: OpenAiTranscriptionService,
    private val modelProvider: CactusModelPathProvider,
    private val analytics: CoreAnalytics,
    private val inferenceBoost: InferenceBoost = NoOpInferenceBoost()
): TranscriptionService {
    companion object {
        private val logger = Logger.withTag("CactusTranscriptionService")
        private val nonSpeechRegex = "\\[[^\\]]*\\]|\\([^)]*\\)".toRegex()
        private val wisprSkipInterval = 1.minutes
    }

    private val transcriptionMutex = Mutex()
    private var modelHandle: Long = 0L
    private var initJob: Job? = null
    private var lastInitedModel: String? = null
    private val scope = CoroutineScope(Dispatchers.Default)
    private val cacheDir = Path(SystemTemporaryDirectory, "cactus_stt")

    /**
     * Calls cactusStop() if the calling coroutine is cancelled while [block] runs.
     */
    private suspend fun <T> withCactusStopOnCancel(handle: Long, block: () -> T): T {
        val callerJob = kotlin.coroutines.coroutineContext[Job]
        val completionHandle = callerJob?.invokeOnCompletion { cause ->
            if (cause != null) {
                logger.d { "Calling cactusStop() due to cancellation: ${cause.message}" }
                cactusStop(handle)
            }
        }
        return try {
            block()
        } finally {
            completionHandle?.dispose()
        }
    }

    /**
     * Run cactusTranscribe() with cancellation support (see [withCactusStopOnCancel]).
     */
    private suspend fun cancellableTranscribe(handle: Long, audioPath: String): String {
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
        return withCactusStopOnCancel(handle) {
            parseTranscriptionText(cactusTranscribe(handle, audioPath, null, null, null, null)).also { text ->
                if (text.isBlank()) {
                    logger.w { "cactusTranscribe returned blank result, native lastError='${cactusGetLastError()}'" }
                }
            }
        }
    }

    private fun parseTranscriptionText(jsonResult: String): String {
        return try {
            Json.parseToJsonElement(jsonResult).jsonObject["response"]?.jsonPrimitive?.content ?: ""
        } catch (e: Exception) {
            logger.w(e) { "Failed to parse transcription JSON, using raw result" }
            jsonResult
        }
    }

    val lastModelUsed get() = lastInitedModel
    val isModelReady get() = modelHandle != 0L
    val configuredMode get() = sttConfig.value.mode
    val configuredModel get() = sttConfig.value.modelName
    val configuredLanguage get() = sttConfig.value.spokenLanguage
    private var _lastSuccessfulMode: CactusSTTMode? = null
    val lastSuccessfulMode get() = _lastSuccessfulMode
    override val onInitialized = Channel<Boolean>(Channel.RENDEZVOUS)

    private val lastErrorMutex = Mutex()
    private var lastWisprError = Instant.DISTANT_PAST

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
            val handle = modelHandle
            if (handle == 0L) return
            withHighPriorityThread {
                withTimeout(2.seconds) {
                    withCactusStopOnCancel(handle) {
                        cactusTranscribe(handle, null, null, null, null, silentPcm)
                    }
                }
            }
        }
    }

    private suspend fun initIfNeeded() {
        val config = sttConfig.value
        if (config.mode == CactusSTTMode.RemoteOnly) return
        if (!isCactusSupported()) return
        val sttModelName = coredevices.util.CommonBuildKonfig.CACTUS_STT_MODEL
        if (!modelProvider.isModelDownloaded(sttModelName)) {
            logger.w { "STT model '$sttModelName' not downloaded, skipping init" }
            return
        }
        val start = Clock.System.now()
        if (config.modelName != lastInitedModel) {
            if (modelHandle != 0L) {
                cactusDestroy(modelHandle)
                modelHandle = 0L
            }
        }
        if (modelHandle == 0L) {
            val modelPath = modelProvider.getSTTModelPath()
            modelHandle = cactusInit(modelPath, null, false)
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
                onInitialized.trySend(modelHandle != 0L || sttConfig.value.mode == CactusSTTMode.RemoteOnly)
            } catch (e: Throwable) {
                logger.e(e) { "Cactus STT model initialization failed: ${e.message}" }
                onInitialized.trySend(false)
            }
        }
    }

    private suspend fun remoteAvailable(): Boolean =
        when (sttConfig.value.cloudProvider) {
            CloudSTTProvider.OpenAiCompatible -> openAi.isAvailable()
            CloudSTTProvider.WisprFlow -> wisprFlow.isAvailable() || kirinki.isAvailable()
        }

    override suspend fun isAvailable(): Boolean {
        return when (configuredMode) {
            CactusSTTMode.RemoteOnly -> remoteAvailable()
            CactusSTTMode.LocalOnly -> isCactusSupported() && (modelHandle != 0L || modelExists())
            CactusSTTMode.RemoteFirst, CactusSTTMode.LocalFirst ->
                remoteAvailable() || (isCactusSupported() && modelHandle != 0L)
            // Rebble modes are dispatched by STTRouter and never reach this service.
            CactusSTTMode.RebbleOnly,
            CactusSTTMode.RebbleFirst,
            CactusSTTMode.RebbleFallback -> false
        }
    }

    override fun earlyInit() {
        if (initJob == null || modelHandle == 0L || lastInitedModel != sttConfig.value.modelName) {
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

    // remoteTranscribe runs cloud transcription via the configured provider. willFallbackLocal
    // shortens timeouts and disables the kirinki backup so a local fallback can take over.
    private suspend fun remoteTranscribe(
        audio: ByteArray,
        sampleRate: Int,
        language: STTLanguage,
        conversationContext: STTConversationContext?,
        dictionaryContext: List<String>?,
        contentContext: String?,
        willFallbackLocal: Boolean
    ): TranscriptionSessionStatus.Transcription {
        if (sttConfig.value.cloudProvider == CloudSTTProvider.OpenAiCompatible) {
            return openAiTranscribe(
                audio = audio,
                sampleRate = sampleRate,
                language = language,
                conversationContext = conversationContext,
                dictionaryContext = dictionaryContext,
                contentContext = contentContext,
                willFallbackLocal = willFallbackLocal,
            )
        }
        // We reduce the timeout if we have the potential to fall back locally since some consumers
        // (e.g. pebble firmware) have hard timeouts.
        val initialTimeout = if (willFallbackLocal) 7.seconds else 10.seconds

        suspend fun transcribeKirinki() = try {
            kirinki.transcribe(
                audioStreamFrames = flowOf(audio),
                sampleRate = sampleRate,
                language = language,
                conversationContext = conversationContext,
                dictionaryContext = dictionaryContext,
                contentContext = contentContext
            ).filterIsInstance<TranscriptionSessionStatus.Transcription>().first().also {
                analytics.logTranscriptionSuccess("kirinki")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            analytics.logTranscriptionFailure("kirinki", transcriptionFailureReason(e), e.message)
            throw e
        }

        // Kirinki is only used as a backup when there's no local model to fall back on. When a local
        // fallback is available we let the caller handle it by propagating the WisprFlow failure.
        val canUseKirinki = !willFallbackLocal && kirinki.isAvailable()

        val skipWispr = lastErrorMutex.withLock {
            // Don't skip wispr if local fallback, because cactus might still be running, we can't trust its cancellation right now due to bug
            ((Clock.System.now() - lastWisprError) < wisprSkipInterval && canUseKirinki) && !willFallbackLocal
        }
        if (skipWispr) {
            if (canUseKirinki) {
                logger.w { "Skipping WisprFlow transcription due to recent error, using kirinki directly" }
                return transcribeKirinki()
            }
            logger.w { "Skipping WisprFlow transcription due to recent error, falling back to local" }
            throw TranscriptionException.TranscriptionServiceUnavailable("wisprflow")
        }

        return try {
            val res = withTimeout(initialTimeout) {
                wisprFlow.transcribe(
                    audioStreamFrames = flowOf(audio),
                    sampleRate = sampleRate,
                    language = language,
                    conversationContext = conversationContext,
                    dictionaryContext = dictionaryContext,
                    contentContext = contentContext
                ).filterIsInstance<TranscriptionSessionStatus.Transcription>().first()
            }
            lastErrorMutex.withLock {
                lastWisprError = Instant.DISTANT_PAST
            }
            analytics.logTranscriptionSuccess("wisprflow")
            res
        } catch (e: Exception) {
            if (e !is TimeoutCancellationException && e is CancellationException) throw e
            analytics.logTranscriptionFailure("wisprflow", transcriptionFailureReason(e), e.message)
            if (e is TranscriptionException.NoSpeechDetected) throw e // NoSpeechDetected is a valid result, not a failure of the service
            lastErrorMutex.withLock {
                lastWisprError = Clock.System.now()
            }

            if (!canUseKirinki) {
                logger.w(e) { "WisprFlow transcription failed, propagating to caller: ${e.message}" }
                throw e
            }
            logger.w(e) { "WisprFlow transcription failed, falling back to kirinki: ${e.message}" }
            transcribeKirinki()
        }
    }

    // openAiTranscribe runs cloud STT against the user's OpenAI-compatible endpoint;
    // NoSpeechDetected is re-thrown as a valid (non-failure) result.
    private suspend fun openAiTranscribe(
        audio: ByteArray,
        sampleRate: Int,
        language: STTLanguage,
        conversationContext: STTConversationContext?,
        dictionaryContext: List<String>?,
        contentContext: String?,
        willFallbackLocal: Boolean
    ): TranscriptionSessionStatus.Transcription {
        // A local fallback caps the wait; without one, allow longer for slow self-hosted endpoints.
        val timeout = if (willFallbackLocal) 7.seconds else 15.seconds
        return try {
            val res = withTimeout(timeout) {
                openAi.transcribe(
                    audioStreamFrames = flowOf(audio),
                    sampleRate = sampleRate,
                    language = language,
                    conversationContext = conversationContext,
                    dictionaryContext = dictionaryContext,
                    contentContext = contentContext
                ).filterIsInstance<TranscriptionSessionStatus.Transcription>().first()
            }
            analytics.logTranscriptionSuccess("openai")
            res
        } catch (e: Exception) {
            if (e !is TimeoutCancellationException && e is CancellationException) throw e
            analytics.logTranscriptionFailure("openai", transcriptionFailureReason(e), e.message)
            if (e is TranscriptionException.NoSpeechDetected) throw e
            logger.w(e) { "OpenAI transcription failed, propagating to caller: ${e.message}" }
            throw e
        }
    }

    private suspend fun <T> withMaybeTimeout(timeout: Duration?, block: suspend () -> T): T {
        return if (timeout != null) {
            withTimeout(timeout) { block() }
        } else {
            block()
        }
    }

    private suspend fun runLocalTranscribe(path: Path, timeout: Duration? = null): String {
        try {
            val handle = modelHandle
            if (handle == 0L) {
                if (!isCactusSupported()) {
                    throw TranscriptionException.TranscriptionServiceUnavailable(modelUsed = sttConfig.value.modelName)
                }
                throw TranscriptionException.TranscriptionRequiresDownload("Model not initialized")
            }
            inferenceBoost.acquire()
            val text = try {
                withMaybeTimeout(timeout) {
                    cancellableTranscribe(handle, path.toString())
                }
            } finally {
                inferenceBoost.release()
            }
            analytics.logTranscriptionSuccess("cactus")
            return text
        } catch (e: TimeoutCancellationException) {
            analytics.logTranscriptionFailure("cactus", transcriptionFailureReason(e), e.message)
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            analytics.logTranscriptionFailure("cactus", transcriptionFailureReason(e), e.message)
            throw e
        }
    }

    private suspend fun localTranscribe(
        audio: ByteArray,
        sampleRate: Int,
        language: STTLanguage,
        conversationContext: STTConversationContext?,
        dictionaryContext: List<String>?,
        contentContext: String?,
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
                    val result = remoteTranscribe(
                        audio = audio,
                        sampleRate = sampleRate,
                        language = language,
                        conversationContext = conversationContext,
                        dictionaryContext = dictionaryContext,
                        contentContext = contentContext,
                        willFallbackLocal = false
                    )
                    LocalTranscriptionResult(
                        text = result.text,
                        modeUsed = sttMode,
                        modelUsed = result.modelUsed
                    )
                }
                CactusSTTMode.LocalOnly -> {
                    val text = runLocalTranscribe(path)
                    LocalTranscriptionResult(
                        text = text,
                        modeUsed = sttMode,
                        modelUsed = sttConfig.value.modelName
                    )
                }
                CactusSTTMode.RemoteFirst -> {
                    try {
                        val result = remoteTranscribe(
                            audio = audio,
                            sampleRate = sampleRate,
                            language = language,
                            conversationContext = conversationContext,
                            dictionaryContext = dictionaryContext,
                            contentContext = contentContext,
                            willFallbackLocal = true
                        )
                        LocalTranscriptionResult(
                            text = result.text,
                            modeUsed = sttMode,
                            modelUsed = result.modelUsed
                        )
                    } catch (e: Exception) {
                        logger.w(e) { "Remote transcription failed, falling back to local: ${e.message}" }
                        val text = runLocalTranscribe(path)
                        LocalTranscriptionResult(
                            text = text,
                            modeUsed = CactusSTTMode.LocalOnly,
                            modelUsed = sttConfig.value.modelName
                        )
                    }
                }
                CactusSTTMode.LocalFirst -> {
                    try {
                        val text = runLocalTranscribe(path, 10.seconds)
                        LocalTranscriptionResult(
                            text = text,
                            modeUsed = sttMode,
                            modelUsed = sttConfig.value.modelName
                        )
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        logger.w(e) { "Local transcription failed, falling back to remote: ${e.message}" }
                        val result = remoteTranscribe(
                            audio = audio,
                            sampleRate = sampleRate,
                            language = language,
                            conversationContext = conversationContext,
                            dictionaryContext = dictionaryContext,
                            contentContext = contentContext,
                            willFallbackLocal = false
                        )
                        LocalTranscriptionResult(
                            text = result.text,
                            modeUsed = CactusSTTMode.RemoteOnly,
                            modelUsed = result.modelUsed
                        )
                    }
                }
                // Rebble modes are routed by STTRouter and never reach this service.
                CactusSTTMode.RebbleOnly,
                CactusSTTMode.RebbleFirst,
                CactusSTTMode.RebbleFallback ->
                    error("Rebble mode $sttMode should be handled by STTRouter, not CactusTranscriptionService")
            }
        } finally {
            try { SystemFileSystem.delete(path) } catch (e: Exception) {
                logger.w(e) { "Failed to delete temp file $path" }
            }
        }
    }

    /**
     * Run the local Cactus model directly on a pre-collected PCM buffer, ignoring [sttConfig.mode].
     * Intended for callers (e.g. Rebble ASR fallback) that decide mode externally.
     * Returns the recognized text. Throws [TranscriptionException.TranscriptionRequiresDownload]
     * if the local model isn't initialized; throws [TranscriptionException.NoSpeechDetected]
     * if the result is empty.
     */
    suspend fun transcribeLocalForFallback(
        audio: ByteArray,
        sampleRate: Int,
        timeout: Duration = Duration.INFINITE,
    ): String {
        if (initJob == null || modelHandle == 0L) {
            if (initJob?.isActive != true) {
                initJob = performInit()
            }
        }
        withTimeout(20.seconds) { initJob?.join() }
        val handle = modelHandle
        if (handle == 0L) throw TranscriptionException.TranscriptionRequiresDownload("Model not initialized")

        val path = getCacheFilePath()
        return transcriptionMutex.withLock {
            withContext(Dispatchers.IO) {
                SystemFileSystem.sink(path).buffered().use { sink ->
                    sink.writeWavHeader(sampleRate, audioSize = audio.size)
                    sink.write(audio)
                }
            }
            try {
                inferenceBoost.acquire()
                val text: String = try {
                    withTimeout(timeout) {
                        cancellableTranscribe(handle, path.toString())
                    }
                } finally {
                    inferenceBoost.release()
                }
                _lastSuccessfulMode = CactusSTTMode.LocalOnly
                val result = text.takeIf { it.isNotBlank() }
                    ?: throw TranscriptionException.NoSpeechDetected(
                        "empty_result",
                        modelUsed = sttConfig.value.modelName,
                    )
                analytics.logTranscriptionSuccess("cactus")
                result
            } catch (e: TimeoutCancellationException) {
                analytics.logTranscriptionFailure("cactus", transcriptionFailureReason(e), e.message)
                throw e
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                analytics.logTranscriptionFailure("cactus", transcriptionFailureReason(e), e.message)
                throw e
            } finally {
                try {
                    SystemFileSystem.delete(path)
                } catch (e: Exception) {
                    logger.w(e) { "Failed to delete temp file $path" }
                }
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
    ): Flow<TranscriptionSessionStatus> = flow {
        logger.d { "CactusTranscriptionService.transcribe() called" }
        if (initJob == null || modelHandle == 0L || lastInitedModel != sttConfig.value.modelName) {
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
            withTimeout(10.seconds) { initJob?.join() }
            val start = Clock.System.now()
            val (text, modeUsed, modelUsed) = transcriptionMutex.withLock {
                localTranscribe(
                    audio = buffer.readByteArray(),
                    sampleRate = sampleRate,
                    language = language,
                    conversationContext = conversationContext,
                    dictionaryContext = dictionaryContext,
                    contentContext = contentContext,
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

            if (!coreConfigFlow.value.obfuscateSensitiveLogs) {
                logger.d { "Transcription text: '$text' (${text?.length} chars), used $modelUsed" }
            } else {
                logger.d { "Transcription text ${text?.length} chars, used $modelUsed" }
            }
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
