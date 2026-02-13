package coredevices.util.transcription

sealed class TranscriptionException(message: String?, cause: Throwable?): Exception(message, cause) {
    class TranscriptionNetworkError(cause: Throwable): TranscriptionException("Network error", cause)
    class TranscriptionServiceUnavailable: TranscriptionException("Service unavailable", null)
    class TranscriptionServiceError(message: String, cause: Throwable? = null): TranscriptionException(message, cause)
    class TranscriptionRequiresDownload(message: String): TranscriptionException(message, null)
    class NoSupportedLanguage: TranscriptionException("No supported language", null)
    class NoSpeechDetected(val type: String): TranscriptionException("No speech detected ($type)", null)
}