package coredevices.util.transcription

import co.touchlab.kermit.Logger
import com.cactus.cactusDestroy
import com.cactus.cactusGetLastError
import com.cactus.cactusInit
import com.cactus.cactusStop
import com.cactus.cactusTranscribe
import com.cactus.isCactusSupported
import coredevices.analytics.CoreAnalytics
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
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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

private val nonSpeechRegex = "\\[[^\\]]*\\]|\\([^)]*\\)".toRegex()

/**
 * Throws [TranscriptionException.NoSpeechDetected] if [text] is blank or contains no usable speech
 * (only noise / non-speech tokens / stutters). Returns normally otherwise.
 *
 * Used both as the final guard on a transcription result and, for [CactusSTTMode.LocalFirst], to
 * treat an empty local result as a failure that triggers the remote fallback (HARD-324).
 */
internal fun validateContainsSpeech(text: String?, modelUsed: String?) {
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
}

class CactusTranscriptionService(
    private val coreConfigFlow: CoreConfigFlow,
    private val modelProvider: CactusModelPathProvider,
    private val analytics: CoreAnalytics,
    private val inferenceBoost: InferenceBoost = NoOpInferenceBoost()
) {
    companion object {
        private val logger = Logger.withTag("CactusTranscriptionService")
    }

    private val transcriptionMutex = Mutex()
    private var modelHandle: Long = 0L
    private var initJob: Job? = null
    private var lastInitedModel: String? = null
    private val scope = CoroutineScope(Dispatchers.Default)
    private val cacheDir = Path(SystemTemporaryDirectory, "cactus_stt")

    val lastModelUsed get() = lastInitedModel
    val isModelReady get() = modelHandle != 0L
    val configuredModel get() = sttConfig.value.modelName
    val onInitialized = Channel<Boolean>(Channel.RENDEZVOUS)

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

    private fun getCacheFilePath(): Path {
        SystemFileSystem.createDirectories(cacheDir, mustCreate = false)
        return Path(cacheDir, "cactus_stt_${Uuid.random()}.wav")
    }

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
        val sttModelName = CommonBuildKonfig.CACTUS_STT_MODEL
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

    /** True if the local model is loaded, or downloaded and ready to load, on a supported device. */
    fun isLocalAvailable(): Boolean = isCactusSupported() && (modelHandle != 0L || modelExists())

    fun earlyInit() {
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

    /** Kick off init if needed and wait (up to [initTimeout]) for it to settle. */
    private suspend fun ensureInit(initTimeout: Duration) {
        if (initJob == null || modelHandle == 0L || lastInitedModel != sttConfig.value.modelName) {
            if (initJob?.isActive != true) {
                initJob = performInit()
            }
        }
        withTimeout(initTimeout) { initJob?.join() }
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

    /**
     * Run the local Cactus model on a pre-collected PCM buffer. Writes a temp WAV, transcribes with
     * cancellation support, and returns the recognized text (which may be blank — the caller decides
     * how to treat that). Serialized via [transcriptionMutex] to protect the native model handle.
     *
     * Throws [TranscriptionException.TranscriptionRequiresDownload] if the model isn't initialized,
     * [TranscriptionException.TranscriptionServiceUnavailable] if Cactus is unsupported, and
     * [TranscriptionException.NotEnoughMemory] under memory pressure.
     */
    suspend fun transcribeLocal(
        audio: ByteArray,
        sampleRate: Int,
        timeout: Duration? = null,
        initTimeout: Duration = 10.seconds,
    ): String {
        ensureInit(initTimeout)
        val path = getCacheFilePath()
        if (!transcriptionMutex.tryLock()) {
            throw TranscriptionException.TranscriptionInProgress(modelUsed = sttConfig.value.modelName)
        }
        return try {
            withContext(Dispatchers.IO) {
                SystemFileSystem.sink(path).buffered().use { sink ->
                    sink.writeWavHeader(sampleRate, audioSize = audio.size)
                    sink.write(audio)
                }
            }
            try {
                runLocalTranscribe(path, timeout)
            } finally {
                try { SystemFileSystem.delete(path) } catch (e: Exception) {
                    logger.w(e) { "Failed to delete temp file $path" }
                }
            }
        } finally {
            transcriptionMutex.unlock()
        }
    }

    /**
     * Run the local Cactus model directly on a pre-collected PCM buffer, ignoring the configured
     * mode. Intended for callers (e.g. Rebble ASR fallback) that decide mode externally.
     * Returns the recognized text. Throws [TranscriptionException.TranscriptionRequiresDownload]
     * if the local model isn't initialized; throws [TranscriptionException.NoSpeechDetected]
     * if the result is empty.
     */
    suspend fun transcribeLocalForFallback(
        audio: ByteArray,
        sampleRate: Int,
        timeout: Duration = Duration.INFINITE,
    ): String {
        val text = transcribeLocal(
            audio = audio,
            sampleRate = sampleRate,
            timeout = timeout.takeIf { it.isFinite() },
            initTimeout = 20.seconds,
        )
        return text.takeIf { it.isNotBlank() }
            ?: throw TranscriptionException.NoSpeechDetected(
                "empty_result",
                modelUsed = sttConfig.value.modelName,
            )
    }
}
