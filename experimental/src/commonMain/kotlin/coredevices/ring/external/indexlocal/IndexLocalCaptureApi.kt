package coredevices.ring.external.indexlocal

import coredevices.ring.audio.M4aEncoder
import coredevices.ring.service.RecordingBackgroundScope
import coredevices.ring.service.button.RingGesture
import kotlin.time.Duration
import kotlin.time.Instant

interface IndexLocalCaptureApi {
    /**
     * Deliver recording audio + transcription to Notesnook on this phone.
     * No-op when Notesnook is not installed. Android-only.
     *
     * If [beginRecordingCapture] is active and a reminder was stashed, this
     * sends one combined capture (audio + reminder) so Notesnook creates a
     * single note.
     */
    fun deliverIfEnabled(
        samples: ShortArray?,
        sampleRate: Int,
        recordingId: String,
        transcription: String?,
        recordedAt: Instant,
        gesture: RingGesture,
    )

    /**
     * Deliver an Index reminder as a Notesnook note with a reminder alarm.
     * [deadline] is the absolute due instant (UTC). Null means no due date
     * (Notesnook permanent reminder).
     *
     * While a recording capture is in progress this only stashes the reminder
     * so [deliverIfEnabled] can send audio + reminder together.
     */
    fun deliverReminder(
        title: String,
        deadline: Instant?,
        notifyBefore: Duration?,
        recordingId: String,
        recordedAt: Instant,
    )

    /** True while the recording decorator is running the Index agent. */
    fun isCapturingRecording(): Boolean

    fun beginRecordingCapture()

    fun endRecordingCapture()

    fun isNotesnookInstalled(): Boolean
}

data class IndexLocalCaptureRunResult(
    val ok: Boolean,
    val status: String,
    val detail: String,
    val byteSize: Long,
    val durationMs: Long,
)

expect class IndexLocalCaptureApiImpl(
    m4aEncoder: M4aEncoder,
    scope: RecordingBackgroundScope,
) : IndexLocalCaptureApi
