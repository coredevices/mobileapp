package coredevices.util.transcription

import co.touchlab.kermit.Logger
import coredevices.util.AudioEncoding
import coredevices.util.CoreConfigFlow
import coredevices.util.models.CactusSTTMode
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

/**
 * Mode-aware [TranscriptionService] owning local↔remote fallback for the [CactusSTTMode] options.
 * Local runs on [CactusTranscriptionService]; the remote backend is whichever injected
 * [RemoteTranscriber] matches [coredevices.util.STTConfig.cloudProvider], so adding a cloud backend
 * needs no change here.
 */
class HybridTranscriptionService(
    private val coreConfigFlow: CoreConfigFlow,
    private val cactus: CactusTranscriptionService,
    remoteTranscribers: Set<RemoteTranscriber>,
) : TranscriptionService {
    companion object {
        private val logger = Logger.withTag("HybridTranscriptionService")
    }

    private val remotesByProvider = remoteTranscribers.associateBy { it.provider }

    // Read fresh from the config StateFlow on every access so a runtime mode/model change takes
    // effect without restarting the app (not cached in a stateIn that nothing collects).
    private val sttConfig get() = coreConfigFlow.value.sttConfig

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

    private fun remoteTranscriber(): RemoteTranscriber =
        remotesByProvider[sttConfig.cloudProvider]
            ?: error("No RemoteTranscriber registered for ${sttConfig.cloudProvider}")

    private suspend fun remoteAvailable(): Boolean = remoteTranscriber().isAvailable()

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

    private suspend fun route(
        audio: ByteArray,
        sampleRate: Int,
        language: STTLanguage,
        conversationContext: STTConversationContext?,
        dictionaryContext: List<String>?,
        contentContext: String?,
    ): RoutedResult {
        suspend fun remote(willFallbackLocal: Boolean): TranscriptionSessionStatus.Transcription =
            remoteTranscriber().transcribe(
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
