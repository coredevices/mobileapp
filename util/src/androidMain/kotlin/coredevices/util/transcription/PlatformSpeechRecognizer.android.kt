package coredevices.util.transcription

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Not implemented on Android yet; the settings option is hidden when unavailable. */
actual class PlatformSpeechRecognizer {
    actual suspend fun isAvailable(): Boolean = false

    actual suspend fun isAuthorized(): Boolean = false

    actual val supportedLanguageTags: StateFlow<List<String>> = MutableStateFlow(emptyList())

    actual suspend fun transcribe(pcm: ByteArray, sampleRate: Int, languageTag: String?): String =
        throw TranscriptionException.TranscriptionServiceUnavailable(PLATFORM_STT_MODEL_NAME)
}
