package coredevices.util.transcription

import coredevices.util.models.ModelDownloadStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Not implemented on Android yet; the settings option is hidden when unavailable. */
actual class PlatformSpeechRecognizer {
    actual suspend fun isAvailable(): Boolean = false

    actual suspend fun isAuthorized(): Boolean = false

    actual val supportedLanguageTags: StateFlow<List<String>> = MutableStateFlow(emptyList())

    actual suspend fun modelAvailability(languageTag: String?): SpeechModelAvailability =
        SpeechModelAvailability.Unsupported

    actual val downloadStatus: StateFlow<ModelDownloadStatus> = MutableStateFlow(ModelDownloadStatus.Idle)

    actual fun downloadModel(languageTag: String?): Boolean = false

    actual suspend fun transcribe(pcm: ByteArray, sampleRate: Int, languageTag: String?): String =
        throw TranscriptionException.TranscriptionServiceUnavailable(PLATFORM_STT_MODEL_NAME)
}
