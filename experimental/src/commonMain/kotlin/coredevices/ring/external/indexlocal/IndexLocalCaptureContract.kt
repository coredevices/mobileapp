package coredevices.ring.external.indexlocal

import coredevices.ring.external.indexwebhook.IndexWebhookPayloadMode
import coredevices.ring.service.button.RingGesture

/**
 * Shared names for the on-device Index → Notesnook intent. Keep these in lockstep
 * with Notesnook's `PebbleIndexContract`. They are the public ABI: do not rename.
 */
object IndexLocalCaptureContract {
    const val NOTESNOOK_PACKAGE = "com.streetwriters.notesnook"
    const val SERVICE_CLASS = "com.streetwriters.notesnook.pebble.PebbleIndexCaptureService"
    const val RECEIVER_CLASS = "com.streetwriters.notesnook.pebble.PebbleIndexCaptureReceiver"
    const val ACTIVITY_CLASS = "com.streetwriters.notesnook.pebble.PebbleIndexCaptureActivity"

    const val ACTION = "com.streetwriters.notesnook.action.INDEX_CAPTURE"
    const val RECEIVE_PERMISSION = "com.streetwriters.notesnook.permission.RECEIVE_INDEX_CAPTURE"

    const val FILE_PROVIDER_AUTHORITY = "coredevices.coreapp.fileprovider"
    const val CACHE_SUBDIR = "index-local"

    const val EXTRA_TRANSCRIPTION = "transcription"
    const val EXTRA_RECORDED_AT = "recordedAt"
    const val EXTRA_CLIENT = "client"
    const val EXTRA_RECORDING_ID = "recordingId"
    const val EXTRA_TRIGGER = "trigger"
    const val EXTRA_PAYLOAD_MODE = "payloadMode"
    const val EXTRA_AUDIO_SIZE = "audioSize"
    const val EXTRA_AUDIO_URI = "audioUri"
    const val EXTRA_AUDIO_BASE64 = "audioBase64"
    const val EXTRA_TEST = "test"
    const val EXTRA_KIND = "kind"
    const val EXTRA_TITLE = "title"
    const val EXTRA_HAS_DEADLINE = "hasDeadline"
    const val EXTRA_DEADLINE_EPOCH_MS = "deadlineEpochMs"
    const val EXTRA_DEADLINE_ISO = "deadlineIso"
    const val EXTRA_NOTIFY_BEFORE_SECONDS = "notifyBeforeSeconds"

    const val CLIENT_RING = "ring"

    const val KIND_NOTE = "note"
    const val KIND_REMINDER = "reminder"

    const val TRIGGER_SINGLE = "single-click-hold"
    const val TRIGGER_DOUBLE = "double-click-hold"
    const val TRIGGER_TEST = "test-event"

    const val MODE_RECORDING = "recording"
    const val MODE_TRANSCRIPTION = "transcription"
    const val MODE_BOTH = "both"

    const val TEST_TRANSCRIPTION = "Index local-capture test event"
}

fun RingGesture.localCaptureTriggerValue(): String = when (this) {
    RingGesture.ClickHold -> IndexLocalCaptureContract.TRIGGER_DOUBLE
    else -> IndexLocalCaptureContract.TRIGGER_SINGLE
}

fun IndexWebhookPayloadMode.wireName(): String = when (this) {
    IndexWebhookPayloadMode.RecordingOnly -> IndexLocalCaptureContract.MODE_RECORDING
    IndexWebhookPayloadMode.TranscriptionOnly -> IndexLocalCaptureContract.MODE_TRANSCRIPTION
    IndexWebhookPayloadMode.Both -> IndexLocalCaptureContract.MODE_BOTH
}
