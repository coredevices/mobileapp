package coredevices.util.transcription

import coredevices.util.AudioEncoding
import kotlin.time.Duration
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow

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
     * @param initialTimeout Optional override for the initial transcription attempt timeout; when null the service's own default is used.
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
        initialTimeout: Duration? = null,
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

    companion object {
        /**
         * Resolve an ISO 639-1 language code into an [STTLanguage]. Null/blank -> [Automatic].
         */
        fun fromCodeOrAutomatic(code: String?): STTLanguage =
            code?.takeIf { it.isNotBlank() }?.let { Specific(setOf(it)) } ?: Automatic
    }
}

/**
 * Curated list of ISO 639-1 language codes for user-selectable spoken language settings.
 * Pairs are (code, English display name).
 */
expect val SpokenLanguageOptions: List<Pair<String, String>>

/**
 * Qualify a bare ISO 639-1 [languageCode] with a [region] (e.g. the device region from
 * `Locale.current.region`) to form a BCP-47 tag, e.g. ("en", "US") -> "en-US". When no region is
 * supplied, falls back to the language's canonical region (CLDR likely-subtags, e.g. "es" -> "es-ES").
 * Returns the bare code when the code is already region/script-qualified or no region can be resolved.
 */
internal fun toBcp47(languageCode: String, region: String?): String {
    if ('-' in languageCode || '_' in languageCode) return languageCode
    val resolved = region?.takeIf { it.isNotBlank() } ?: canonicalRegionForLanguage(languageCode)
    return if (resolved != null) "$languageCode-${resolved.uppercase()}" else languageCode
}

/** The canonical (most common) region for an ISO 639-1 language, or null if unknown. */
internal fun canonicalRegionForLanguage(languageCode: String): String? =
    canonicalRegions[languageCode.lowercase()]

/**
 * CLDR "likely subtags" region for the most common variant of each language. Used to qualify a
 * spoken-language selection into a BCP-47 tag when the device locale carries no region of its own.
 */
private val canonicalRegions: Map<String, String> = mapOf(
    "en" to "US", "es" to "ES", "fr" to "FR", "de" to "DE", "it" to "IT", "pt" to "BR",
    "nl" to "NL", "ru" to "RU", "pl" to "PL", "uk" to "UA", "cs" to "CZ", "sk" to "SK",
    "hu" to "HU", "ro" to "RO", "bg" to "BG", "hr" to "HR", "sr" to "RS", "sl" to "SI",
    "bs" to "BA", "el" to "GR", "tr" to "TR", "sv" to "SE", "da" to "DK", "fi" to "FI",
    "no" to "NO", "nb" to "NO", "nn" to "NO", "is" to "IS", "et" to "EE", "lv" to "LV",
    "lt" to "LT", "ga" to "IE", "cy" to "GB", "sq" to "AL", "mk" to "MK", "ca" to "ES",
    "eu" to "ES", "gl" to "ES", "ar" to "EG", "he" to "IL", "fa" to "IR", "ps" to "AF",
    "ur" to "PK", "hi" to "IN", "bn" to "BD", "pa" to "IN", "gu" to "IN", "ta" to "IN",
    "te" to "IN", "kn" to "IN", "ml" to "IN", "mr" to "IN", "ne" to "NP", "si" to "LK",
    "th" to "TH", "lo" to "LA", "km" to "KH", "my" to "MM", "vi" to "VN", "id" to "ID",
    "ms" to "MY", "tl" to "PH", "fil" to "PH", "zh" to "CN", "ja" to "JP", "ko" to "KR",
    "sw" to "TZ", "am" to "ET", "so" to "SO", "ha" to "NG", "yo" to "NG", "ig" to "NG",
    "zu" to "ZA", "xh" to "ZA", "af" to "ZA", "az" to "AZ", "ka" to "GE", "hy" to "AM",
    "kk" to "KZ", "ky" to "KG", "uz" to "UZ", "mn" to "MN",
)

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