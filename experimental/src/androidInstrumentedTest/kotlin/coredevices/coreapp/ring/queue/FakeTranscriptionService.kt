package coredevices.coreapp.ring.queue

import coredevices.util.AudioEncoding
import coredevices.util.transcription.STTConversationContext
import coredevices.util.transcription.STTLanguage
import coredevices.util.transcription.TranscriptionException
import coredevices.util.transcription.TranscriptionService
import coredevices.util.transcription.TranscriptionSessionStatus
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.io.IOException
import kotlin.time.Duration

class FakeTranscriptionService : TranscriptionService {
    private val behaviorQueue = ArrayDeque<Behavior>()
    override val onInitialized: Channel<Boolean> = Channel()

    sealed class Behavior {
        data class Success(val text: String = "Hello world") : Behavior()
        data object NetworkError : Behavior()
        data object NoSpeech : Behavior()
    }

    fun enqueue(vararg behaviors: Behavior) {
        behaviorQueue.addAll(behaviors)
    }

    override suspend fun isAvailable(): Boolean = true

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
        val behavior = behaviorQueue.removeFirst()
        when (behavior) {
            is Behavior.Success -> {
                emit(TranscriptionSessionStatus.Transcription(behavior.text))
            }
            is Behavior.NetworkError -> {
                throw TranscriptionException.TranscriptionNetworkError(IOException("Fake network error"))
            }
            is Behavior.NoSpeech -> {
                throw TranscriptionException.NoSpeechDetected("silence")
            }
        }
    }
}