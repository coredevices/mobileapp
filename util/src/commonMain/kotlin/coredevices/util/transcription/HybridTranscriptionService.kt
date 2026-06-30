package coredevices.util.transcription

import co.touchlab.kermit.Logger
import coredevices.analytics.CoreAnalytics
import coredevices.util.AudioEncoding
import coredevices.util.CloudSTTProvider
import coredevices.util.CoreConfigFlow
import coredevices.util.models.CactusSTTMode
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Mode-aware [TranscriptionService] that routes between the local Cactus model
 * ([CactusTranscriptionService]) and the remote backends, and owns the fallback behaviour for the
 * [CactusSTTMode] options. The remote backend is selected by [STTConfig.cloudProvider]: either
 * WisprFlow (with Kirinki as backup) or a user-configured OpenAI-compatible endpoint.
 */
class HybridTranscriptionService(
    private val coreConfigFlow: CoreConfigFlow,
    private val cactus: CactusTranscriptionService,
    private val wisprFlow: WisprFlowRESTTranscriptionService,
    private val kirinki: KirinkiTranscriptionService,
    private val openAi: OpenAiTranscriptionService,
    private val analytics: CoreAnalytics,
) : TranscriptionService {
    companion object {
        private val logger = Logger.withTag("HybridTranscriptionService")
        private val wisprSkipInterval = 1.seconds
    }

    // Read fresh from the config StateFlow on every access so a runtime mode/model change takes
    // effect without restarting the app (not cached in a stateIn that nothing collects).
    private val sttConfig get() = coreConfigFlow.value.sttConfig

    private val lastErrorMutex = Mutex()
    private var lastWisprError = Instant.DISTANT_PAST

    private var _lastSuccessfulMode: CactusSTTMode? = null

    // Diagnostics consumed by the bug report STT summary.
    val configuredMode get() = sttConfig.mode
    val configuredModel get() = sttConfig.modelName
    val configuredLanguage get() = sttConfig.spokenLanguage
    val lastSuccessfulMode get() = _lastSuccessfulMode
    val lastModelUsed get() = cactus.lastModelUsed
    val isModelReady get() = cactus.isModelReady

    override val onInitialized: Channel<Boolean> get() = cactus.onInitialized

    override fun earlyInit() {
        if (sttConfig.mode.usesLocalCactus()) {
            cactus.earlyInit()
        }
    }

    private suspend fun remoteAvailable(): Boolean =
        when (sttConfig.cloudProvider) {
            CloudSTTProvider.OpenAiCompatible -> openAi.isAvailable()
            CloudSTTProvider.WisprFlow -> wisprFlow.isAvailable() || kirinki.isAvailable()
        }

    override suspend fun isAvailable(): Boolean {
        return when (configuredMode) {
            CactusSTTMode.RemoteOnly -> remoteAvailable()
            CactusSTTMode.LocalOnly -> cactus.isLocalAvailable()
            CactusSTTMode.RemoteFirst, CactusSTTMode.LocalFirst ->
                remoteAvailable() || cactus.isModelReady
            // Rebble modes are dispatched by STTRouter and never reach this service.
            CactusSTTMode.RebbleOnly,
            CactusSTTMode.RebbleFirst,
            CactusSTTMode.RebbleFallback -> false
        }
    }

    private data class RoutedResult(
        val text: String?,
        val modeUsed: CactusSTTMode,
        val modelUsed: String?
    )

    // willFallbackLocal == false enables Kirinki as a WisprFlow backup with more lenient timeouts;
    // for OpenAI it only relaxes the timeout (there is no Kirinki backup).
    private suspend fun remoteTranscribe(
        audio: ByteArray,
        sampleRate: Int,
        language: STTLanguage,
        conversationContext: STTConversationContext?,
        dictionaryContext: List<String>?,
        contentContext: String?,
        willFallbackLocal: Boolean
    ): TranscriptionSessionStatus.Transcription {
        if (sttConfig.cloudProvider == CloudSTTProvider.OpenAiCompatible) {
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

    // NoSpeechDetected is re-thrown as a valid (non-failure) result; any other error propagates so a
    // local fallback can take over. Unlike the WisprFlow path there is no Kirinki backup.
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

    private suspend fun route(
        audio: ByteArray,
        sampleRate: Int,
        language: STTLanguage,
        conversationContext: STTConversationContext?,
        dictionaryContext: List<String>?,
        contentContext: String?,
    ): RoutedResult {
        suspend fun remote(willFallbackLocal: Boolean): TranscriptionSessionStatus.Transcription =
            remoteTranscribe(
                audio = audio,
                sampleRate = sampleRate,
                language = language,
                conversationContext = conversationContext,
                dictionaryContext = dictionaryContext,
                contentContext = contentContext,
                willFallbackLocal = willFallbackLocal,
            )

        logger.d { "Using transcription mode ${sttConfig.mode}" }
        return when (val sttMode = sttConfig.mode) {
            CactusSTTMode.RemoteOnly -> {
                val result = remote(willFallbackLocal = false)
                RoutedResult(result.text, sttMode, result.modelUsed)
            }
            CactusSTTMode.LocalOnly -> {
                val text = cactus.transcribeLocal(audio, sampleRate)
                RoutedResult(text, sttMode, configuredModel)
            }
            CactusSTTMode.RemoteFirst -> {
                try {
                    val result = remote(willFallbackLocal = true)
                    RoutedResult(result.text, sttMode, result.modelUsed)
                } catch (e: TimeoutCancellationException) {
                    logger.w(e) { "Remote transcription timeout, falling back to local: ${e.message}" }
                    val text = cactus.transcribeLocal(audio, sampleRate)
                    RoutedResult(text, CactusSTTMode.LocalOnly, configuredModel)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.w(e) { "Remote transcription failed, falling back to local: ${e.message}" }
                    val text = cactus.transcribeLocal(audio, sampleRate)
                    RoutedResult(text, CactusSTTMode.LocalOnly, configuredModel)
                }
            }
            CactusSTTMode.LocalFirst -> {
                try {
                    val text = cactus.transcribeLocal(audio, sampleRate, timeout = 8.seconds)
                    // Treat an empty/no-speech local result as a failure so we fall back to
                    // remote, as remote is more accurate.
                    validateContainsSpeech(text, configuredModel)
                    RoutedResult(text, sttMode, configuredModel)
                } catch (e: TimeoutCancellationException) {
                    logger.w(e) { "Local transcription timed out, falling back to remote: ${e.message}" }
                    val result = remote(willFallbackLocal = false)
                    RoutedResult(result.text, CactusSTTMode.RemoteOnly, result.modelUsed)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.w(e) { "Local transcription failed, falling back to remote: ${e.message}" }
                    val result = remote(willFallbackLocal = false)
                    RoutedResult(result.text, CactusSTTMode.RemoteOnly, result.modelUsed)
                }
            }
            // Rebble modes are routed by STTRouter and never reach this service.
            CactusSTTMode.RebbleOnly,
            CactusSTTMode.RebbleFirst,
            CactusSTTMode.RebbleFallback ->
                error("Rebble mode $sttMode should be handled by STTRouter, not HybridTranscriptionService")
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
        logger.d { "HybridTranscriptionService.transcribe() called" }
        // Kick off local model init concurrently with audio collection so it's warm if we need it.
        earlyInit()
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
            val start = Clock.System.now()
            val (text, modeUsed, modelUsed) = route(
                audio = buffer.readByteArray(),
                sampleRate = sampleRate,
                language = language,
                conversationContext = conversationContext,
                dictionaryContext = dictionaryContext,
                contentContext = contentContext,
            )
            val duration = Clock.System.now() - start
            logger.d { "Transcription completed in $duration" }

            validateContainsSpeech(text, modelUsed)
            if (text != null) _lastSuccessfulMode = modeUsed

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
            logger.e(e) { "Uncaught timeout during transcription" }
            throw TranscriptionException.TranscriptionServiceUnavailable(modelUsed = configuredModel)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.e(e) { "Transcription failed: ${e.message}" }
            throw e
        }
    }
}
