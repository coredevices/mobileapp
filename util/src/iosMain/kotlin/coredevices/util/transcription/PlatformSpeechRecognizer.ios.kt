package coredevices.util.transcription

import coredevices.util.hasSpeechRecognitionAuthorization
import coredevices.util.models.ModelDownloadStatus
import coredevices.util.models.inProgress
import coredevices.util.writeWavHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    /** Completes with one of [AssetStatus]. */
    @Volatile
    var assetStatus: ((localeTag: String?, completion: (String) -> Unit) -> Unit)? = null

    /** Progress is 0..1; completion carries an error description, or null on success. */
    @Volatile
    var downloadAssets: ((localeTag: String?, onProgress: (Double) -> Unit, completion: (String?) -> Unit) -> Unit)? = null

    /** Populated once the Swift side finishes its async locale query, shortly after launch. */
    val supportedLanguageTags = MutableStateFlow<List<String>>(emptyList())

    /** Strings the Swift side reports from `AssetInventory.status`. */
    object AssetStatus {
        const val INSTALLED = "installed"
        const val DOWNLOADING = "downloading"
        const val NOT_DOWNLOADED = "notDownloaded"
        const val UNSUPPORTED = "unsupported"
    }
}

/** Apple SpeechAnalyzer/SpeechTranscriber (iOS 26+) via the Swift bridge. */
actual class PlatformSpeechRecognizer {
    // The system caps concurrent SpeechAnalyzer sessions; serialize like Cactus does.
    private val mutex = Mutex()
    private val _downloadStatus = MutableStateFlow<ModelDownloadStatus>(ModelDownloadStatus.Idle)

    actual suspend fun isAvailable(): Boolean =
        NativeSpeechAnalyzerBridge.isSupported?.invoke() == true

    actual suspend fun isAuthorized(): Boolean = hasSpeechRecognitionAuthorization()

    actual val supportedLanguageTags: StateFlow<List<String>> =
        NativeSpeechAnalyzerBridge.supportedLanguageTags

    actual val downloadStatus: StateFlow<ModelDownloadStatus> = _downloadStatus.asStateFlow()

    actual suspend fun modelAvailability(languageTag: String?): SpeechModelAvailability {
        if (_downloadStatus.value.inProgress) return SpeechModelAvailability.Downloading
        val query = NativeSpeechAnalyzerBridge.assetStatus ?: return SpeechModelAvailability.Unsupported
        val status = suspendCancellableCoroutine { cont ->
            query(languageTag?.let { toBcp47(it, null) }) { cont.resume(it) { _, _, _ -> } }
        }
        return when (status) {
            NativeSpeechAnalyzerBridge.AssetStatus.INSTALLED -> SpeechModelAvailability.Installed
            NativeSpeechAnalyzerBridge.AssetStatus.DOWNLOADING -> SpeechModelAvailability.Downloading
            NativeSpeechAnalyzerBridge.AssetStatus.NOT_DOWNLOADED -> SpeechModelAvailability.NotDownloaded
            else -> SpeechModelAvailability.Unsupported
        }
    }

    actual fun downloadModel(languageTag: String?): Boolean {
        val download = NativeSpeechAnalyzerBridge.downloadAssets ?: return false
        if (_downloadStatus.value.inProgress) return false
        _downloadStatus.value = ModelDownloadStatus.Scheduled(PLATFORM_STT_MODEL_NAME)
        download(
            languageTag?.let { toBcp47(it, null) },
            { progress ->
                _downloadStatus.value = ModelDownloadStatus.Downloading(PLATFORM_STT_MODEL_NAME, progress.toFloat())
            },
        ) { error ->
            _downloadStatus.value = if (error == null) {
                ModelDownloadStatus.Idle
            } else {
                ModelDownloadStatus.Failed(PLATFORM_STT_MODEL_NAME, error)
            }
        }
        return true
    }

    actual suspend fun transcribe(pcm: ByteArray, sampleRate: Int, languageTag: String?): String {
        val transcribe = NativeSpeechAnalyzerBridge.transcribeWavFile
            ?: throw TranscriptionException.TranscriptionServiceUnavailable(PLATFORM_STT_MODEL_NAME)
        when (modelAvailability(languageTag)) {
            SpeechModelAvailability.Installed -> Unit
            SpeechModelAvailability.Unsupported ->
                throw TranscriptionException.NoSupportedLanguage(PLATFORM_STT_MODEL_NAME)
            SpeechModelAvailability.Downloading, SpeechModelAvailability.NotDownloaded ->
                throw TranscriptionException.TranscriptionRequiresDownload(
                    "Speech model for ${languageTag ?: "device locale"} not installed",
                    PLATFORM_STT_MODEL_NAME,
                )
        }
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
