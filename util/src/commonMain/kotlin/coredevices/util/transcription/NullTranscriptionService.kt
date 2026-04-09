package coredevices.util.transcription

import co.touchlab.kermit.Logger
import coredevices.util.AudioEncoding
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class NullTranscriptionService: TranscriptionService {
    companion object {
        private val logger = Logger.withTag(NullTranscriptionService::class.simpleName!!)
    }

    override val onInitialized: Channel<Boolean> = Channel()
    override suspend fun isAvailable(): Boolean = true

    //TODO: Throw exception instead of placeholder implementation
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
        emit(TranscriptionSessionStatus.Open)
        logger.v { "Transcription flow opened" }
        audioStreamFrames?.collect {
            // Do nothing
        }
        delay(3.seconds)
        emit(TranscriptionSessionStatus.Transcription("This is a placeholder transcription result."))
    }
}