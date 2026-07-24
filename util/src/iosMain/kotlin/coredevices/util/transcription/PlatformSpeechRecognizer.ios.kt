package coredevices.util.transcription

import coredevices.util.hasSpeechRecognitionAuthorization
import coredevices.util.writeWavHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import kotlin.concurrent.Volatile
import kotlin.coroutines.resume
import kotlin.uuid.Uuid

/**
 * Swift-side SpeechAnalyzer implementation, registered at app launch (SpeechAnalyzer is a
 * Swift-only API that Kotlin/Native cannot call directly). Null on devices below iOS 26.
 */
object NativeSpeechAnalyzerBridge {
    @Volatile
    var isSupported: (() -> Boolean)? = null

    @Volatile
    var cancelTranscription: (() -> Unit)? = null

    @Volatile
    var transcribeWavFile: ((path: String, localeTag: String?, completion: (String?, String?) -> Unit) -> Unit)? = null

    /** Populated once the Swift side finishes its async locale query, shortly after launch. */
    val supportedLanguageTags = MutableStateFlow<List<String>>(emptyList())
}

/** Apple SpeechAnalyzer/SpeechTranscriber (iOS 26+) via the Swift bridge. */
actual class PlatformSpeechRecognizer {
    // The system caps concurrent SpeechAnalyzer sessions; serialize like Cactus does.
    private val mutex = Mutex()

    actual suspend fun isAvailable(): Boolean =
        NativeSpeechAnalyzerBridge.isSupported?.invoke() == true

    actual suspend fun isAuthorized(): Boolean = hasSpeechRecognitionAuthorization()

    actual val supportedLanguageTags: StateFlow<List<String>> =
        NativeSpeechAnalyzerBridge.supportedLanguageTags

    actual suspend fun transcribe(pcm: ByteArray, sampleRate: Int, languageTag: String?): String {
        val transcribe = NativeSpeechAnalyzerBridge.transcribeWavFile
            ?: throw TranscriptionException.TranscriptionServiceUnavailable(PLATFORM_STT_MODEL_NAME)
        if (!mutex.tryLock()) {
            throw TranscriptionException.TranscriptionInProgress(PLATFORM_STT_MODEL_NAME)
        }
        val path = Path(SystemTemporaryDirectory, "platform_stt_${Uuid.random()}.wav")
        try {
            withContext(Dispatchers.IO) {
                SystemFileSystem.sink(path).buffered().use { sink ->
                    sink.writeWavHeader(sampleRate, audioSize = pcm.size)
                    sink.write(pcm)
                }
            }
            val (text, error) = suspendCancellableCoroutine { cont ->
                cont.invokeOnCancellation { NativeSpeechAnalyzerBridge.cancelTranscription?.invoke() }
                transcribe(path.toString(), languageTag) { text, error ->
                    cont.resume(text to error) { _, _, _ -> }
                }
                // The bridge only has a task to cancel once transcribe() returns, so a
                // cancellation that landed before then has to be re-issued here.
                if (!cont.isActive) NativeSpeechAnalyzerBridge.cancelTranscription?.invoke()
            }
            if (error != null) {
                throw TranscriptionException.TranscriptionServiceError(error, modelUsed = PLATFORM_STT_MODEL_NAME)
            }
            return text.orEmpty()
        } finally {
            mutex.unlock()
            try {
                SystemFileSystem.delete(path)
            } catch (_: Exception) {
            }
        }
    }
}
