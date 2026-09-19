package coredevices.util.transcription

import coredevices.util.models.CactusSTTMode
import coredevices.util.models.ModelDownloadStatus
import coredevices.util.models.inProgress

/** Whether an on-device speech engine has the model it needs for the configured language. */
enum class SpeechModelAvailability {
    Installed,
    Downloading,
    NotDownloaded,
    Unsupported,
}

/**
 * Something the user has to do before on-device transcription can work. Emitted by
 * [LocalTranscriptionService] when a recording reaches an engine whose model isn't installed;
 * the app surfaces it as a notification.
 */
sealed interface SpeechModelPrompt {
    val mode: CactusSTTMode

    /** No download is in flight, so nothing will change until the user starts one. */
    data class DownloadRequired(override val mode: CactusSTTMode) : SpeechModelPrompt

    /** A download is in flight and there is no cloud fallback to cover the wait. */
    data class DownloadPending(override val mode: CactusSTTMode) : SpeechModelPrompt
}

internal fun speechModelPrompt(
    mode: CactusSTTMode,
    availability: SpeechModelAvailability,
    cloudFallback: Boolean,
): SpeechModelPrompt? = when (availability) {
    SpeechModelAvailability.NotDownloaded -> SpeechModelPrompt.DownloadRequired(mode)
    SpeechModelAvailability.Downloading -> SpeechModelPrompt.DownloadPending(mode).takeUnless { cloudFallback }
    SpeechModelAvailability.Installed, SpeechModelAvailability.Unsupported -> null
}

/** The OS model for the spoken language is missing and nothing is fetching it. */
fun platformModelNeedsDownload(
    availability: SpeechModelAvailability,
    download: ModelDownloadStatus,
): Boolean = download is ModelDownloadStatus.Failed ||
    (!download.inProgress && availability == SpeechModelAvailability.NotDownloaded)

/** Short state label for the OS model, e.g. for a settings row. */
fun platformModelState(
    availability: SpeechModelAvailability,
    download: ModelDownloadStatus,
): String = when {
    download is ModelDownloadStatus.Scheduled -> "Download scheduled"
    download is ModelDownloadStatus.Downloading ->
        download.progress?.let { "Downloading ${(it * 100).toInt()}%" } ?: "Downloading…"
    download is ModelDownloadStatus.Failed -> "Download failed"
    availability == SpeechModelAvailability.Installed -> "Downloaded"
    availability == SpeechModelAvailability.Downloading -> "Downloading…"
    availability == SpeechModelAvailability.NotDownloaded -> "Not downloaded"
    else -> "Not supported"
}

/** Body of the download dialog for the OS model, or the wait message while one is in flight. */
fun platformModelDialogMessage(
    spokenLanguage: String?,
    availability: SpeechModelAvailability,
    download: ModelDownloadStatus,
): String {
    val language = spokenLanguageLabel(spokenLanguage)
    return when {
        download.inProgress || availability == SpeechModelAvailability.Downloading ->
            "iOS is downloading the speech model for $language. Ensure your connection is stable and isn't metered."
        download is ModelDownloadStatus.Failed ->
            "The speech model download for $language failed (${download.errorMessage}). " +
                "Data charges may apply, Wi-Fi is recommended."
        availability == SpeechModelAvailability.Installed ->
            "The speech model for $language is downloaded."
        else ->
            "To use iOS speech recognition for $language, iOS needs to download a speech model " +
                "first. Data charges may apply, Wi-Fi is recommended."
    }
}
