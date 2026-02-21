package coredevices.util.transcription

sealed class TranscriptionException(message: String?, cause: Throwable?, val modelUsed: String?): Exception(message, cause) {
    class TranscriptionNetworkError(cause: Throwable, modelUsed: String? = null): TranscriptionException("Network error, model = $modelUsed", cause, modelUsed)
    class TranscriptionServiceUnavailable(modelUsed: String? = null): TranscriptionException("Service unavailable, model = $modelUsed", null, modelUsed)
    class TranscriptionServiceError(message: String, cause: Throwable? = null, modelUsed: String? = null): TranscriptionException(message, cause, modelUsed)
    class TranscriptionRequiresDownload(message: String, modelUsed: String? = null): TranscriptionException(message, null, modelUsed)
    class NoSupportedLanguage(modelUsed: String? = null): TranscriptionException("No supported language, model = $modelUsed", null, modelUsed)
    class NoSpeechDetected(val type: String, modelUsed: String? = null): TranscriptionException("No speech detected ($type), model = $modelUsed", null, modelUsed)
}