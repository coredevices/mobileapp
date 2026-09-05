package coredevices.ring.external.indexwebhook

import co.touchlab.kermit.Logger
import coredevices.HackyPermissionRequesterProvider
import coredevices.api.ApiClient
import coredevices.ring.api.ApiConfig
import coredevices.ring.audio.M4aEncoder
import coredevices.ring.service.button.RingGesture
import coredevices.util.Permission
import io.rebble.libpebblecommon.util.GeolocationPositionResult
import io.rebble.libpebblecommon.util.SystemGeolocation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.content.ByteArrayContent
import io.ktor.http.isSuccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.round
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.time.TimeSource
import kotlin.uuid.Uuid

interface IndexWebhookApi {
    /**
     * Upload recording data to the webhook configured for [gesture].
     * Runs asynchronously and does not block the caller.
     *
     * @param samples PCM audio samples (16-bit signed, mono). Null when TranscriptionOnly mode.
     * @param sampleRate Sample rate of the audio in Hz
     * @param recordingId Unique identifier for the recording (used in filename)
     * @param transcription Transcription text. Null when RecordingOnly mode.
     * @param recordedAt When the recording was actually made
     * @param gesture Button gesture that started the recording
     */
    fun uploadIfEnabled(
        samples: ShortArray?,
        sampleRate: Int,
        recordingId: String,
        transcription: String?,
        recordedAt: Instant,
        gesture: RingGesture,
    )

    /** POST a synthetic payload so a user can verify their endpoint before saving. */
    suspend fun sendTestEvent(
        gesture: RingGesture,
        url: String,
        headers: Map<String, String>,
        includeLocation: Boolean,
    ): IndexWebhookRunResult
}

data class IndexWebhookRunResult(
    val ok: Boolean,
    val status: String,
    val detail: String,
    val byteSize: Long,
    val durationMs: Long,
)

internal data class IndexWebhookLocation(
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long,
)

internal fun indexWebhookLocation(
    result: GeolocationPositionResult.Success,
    now: Instant,
): IndexWebhookLocation? {
    if (!result.latitude.isFinite() || result.latitude !in -90.0..90.0) return null
    if (!result.longitude.isFinite() || result.longitude !in -180.0..180.0) return null
    if (result.timestamp > now || result.timestamp < now - 1.hours) return null
    return IndexWebhookLocation(
        latitude = round(result.latitude * 1000) / 1000,
        longitude = round(result.longitude * 1000) / 1000,
        timestamp = result.timestamp.toEpochMilliseconds(),
    )
}

internal suspend fun resolveIndexWebhookLocation(
    includeLocation: Boolean,
    hasPermission: suspend () -> Boolean,
    getCurrentPosition: suspend () -> GeolocationPositionResult,
    now: () -> Instant,
): IndexWebhookLocation? {
    if (!includeLocation) return null
    return try {
        if (!hasPermission()) return null
        when (val result = getCurrentPosition()) {
            is GeolocationPositionResult.Success -> indexWebhookLocation(result, now())
            is GeolocationPositionResult.Error -> null
        }
    } catch (_: Exception) {
        null
    }
}

/** Value of the `X-Index-Trigger` header. Endpoints key off these, do not rename them. */
val RingGesture.webhookTriggerValue: String
    get() = when (this) {
        RingGesture.ClickHold -> "double-click-hold"
        else -> "single-click-hold"
    }

internal const val WEBHOOK_AUDIO_SIZE_HEADER = "X-Audio-Size"
internal const val WEBHOOK_TRIGGER_HEADER = "X-Index-Trigger"
internal const val WEBHOOK_TEST_HEADER = "X-Index-Test"
internal const val WEBHOOK_TEST_TRIGGER = "test-event"
internal const val WEBHOOK_TEST_TRANSCRIPTION = "Index webhook test event"

/**
 * Generic webhook API client for uploading Index recording data.
 * Sends audio (M4A) and/or transcription text to a user-configured endpoint.
 */
