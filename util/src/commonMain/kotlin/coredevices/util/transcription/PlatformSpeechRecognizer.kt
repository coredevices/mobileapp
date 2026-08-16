package coredevices.util.transcription

import kotlinx.coroutines.flow.StateFlow

const val PLATFORM_STT_MODEL_NAME = "platform-native"

/**
 * OS-native on-device speech recognition. On iOS this is Apple's
 * SpeechAnalyzer/SpeechTranscriber (iOS 26+); not implemented on Android yet.
 */
expect class PlatformSpeechRecognizer() {
    suspend fun isAvailable(): Boolean

    /**
     * Whether the user has granted [coredevices.util.Permission.SpeechRecognizer]. Requesting it is
     * the permission system's job; the engine only reads the current state.
     */
    suspend fun isAuthorized(): Boolean

    /**
     * BCP-47 tags the engine can transcribe. Emits empty until the engine reports its locales
     * shortly after launch, and stays empty where it is unavailable.
     */
    val supportedLanguageTags: StateFlow<List<String>>

    /**
     * Transcribe a complete PCM_16BIT mono buffer. [languageTag] is a BCP-47 tag or null for the
     * device locale. Returns the recognized text (may be blank); throws [TranscriptionException]
     * on failure.
     */
    suspend fun transcribe(pcm: ByteArray, sampleRate: Int, languageTag: String?): String
}
