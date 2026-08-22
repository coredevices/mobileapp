package coredevices.ring.external.indexlocal

import coredevices.ring.audio.M4aEncoder
import coredevices.ring.service.RecordingBackgroundScope
import coredevices.ring.service.button.RingGesture
import kotlin.time.Duration
import kotlin.time.Instant

actual class IndexLocalCaptureApiImpl actual constructor(
    m4aEncoder: M4aEncoder,
    scope: RecordingBackgroundScope,
) : IndexLocalCaptureApi {

    override fun isNotesnookInstalled(): Boolean = false

    override fun isCapturingRecording(): Boolean = false
    override fun beginRecordingCapture() = Unit
    override fun endRecordingCapture() = Unit

    override fun deliverIfEnabled(
        samples: ShortArray?,
        sampleRate: Int,
        recordingId: String,
        transcription: String?,
        recordedAt: Instant,
        gesture: RingGesture,
    ) = Unit

    override fun deliverReminder(
        title: String,
        deadline: Instant?,
        notifyBefore: Duration?,
        recordingId: String,
        recordedAt: Instant,
    ) = Unit
}
