package coredevices.ring.service.recordings

sealed class RecordingException(message: String?, cause: Throwable?, val recordingEntryId: Long?): Exception(message, cause) {
    class TranscriptionException(message: String?, cause: Throwable?, recordingEntryId: Long?):
        RecordingException(message, cause, recordingEntryId)
    class AgentException(message: String?, cause: Throwable?, recordingEntryId: Long?):
        RecordingException(message, cause, recordingEntryId)
}