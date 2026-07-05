package coredevices.util.transcription

import co.touchlab.kermit.Logger
import coredevices.analytics.CoreAnalytics
import coredevices.util.CloudSTTProvider
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.seconds

class OpenAiRemoteTranscriber(
    private val openAi: OpenAiTranscriptionService,
    private val analytics: CoreAnalytics,
) : RemoteTranscriber {
    companion object {
        private val logger = Logger.withTag("OpenAiRemoteTranscriber")
    }

    override val provider = CloudSTTProvider.OpenAiCompatible

    override suspend fun isAvailable(): Boolean = openAi.isAvailable()

    override suspend fun transcribe(
        audio: ByteArray,
        sampleRate: Int,
        language: STTLanguage,
        conversationContext: STTConversationContext?,
        dictionaryContext: List<String>?,
        contentContext: String?,
        willFallbackLocal: Boolean,
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
            if (e is TranscriptionException.NoSpeechDetected) throw e // NoSpeechDetected is a valid result, not a failure of the service
            logger.w(e) { "OpenAI transcription failed, propagating to caller: ${e.message}" }
            throw e
        }
    }
}
