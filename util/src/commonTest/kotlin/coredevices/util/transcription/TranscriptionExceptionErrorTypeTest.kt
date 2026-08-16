package coredevices.util.transcription

import coredevices.indexai.data.entity.RecordingEntryErrorType
import kotlin.test.Test
import kotlin.test.assertEquals

class TranscriptionExceptionErrorTypeTest {
    @Test
    fun noSpeechDetectedIsItsOwnErrorType() {
        assertEquals(
            RecordingEntryErrorType.no_speech,
            TranscriptionException.NoSpeechDetected("empty_result", modelUsed = "parakeet").errorType
        )
    }

    @Test
    fun networkErrorIsItsOwnErrorType() {
        assertEquals(
            RecordingEntryErrorType.transcription_network,
            TranscriptionException.TranscriptionNetworkError(RuntimeException("timeout")).errorType
        )
    }

    @Test
    fun otherFailuresAreGenericTranscriptionFailures() {
        assertEquals(
            RecordingEntryErrorType.transcription_failed,
            TranscriptionException.NotEnoughMemory("cactus").errorType
        )
        assertEquals(
            RecordingEntryErrorType.transcription_failed,
            TranscriptionException.TranscriptionServiceError("boom").errorType
        )
    }
}
