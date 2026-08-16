package coredevices.util.transcription

import coredevices.indexai.data.entity.RecordingEntryErrorType

sealed class TranscriptionException(message: String?, cause: Throwable?, val modelUsed: String?): Exception(message, cause) {
    /** How this failure is persisted on the recording entry. */
    abstract val errorType: RecordingEntryErrorType

    class TranscriptionNetworkError(cause: Throwable, modelUsed: String? = null): TranscriptionException("Network error, model = $modelUsed", cause, modelUsed) {
        override val errorType = RecordingEntryErrorType.transcription_network
    }
    open class TranscriptionServiceUnavailable(modelUsed: String? = null): TranscriptionException("Service unavailable, model = $modelUsed", null, modelUsed) {
        override val errorType = RecordingEntryErrorType.transcription_failed
    }
    /** Another transcription is already running on the single native model handle; this attempt should be deferred and retried, not failed. */
    class TranscriptionInProgress(modelUsed: String? = null): TranscriptionException("Transcription already in progress, model = $modelUsed", null, modelUsed) {
        override val errorType = RecordingEntryErrorType.transcription_failed
    }
    class NotEnoughMemory(modelUsed: String? = null): TranscriptionServiceUnavailable(modelUsed)
    class TranscriptionServiceError(message: String, cause: Throwable? = null, modelUsed: String? = null): TranscriptionException(message, cause, modelUsed) {
        override val errorType = RecordingEntryErrorType.transcription_failed
    }
    class TranscriptionRequiresDownload(message: String, modelUsed: String? = null): TranscriptionException(message, null, modelUsed) {
        override val errorType = RecordingEntryErrorType.transcription_failed
    }
    class NoSupportedLanguage(modelUsed: String? = null): TranscriptionException("No supported language, model = $modelUsed", null, modelUsed) {
        override val errorType = RecordingEntryErrorType.transcription_failed
    }
    class NoSpeechDetected(val type: String, modelUsed: String? = null): TranscriptionException("No speech detected ($type), model = $modelUsed", null, modelUsed) {
        override val errorType = RecordingEntryErrorType.no_speech
    }
}
