package coredevices.util.transcription

import co.touchlab.kermit.Logger
import coredevices.analytics.CoreAnalytics
import coredevices.util.CloudSTTProvider
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class WisprFlowRemoteTranscriber(
    private val wisprFlow: WisprFlowRESTTranscriptionService,
    private val kirinki: KirinkiTranscriptionService,
    private val analytics: CoreAnalytics,
) : RemoteTranscriber {
    companion object {
        private val logger = Logger.withTag("WisprFlowRemoteTranscriber")
        private val wisprSkipInterval = 1.seconds
    }

    override val provider = CloudSTTProvider.WisprFlow

    private val lastErrorMutex = Mutex()
    private var lastWisprError = Instant.DISTANT_PAST

    override suspend fun isAvailable(): Boolean = wisprFlow.isAvailable() || kirinki.isAvailable()

    override suspend fun transcribe(
        audio: ByteArray,
        sampleRate: Int,
        language: STTLanguage,
        conversationContext: STTConversationContext?,
        dictionaryContext: List<String>?,
        contentContext: String?,
        willFallbackLocal: Boolean,
    ): TranscriptionSessionStatus.Transcription {
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
}
