package coredevices.util.transcription

import coredevices.analytics.CoreAnalytics
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Instant

private class RecordingAnalytics : CoreAnalytics {
    val events = mutableListOf<Pair<String, Map<String, Any>?>>()
    override fun logEvent(name: String, parameters: Map<String, Any>?) {
        events.add(name to parameters)
    }
    override suspend fun logHeartbeatState(name: String, value: Boolean, timestamp: Instant) {}
    override suspend fun processHeartbeat() {}
    override fun updateLastConnectedSerial(serial: String?) {}
    override fun updateRingTransferDurationMetric(duration: Duration) {}
    override fun updateRingLifetimeCollectionCount(serial: String, count: Int) {}
    override fun updateRingBatteryVoltage(voltageMilliV: Int) {}
}

class TranscriptionAnalyticsTest {
    @Test
    fun successEventIncludesService() {
        val analytics = RecordingAnalytics()
        analytics.logTranscriptionSuccess("wisprflow")
        assertEquals(
            listOf<Pair<String, Map<String, Any>?>>(
                TRANSCRIPTION_SUCCESS_EVENT to mapOf("service" to "wisprflow")
            ),
            analytics.events,
        )
    }

    @Test
    fun failureEventIncludesServiceAndReason() {
        val analytics = RecordingAnalytics()
        analytics.logTranscriptionFailure("cactus", "timeout")
        assertEquals(
            listOf<Pair<String, Map<String, Any>?>>(
                TRANSCRIPTION_FAILURE_EVENT to
                        mapOf("service" to "cactus", "reason" to "timeout", "desc" to "<none>")
            ),
            analytics.events,
        )
    }

    @Test
    fun failureReasonMapsExceptionTypes() {
        assertEquals(
            "not_enough_memory",
            transcriptionFailureReason(TranscriptionException.NotEnoughMemory()),
        )
        assertEquals(
            "service_unavailable",
            transcriptionFailureReason(TranscriptionException.TranscriptionServiceUnavailable()),
        )
        assertEquals(
            "network_error",
            transcriptionFailureReason(TranscriptionException.TranscriptionNetworkError(Exception("x"))),
        )
        assertEquals(
            "requires_download",
            transcriptionFailureReason(TranscriptionException.TranscriptionRequiresDownload("x")),
        )
        assertEquals(
            "no_supported_language",
            transcriptionFailureReason(TranscriptionException.NoSupportedLanguage()),
        )
        assertEquals(
            "no_speech_empty_result",
            transcriptionFailureReason(TranscriptionException.NoSpeechDetected("empty_result")),
        )
        assertEquals(
            "service_error",
            transcriptionFailureReason(TranscriptionException.TranscriptionServiceError("x")),
        )
        assertEquals("IllegalStateException", transcriptionFailureReason(IllegalStateException()))
    }
}
