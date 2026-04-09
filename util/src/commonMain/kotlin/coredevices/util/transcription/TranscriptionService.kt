package coredevices.util.transcription

import coredevices.util.AudioEncoding
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlin.time.Duration

interface TranscriptionService {
    /**
     * Check if transcription service is available.
     * @return True if transcription service is available, false otherwise.
     */
    suspend fun isAvailable(): Boolean

    /**
     * Begin initialization of transcription service in the background.
     */
    fun earlyInit() {
        onInitialized.trySend(true)
    }

    /**
     * Transcribe audio stream frames to text.
     * @param audioStreamFrames Audio stream frames to transcribe (in PCM format). If null, transcription will use default mic (and requires permission).
     * @return Flow of transcription session status.
     */
    suspend fun transcribe(
        audioStreamFrames: Flow<ByteArray>?,
        sampleRate: Int = 16000,
        language: STTLanguage = STTLanguage.Automatic,
        conversationContext: STTConversationContext? = null,
        dictionaryContext: List<String>? = null,
        contentContext: String? = null,
        encoding: AudioEncoding = AudioEncoding.PCM_16BIT,
        timeout: Duration = Duration.INFINITE
    ): Flow<TranscriptionSessionStatus>

    val onInitialized: Channel<Boolean>
}

sealed interface STTLanguage {
    /**
     * STT Provider guesses language
     */
    data object Automatic : STTLanguage

    /**
     * ISO 639-1 language codes, e.g. "en", "es", "fr".
     */
    data class Specific(val languageCodes: Set<String>) : STTLanguage
}

data class STTConversationMessage(
    val role: STTConvoRole,
    val content: String,
)

data class STTConversationContext(
    val id: String,
    val participants: List<String> = emptyList(),
    val messages: List<STTConversationMessage> = emptyList()
)

enum class STTConvoRole {
    User,
    Human,
    Assistant
}