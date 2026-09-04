package coredevices.ring.external.indexwebhook

import co.touchlab.kermit.Logger
import coredevices.api.ApiClient
import coredevices.ring.api.ApiConfig
import coredevices.ring.service.button.RingGesture
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.content.ByteArrayContent
import io.ktor.http.fromHttpToGmtDate
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.io.IOException
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.time.TimeSource
import kotlin.uuid.Uuid

interface IndexWebhookApi {
    /** POST a synthetic payload so a user can verify their endpoint before saving. */
    suspend fun sendTestEvent(
        gesture: RingGesture,
        url: String,
        headers: Map<String, String>,
    ): IndexWebhookRunResult
}

data class IndexWebhookRunResult(
    val ok: Boolean,
    val status: String,
    val detail: String,
    val byteSize: Long,
    val durationMs: Long,
    val retryable: Boolean = false,
    val retryAfter: Duration? = null,
    val transportFailure: Boolean = false,
)

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
internal const val WEBHOOK_DELIVERY_HEADER = "X-Index-Delivery"

/**
 * Generic webhook API client for uploading Index recording data.
 * Sends audio (M4A) and/or transcription text to a user-configured endpoint.
 */
class IndexWebhookApiImpl(
    config: ApiConfig,
    private val runRepository: IndexWebhookRunRepository,
) : IndexWebhookApi, ApiClient(config.version, timeout = 2.minutes, followAllRedirects = true) {

    companion object {
        private val logger = Logger.withTag("IndexWebhookApi")
    }

    suspend fun send(delivery: IndexWebhookDelivery): IndexWebhookRunResult {
        val result = post(
            url = delivery.url,
            headers = delivery.headers,
            triggerValue = delivery.gesture.webhookTriggerValue,
            audioData = delivery.audioData,
            filename = delivery.filename,
            transcription = delivery.transcription,
            recordedAt = delivery.recordedAt,
            isTest = false,
            deliveryId = delivery.deliveryId,
        )
        runRepository.record(
            gesture = delivery.gesture,
            ok = result.ok,
            status = result.status,
            detail = result.detail,
            byteSize = result.byteSize,
            durationMs = result.durationMs,
            deliveryId = delivery.deliveryId,
            canRetry = !result.ok && !result.retryable,
        )
        return result
    }

    override suspend fun sendTestEvent(
        gesture: RingGesture,
        url: String,
        headers: Map<String, String>,
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
            deliveryId = null,
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

    private suspend fun post(
        url: String,
        headers: Map<String, String>,
        triggerValue: String,
        audioData: ByteArray?,
        filename: String?,
        transcription: String?,
        recordedAt: Instant,
        isTest: Boolean,
        deliveryId: String?,
    ): IndexWebhookRunResult {
        val boundary = Uuid.random().toString()
        val bodyBytes = buildWebhookMultipartBody(
            boundary = boundary,
            audioData = audioData,
            filename = filename ?: "recording.m4a",
            recordedAt = recordedAt.toEpochMilliseconds(),
            transcription = transcription,
            isTest = isTest,
        )
        val started = TimeSource.Monotonic.markNow()
        return try {
            val response = client.post(url) {
                headers
                    .filterKeys {
                        !it.equals(WEBHOOK_TRIGGER_HEADER, ignoreCase = true) &&
                            !it.equals(WEBHOOK_TEST_HEADER, ignoreCase = true) &&
                            !it.equals(WEBHOOK_DELIVERY_HEADER, ignoreCase = true) &&
                            !it.equals(WEBHOOK_AUDIO_SIZE_HEADER, ignoreCase = true)
                    }
                    .forEach { (name, value) -> header(name, value) }
                header(WEBHOOK_TRIGGER_HEADER, triggerValue)
                if (isTest) header(WEBHOOK_TEST_HEADER, "true")
                if (deliveryId != null) header(WEBHOOK_DELIVERY_HEADER, deliveryId)
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
                    retryable = response.status.value.isRetryableWebhookStatus(),
                    retryAfter = response.headers[HttpHeaders.RetryAfter].toRetryAfterDuration(),
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.e(e) { "Failed to post to webhook" }
            IndexWebhookRunResult(
                ok = false,
                status = "FAILED",
                detail = e.message ?: "unknown error",
                byteSize = bodyBytes.size.toLong(),
                durationMs = started.elapsedNow().inWholeMilliseconds,
                retryable = true,
                transportFailure = e is IOException,
            )
        }
    }
}

internal fun Int.isRetryableWebhookStatus(): Boolean =
    this == 408 || this == 425 || this == 429 || this in 500..599

internal fun String?.toRetryAfterDuration(now: Instant = Clock.System.now()): Duration? {
    val value = this?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    value.toLongOrNull()?.takeIf { it >= 0 }?.let { return it.seconds }
    return runCatching {
        (Instant.fromEpochMilliseconds(value.fromHttpToGmtDate().timestamp) - now)
            .coerceAtLeast(Duration.ZERO)
    }.getOrNull()
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