class IndexWebhookApiImpl(
    config: ApiConfig,
    private val m4aEncoder: M4aEncoder,
    private val webhookPreferences: IndexWebhookPreferences,
    private val runRepository: IndexWebhookRunRepository,
    private val scope: CoroutineScope,
    private val permissionProvider: HackyPermissionRequesterProvider,
    private val geolocation: SystemGeolocation,
) : IndexWebhookApi, ApiClient(config.version, timeout = 2.minutes, followAllRedirects = true) {

    companion object {
        private val logger = Logger.withTag("IndexWebhookApi")
    }

    override fun uploadIfEnabled(
        samples: ShortArray?,
        sampleRate: Int,
        recordingId: String,
        transcription: String?,
        recordedAt: Instant,
        gesture: RingGesture,
    ) {
        val config = webhookPreferences.configFor(gesture)
        val url = config.url
        if (!config.isActive || url == null) return

        scope.launch {
            try {
                logger.d { "Webhook upload for $recordingId (${gesture.name})" }

                // The caller already selected the payload for the delivery's mode.
                val m4aData: ByteArray? = samples?.let { m4aEncoder.encode(it, sampleRate) }
                val location = currentLocation(config.includeLocation)

                val result = post(
                    url = url,
                    headers = config.headers,
                    triggerValue = gesture.webhookTriggerValue,
                    audioData = m4aData,
                    filename = "$recordingId.m4a",
                    transcription = transcription,
                    recordedAt = recordedAt,
                    isTest = false,
                    location = location,
                )
                runRepository.record(
                    gesture = gesture,
                    ok = result.ok,
                    status = result.status,
                    detail = result.detail,
                    byteSize = result.byteSize,
                    durationMs = result.durationMs,
                )
                if (result.ok) {
                    logger.i { "Webhook upload succeeded for $recordingId" }
                } else {
                    logger.e { "Webhook upload failed for $recordingId: ${result.status} ${result.detail}" }
                }
            } catch (e: Exception) {
                logger.e(e) { "Error during webhook upload for $recordingId" }
            }
        }
    }

    override suspend fun sendTestEvent(
        gesture: RingGesture,
        url: String,
        headers: Map<String, String>,
        includeLocation: Boolean,
    ): IndexWebhookRunResult {
        val result = post(
            url = url,
            headers = headers,
            triggerValue = WEBHOOK_TEST_TRIGGER,
            audioData = null,
            filename = null,
            transcription = WEBHOOK_TEST_TRANSCRIPTION,
            recordedAt = Clock.System.now(),
            isTest = true,
            location = currentLocation(includeLocation),
        )
        runRepository.record(
            gesture = gesture,
            ok = result.ok,
            status = result.status,
            detail = "test event",
            byteSize = result.byteSize,
            durationMs = result.durationMs,
        )
        return result
    }

    private suspend fun currentLocation(includeLocation: Boolean): IndexWebhookLocation? {
        return resolveIndexWebhookLocation(
            includeLocation = includeLocation,
            hasPermission = { permissionProvider.get().hasPermission(Permission.Location) },
            getCurrentPosition = {
                geolocation.getCurrentPosition(
                    maximumAge = 1.hours,
                    timeout = 1.seconds,
                    highAccuracy = false,
                )
            },
            now = Clock.System::now,
        )
    }

    private suspend fun post(
        url: String,
        headers: Map<String, String>,
        triggerValue: String,
        audioData: ByteArray?,
        filename: String?,
        transcription: String?,
        recordedAt: Instant,
        isTest: Boolean,
        location: IndexWebhookLocation?,
    ): IndexWebhookRunResult {
        val boundary = Uuid.random().toString()
        val bodyBytes = buildWebhookMultipartBody(
            boundary = boundary,
            audioData = audioData,
            filename = filename ?: "recording.m4a",
            recordedAt = recordedAt.toEpochMilliseconds(),
            transcription = transcription,
            isTest = isTest,
            location = location,
        )
        val started = TimeSource.Monotonic.markNow()
        return try {
            val response = client.post(url) {
                headers
                    .filterKeys {
                        !it.equals(WEBHOOK_TRIGGER_HEADER, ignoreCase = true) &&
                            !it.equals(WEBHOOK_TEST_HEADER, ignoreCase = true)
                    }
                    .forEach { (name, value) -> header(name, value) }
                header(WEBHOOK_TRIGGER_HEADER, triggerValue)
                if (isTest) header(WEBHOOK_TEST_HEADER, "true")
                if (audioData != null) header(WEBHOOK_AUDIO_SIZE_HEADER, audioData.size.toString())
                setBody(
                    ByteArrayContent(
                        bytes = bodyBytes,
                        contentType = ContentType.parse("multipart/form-data; boundary=$boundary"),
                    )
                )
            }
            val elapsed = started.elapsedNow().inWholeMilliseconds
            if (response.status.isSuccess()) {
                IndexWebhookRunResult(
                    ok = true,
                    status = "${response.status.value} OK",
                    detail = contentsLabel(audioData != null, transcription != null),
                    byteSize = bodyBytes.size.toLong(),
                    durationMs = elapsed,
                )
            } else {
                IndexWebhookRunResult(
                    ok = false,
                    status = "${response.status.value} ERROR",
                    detail = response.bodyAsText().take(200).ifBlank { response.status.description },
                    byteSize = bodyBytes.size.toLong(),
                    durationMs = elapsed,
                )
            }
        } catch (e: Exception) {
            logger.e(e) { "Failed to post to webhook" }
            IndexWebhookRunResult(
                ok = false,
                status = "FAILED",
                detail = e.message ?: "unknown error",
                byteSize = bodyBytes.size.toLong(),
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

/**
 * Build a multipart/form-data body with conditional audio and transcription parts.
 * Format is compatible with the original Vermillion API when using RecordingOnly mode.
 */
internal fun buildWebhookMultipartBody(
    boundary: String,
    audioData: ByteArray?,
    filename: String,
    recordedAt: Long,
    transcription: String?,
    isTest: Boolean,
    location: IndexWebhookLocation? = null,
): ByteArray {
    val crlf = "\r\n"
    val parts = mutableListOf<ByteArray>()

    if (audioData != null) {
        val header = StringBuilder()
        header.append("--$boundary$crlf")
        header.append("Content-Disposition: form-data; name=\"audio\"; filename=\"$filename\"$crlf")
        header.append("Content-Type: audio/mp4$crlf$crlf")
        parts.add(header.toString().encodeToByteArray())
        parts.add(audioData)
        parts.add(crlf.encodeToByteArray())
    }

    if (transcription != null) {
        val text = StringBuilder()
        text.append("--$boundary$crlf")
        text.append("Content-Disposition: form-data; name=\"transcription\"$crlf$crlf")
        text.append("$transcription$crlf")
        parts.add(text.toString().encodeToByteArray())
    }

    val metadata = StringBuilder()
    if (isTest) {
        metadata.append("--$boundary$crlf")
        metadata.append("Content-Disposition: form-data; name=\"test\"$crlf$crlf")
        metadata.append("true$crlf")
    }
    if (location != null) {
        metadata.append("--$boundary$crlf")
        metadata.append("Content-Disposition: form-data; name=\"locationLatitude\"$crlf$crlf")
        metadata.append("${location.latitude}$crlf")

        metadata.append("--$boundary$crlf")
        metadata.append("Content-Disposition: form-data; name=\"locationLongitude\"$crlf$crlf")
        metadata.append("${location.longitude}$crlf")

        metadata.append("--$boundary$crlf")
        metadata.append("Content-Disposition: form-data; name=\"locationTimestamp\"$crlf$crlf")
        metadata.append("${location.timestamp}$crlf")
    }
    metadata.append("--$boundary$crlf")
    metadata.append("Content-Disposition: form-data; name=\"recordedAt\"$crlf$crlf")
    metadata.append("$recordedAt$crlf")

    metadata.append("--$boundary$crlf")
    metadata.append("Content-Disposition: form-data; name=\"client\"$crlf$crlf")
    metadata.append("ring$crlf")

    metadata.append("--$boundary--$crlf")
    parts.add(metadata.toString().encodeToByteArray())

    val totalSize = parts.sumOf { it.size }
    val result = ByteArray(totalSize)
    var offset = 0
    for (part in parts) {
        part.copyInto(result, offset)
        offset += part.size
    }
    return result
}
