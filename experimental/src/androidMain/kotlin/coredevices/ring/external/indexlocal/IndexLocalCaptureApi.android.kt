package coredevices.ring.external.indexlocal

import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Base64
import androidx.core.content.FileProvider
import co.touchlab.kermit.Logger
import coredevices.ring.audio.M4aEncoder
import coredevices.ring.external.indexwebhook.IndexWebhookPayloadMode
import coredevices.ring.service.RecordingBackgroundScope
import coredevices.ring.service.button.RingGesture
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File
import kotlin.time.Duration
import kotlin.time.Instant
import kotlin.time.TimeSource

actual class IndexLocalCaptureApiImpl actual constructor(
    private val m4aEncoder: M4aEncoder,
    private val scope: RecordingBackgroundScope,
) : IndexLocalCaptureApi, KoinComponent {

    private val context: Context by inject()

    companion object {
        private val logger = Logger.withTag("IndexLocalCaptureApi")
    }

    @Volatile private var capturingRecording = false
    @Volatile private var pendingReminder: PendingReminder? = null

    private data class PendingReminder(
        val title: String,
        val deadline: Instant?,
        val notifyBefore: Duration?,
        val recordingId: String,
        val recordedAt: Instant,
    )

    override fun isNotesnookInstalled(): Boolean = try {
        context.packageManager.getPackageInfo(IndexLocalCaptureContract.NOTESNOOK_PACKAGE, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    } catch (_: Exception) {
        false
    }

    override fun isCapturingRecording(): Boolean = capturingRecording

    override fun beginRecordingCapture() {
        capturingRecording = true
        pendingReminder = null
    }

    override fun endRecordingCapture() {
        capturingRecording = false
    }

    override fun deliverIfEnabled(
        samples: ShortArray?,
        sampleRate: Int,
        recordingId: String,
        transcription: String?,
        recordedAt: Instant,
        gesture: RingGesture,
    ) {
        val reminder = pendingReminder
        pendingReminder = null
        scope.launch {
            try {
                val m4aData = samples?.let { m4aEncoder.encode(it, sampleRate) }
                val result = deliver(
                    gesture = gesture,
                    recordingId = reminder?.recordingId ?: recordingId,
                    transcription = transcription,
                    recordedAt = recordedAt,
                    audioData = m4aData,
                    payloadMode = IndexWebhookPayloadMode.Both,
                    isTest = false,
                    kind = if (reminder != null) {
                        IndexLocalCaptureContract.KIND_REMINDER
                    } else {
                        IndexLocalCaptureContract.KIND_NOTE
                    },
                    title = reminder?.title,
                    deadline = reminder?.deadline,
                    hasDeadline = reminder != null && reminder.deadline != null,
                    notifyBeforeSeconds = reminder?.notifyBefore?.inWholeSeconds,
                )
                if (result.ok) {
                    logger.i { "Local capture delivered $recordingId to Notesnook" }
                } else {
                    logger.e { "Local capture failed for $recordingId: ${result.status} ${result.detail}" }
                }
            } catch (e: Exception) {
                logger.e(e) { "Error during local capture for $recordingId" }
            }
        }
    }

    override fun deliverReminder(
        title: String,
        deadline: Instant?,
        notifyBefore: Duration?,
        recordingId: String,
        recordedAt: Instant,
    ) {
        if (capturingRecording) {
            pendingReminder = PendingReminder(
                title = title,
                deadline = deadline,
                notifyBefore = notifyBefore,
                recordingId = recordingId,
                recordedAt = recordedAt,
            )
            logger.i {
                "Stashed reminder for recording capture title=$title deadlineMs=${deadline?.toEpochMilliseconds()} iso=${deadline?.toString()}"
            }
            return
        }
        scope.launch {
            try {
                val result = deliver(
                    gesture = RingGesture.Hold,
                    recordingId = recordingId,
                    transcription = title,
                    recordedAt = recordedAt,
                    audioData = null,
                    payloadMode = IndexWebhookPayloadMode.TranscriptionOnly,
                    isTest = false,
                    kind = IndexLocalCaptureContract.KIND_REMINDER,
                    title = title,
                    deadline = deadline,
                    hasDeadline = deadline != null,
                    notifyBeforeSeconds = notifyBefore?.inWholeSeconds,
                )
                if (result.ok) {
                    logger.i {
                        "Reminder delivered $recordingId deadline=${deadline?.toEpochMilliseconds()} iso=${deadline?.toString()}"
                    }
                } else {
                    logger.e { "Reminder capture failed for $recordingId: ${result.status} ${result.detail}" }
                }
            } catch (e: Exception) {
                logger.e(e) { "Error during reminder capture for $recordingId" }
            }
        }
    }

    private fun deliver(
        gesture: RingGesture,
        recordingId: String,
        transcription: String?,
        recordedAt: Instant,
        audioData: ByteArray?,
        payloadMode: IndexWebhookPayloadMode,
        isTest: Boolean,
        kind: String = IndexLocalCaptureContract.KIND_NOTE,
        title: String? = null,
        deadline: Instant? = null,
        hasDeadline: Boolean = false,
        notifyBeforeSeconds: Long? = null,
    ): IndexLocalCaptureRunResult {
        val started = TimeSource.Monotonic.markNow()
        if (!isNotesnookInstalled()) {
            return IndexLocalCaptureRunResult(
                ok = false,
                status = "NOT INSTALLED",
                detail = "Notesnook is not installed",
                byteSize = 0,
                durationMs = started.elapsedNow().inWholeMilliseconds,
            )
        }

        val audioUri: Uri? = audioData?.let { bytes ->
            val dir = File(context.cacheDir, IndexLocalCaptureContract.CACHE_SUBDIR).apply { mkdirs() }
            val file = File(dir, "$recordingId.m4a")
            file.writeBytes(bytes)
            FileProvider.getUriForFile(
                context,
                IndexLocalCaptureContract.FILE_PROVIDER_AUTHORITY,
                file,
            )
        }

        val payload = Intent(IndexLocalCaptureContract.ACTION).apply {
            `package` = IndexLocalCaptureContract.NOTESNOOK_PACKAGE
            putExtra(IndexLocalCaptureContract.EXTRA_RECORDED_AT, recordedAt.toEpochMilliseconds())
            putExtra(IndexLocalCaptureContract.EXTRA_CLIENT, IndexLocalCaptureContract.CLIENT_RING)
            putExtra(IndexLocalCaptureContract.EXTRA_RECORDING_ID, recordingId)
            putExtra(
                IndexLocalCaptureContract.EXTRA_TRIGGER,
                if (isTest) IndexLocalCaptureContract.TRIGGER_TEST else gesture.localCaptureTriggerValue(),
            )
            putExtra(IndexLocalCaptureContract.EXTRA_PAYLOAD_MODE, payloadMode.wireName())
            putExtra(IndexLocalCaptureContract.EXTRA_KIND, kind)
            if (title != null) putExtra(IndexLocalCaptureContract.EXTRA_TITLE, title)
            if (kind == IndexLocalCaptureContract.KIND_REMINDER) {
                putExtra(IndexLocalCaptureContract.EXTRA_HAS_DEADLINE, hasDeadline)
                if (deadline != null) {
                    putExtra(
                        IndexLocalCaptureContract.EXTRA_DEADLINE_EPOCH_MS,
                        deadline.toEpochMilliseconds(),
                    )
                    putExtra(IndexLocalCaptureContract.EXTRA_DEADLINE_ISO, deadline.toString())
                }
                notifyBeforeSeconds?.let {
                    putExtra(IndexLocalCaptureContract.EXTRA_NOTIFY_BEFORE_SECONDS, it)
                }
            }
            if (isTest) putExtra(IndexLocalCaptureContract.EXTRA_TEST, true)
            if (transcription != null) {
                putExtra(IndexLocalCaptureContract.EXTRA_TRANSCRIPTION, transcription)
            }
            if (audioUri != null && audioData != null) {
                // String extra survives startForegroundService even when ClipData /
                // EXTRA_STREAM Uri parcelables are stripped on Android 13–17.
                setDataAndType(audioUri, "audio/mp4")
                putExtra(Intent.EXTRA_STREAM, audioUri)
                putExtra(IndexLocalCaptureContract.EXTRA_AUDIO_URI, audioUri.toString())
                putExtra(IndexLocalCaptureContract.EXTRA_AUDIO_SIZE, audioData.size)
                // Binder extras are capped ~1 MB. Typical Index holds are well under
                // this; URI copy is still preferred, this is a grant-stripping fallback.
                if (audioData.size <= 400 * 1024) {
                    putExtra(
                        IndexLocalCaptureContract.EXTRA_AUDIO_BASE64,
                        Base64.encodeToString(audioData, Base64.NO_WRAP),
                    )
                }
                clipData = ClipData.newUri(context.contentResolver, "audio", audioUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } else {
                type = "text/plain"
            }
        }

        if (audioUri != null) {
            context.grantUriPermission(
                IndexLocalCaptureContract.NOTESNOOK_PACKAGE,
                audioUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }

        val byteSize = (audioData?.size ?: 0).toLong() + (transcription?.length ?: 0)
        fun targeted(className: String): Intent = Intent(payload).apply {
            component = ComponentName(IndexLocalCaptureContract.NOTESNOOK_PACKAGE, className)
            `package` = IndexLocalCaptureContract.NOTESNOOK_PACKAGE
        }

        fun ok(status: String) = IndexLocalCaptureRunResult(
            ok = true,
            status = status,
            detail = contentsLabel(audioData != null, transcription != null),
            byteSize = byteSize,
            durationMs = started.elapsedNow().inWholeMilliseconds,
        )

        try {
            val serviceIntent = targeted(IndexLocalCaptureContract.SERVICE_CLASS)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
            logger.i {
                "Delivered $recordingId via capture service audio=${audioUri != null} bytes=${audioData?.size ?: 0}"
            }
            return ok("SERVICE")
        } catch (serviceError: Exception) {
            logger.w(serviceError) { "startForegroundService failed, trying activity trampoline" }
        }

        try {
            val activityIntent = targeted(IndexLocalCaptureContract.ACTIVITY_CLASS).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            context.startActivity(activityIntent)
            logger.i { "Delivered $recordingId via capture activity" }
            return ok("ACTIVITY")
        } catch (activityError: Exception) {
            logger.w(activityError) { "startActivity trampoline failed, trying broadcast" }
        }

        return try {
            context.sendBroadcast(targeted(IndexLocalCaptureContract.RECEIVER_CLASS))
            logger.i { "Delivered $recordingId via broadcast" }
            ok("BROADCAST")
        } catch (broadcastError: Exception) {
            logger.e(broadcastError) { "Local capture broadcast also failed" }
            IndexLocalCaptureRunResult(
                ok = false,
                status = "FAILED",
                detail = broadcastError.message ?: "unknown error",
                byteSize = byteSize,
                durationMs = started.elapsedNow().inWholeMilliseconds,
            )
        }
    }
}

private fun contentsLabel(hasAudio: Boolean, hasTranscription: Boolean): String = when {
    hasAudio && hasTranscription -> "recording + transcription"
    hasAudio -> "recording"
    hasTranscription -> "transcription"
    else -> "metadata only"
}
