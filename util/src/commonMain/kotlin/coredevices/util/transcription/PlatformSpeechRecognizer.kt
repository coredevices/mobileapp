package coredevices.util.transcription

import coredevices.util.models.ModelDownloadStatus
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
     * Install state of the engine's model for [languageTag] (a BCP-47 tag or bare ISO 639-1 code;
     * null for the device locale). The OS shares these models between apps and may evict them, so
     * this is queried rather than cached.
     */
    suspend fun modelAvailability(languageTag: String?): SpeechModelAvailability

    /** Progress of a download started by [downloadModel], mirroring the Cactus model download flow. */
    val downloadStatus: StateFlow<ModelDownloadStatus>

    /**
     * Ask the OS to install the model for [languageTag]. Returns false when the engine is
     * unavailable or a download is already in flight.
     */
    fun downloadModel(languageTag: String?): Boolean

    /**
     * Transcribe a complete PCM_16BIT mono buffer. [languageTag] is a BCP-47 tag or null for the
     * device locale. Returns the recognized text (may be blank); throws [TranscriptionException]
     * on failure, including [TranscriptionException.TranscriptionRequiresDownload] when the
     * model for the language isn't installed. Never starts a download itself.
     */
    suspend fun transcribe(pcm: ByteArray, sampleRate: Int, languageTag: String?): String
}
