package coredevices.ring.external.indexwebhook

import co.touchlab.kermit.Logger
import coredevices.api.ApiClient
import coredevices.ring.api.ApiConfig
import coredevices.ring.audio.M4aEncoder
import coredevices.ring.service.button.RingGesture
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.content.ByteArrayContent
import io.ktor.http.isSuccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
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

    /** POST a click gesture's button event to its webhook. Runs asynchronously. */
    fun sendButtonEventIfEnabled(gesture: RingGesture, pressedAt: Instant)

    /** POST a synthetic payload so a user can verify their endpoint before saving. */
    suspend fun sendTestEvent(
        gesture: RingGesture,
        url: String,
        headers: Map<String, String>,
        signRequests: Boolean,
        signingSecret: String?,
        clickPayload: String = "",
    ): IndexWebhookRunResult
}

data class IndexWebhookRunResult(
    val ok: Boolean,
    val status: String,
    val detail: String,
    val byteSize: Long,
    val durationMs: Long,
)

/** Value of the `X-Index-Trigger` header. Endpoints key off these, do not rename them. */
val RingGesture.webhookTriggerValue: String
    get() = when (this) {
        RingGesture.Click -> "click"
        RingGesture.DoubleClick -> "double-click"
        RingGesture.TripleClick -> "triple-click"
        RingGesture.Hold -> "single-click-hold"
        RingGesture.ClickHold -> "double-click-hold"
    }

internal const val WEBHOOK_AUDIO_SIZE_HEADER = "X-Audio-Size"
internal const val WEBHOOK_VERSION_HEADER = "X-Index-Webhook-Version"
internal const val WEBHOOK_VERSION = "1"
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
    private val signingSecretStorage: IndexWebhookSigningSecretStorage,
    private val runRepository: IndexWebhookRunRepository,
    private val scope: CoroutineScope,
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

                val boundary = Uuid.random().toString()
                val result = post(
                    url = url,
                    headers = config.headers,
                    signRequests = config.signRequests,
                    signingSecret = signingSecretFor(gesture, config),
                    deliveryId = recordingId,
                    triggerValue = gesture.webhookTriggerValue,
                    bodyBytes = buildWebhookMultipartBody(
                        boundary = boundary,
                        audioData = m4aData,
                        filename = "$recordingId.m4a",
                        recordedAt = recordedAt.toEpochMilliseconds(),
                        transcription = transcription,
                        isTest = false,
                    ),
                    contentType = multipartType(boundary),
                    audioSize = m4aData?.size,
                    isTest = false,
                    okDetail = contentsLabel(m4aData != null, transcription != null),
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

    override fun sendButtonEventIfEnabled(gesture: RingGesture, pressedAt: Instant) {
        val config = webhookPreferences.configFor(gesture)
        val url = config.url
        if (!config.isActive || url == null) return

        scope.launch {
            try {
                val result = post(
                    url = url,
                    headers = config.headers,
                    signRequests = config.signRequests,
                    signingSecret = signingSecretFor(gesture, config),
                    deliveryId = Uuid.random().toString(),
                    triggerValue = gesture.webhookTriggerValue,
                    bodyBytes = buildButtonEventBody(gesture, config.clickPayload, pressedAt, isTest = false),
                    contentType = ContentType.Application.Json,
                    audioSize = null,
                    isTest = false,
                    okDetail = buttonEventLabel(gesture),
                )
                runRepository.record(
                    gesture = gesture,
                    ok = result.ok,
                    status = result.status,
                    detail = result.detail,
                    byteSize = result.byteSize,
                    durationMs = result.durationMs,
                )
                if (!result.ok) {
                    logger.e { "Button webhook failed for ${gesture.name}: ${result.status} ${result.detail}" }
                }
            } catch (e: Exception) {
                logger.e(e) { "Error sending button webhook for ${gesture.name}" }
            }
        }
    }

    override suspend fun sendTestEvent(
        gesture: RingGesture,
        url: String,
        headers: Map<String, String>,
        signRequests: Boolean,
        signingSecret: String?,
        clickPayload: String,
    ): IndexWebhookRunResult {
        val boundary = Uuid.random().toString()
        val isClick = gesture.isClick
        val result = post(
            url = url,
            headers = headers,
            signRequests = signRequests,
            signingSecret = signingSecret,
            deliveryId = Uuid.random().toString(),
            triggerValue = WEBHOOK_TEST_TRIGGER,
            bodyBytes = if (isClick) {
                buildButtonEventBody(gesture, clickPayload, Clock.System.now(), isTest = true)
            } else {
                buildWebhookMultipartBody(
                    boundary = boundary,
                    audioData = null,
                    filename = "recording.m4a",
                    recordedAt = Clock.System.now().toEpochMilliseconds(),
                    transcription = WEBHOOK_TEST_TRANSCRIPTION,
                    isTest = true,
                )
            },
            contentType = if (isClick) ContentType.Application.Json else multipartType(boundary),
            audioSize = null,
            isTest = true,
            okDetail = if (isClick) buttonEventLabel(gesture) else contentsLabel(false, true),
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

    private suspend fun signingSecretFor(gesture: RingGesture, config: IndexWebhookConfig): String? =
        if (config.signRequests) {
            try {
                signingSecretStorage.get(gesture)
            } catch (e: Exception) {
                logger.e(e) { "Could not read webhook signing secret" }
                null
            }
        } else null

    private suspend fun post(
        url: String,
        headers: Map<String, String>,
        signRequests: Boolean,
        signingSecret: String?,
        deliveryId: String,
        triggerValue: String,
        bodyBytes: ByteArray,
        contentType: ContentType,
        audioSize: Int?,
        isTest: Boolean,
        okDetail: String,
    ): IndexWebhookRunResult {
        val started = TimeSource.Monotonic.markNow()
        return try {
            val timestamp = Clock.System.now().toEpochMilliseconds() / 1000
            val signature = if (signRequests) {
                if (signingSecret.isNullOrBlank()) {
                    return IndexWebhookRunResult(
                        ok = false,
                        status = "SIGNING ERROR",
                        detail = "Signing secret is missing",
                        byteSize = bodyBytes.size.toLong(),
                        durationMs = started.elapsedNow().inWholeMilliseconds,
                    )
                }
                createWebhookSignature(
                    secret = signingSecret,
                    timestamp = timestamp,
                    deliveryId = deliveryId,
                    triggerValue = triggerValue,
                    isTest = isTest,
                    body = bodyBytes,
                )
            } else null
            val response = client.post(url) {
                headers.withoutReservedWebhookHeaders()
                    .forEach { (name, value) -> header(name, value) }
                header(WEBHOOK_VERSION_HEADER, WEBHOOK_VERSION)
                header(WEBHOOK_TRIGGER_HEADER, triggerValue)
                if (isTest) header(WEBHOOK_TEST_HEADER, "true")
                if (audioSize != null) header(WEBHOOK_AUDIO_SIZE_HEADER, audioSize.toString())
                if (signature != null) {
                    header(WEBHOOK_SIGNATURE_HEADER, signature)
                    header(WEBHOOK_TIMESTAMP_HEADER, timestamp.toString())
                    header(WEBHOOK_DELIVERY_HEADER, deliveryId)
                }
                setBody(
                    ByteArrayContent(bytes = bodyBytes, contentType = contentType)
                )
            }
            val elapsed = started.elapsedNow().inWholeMilliseconds
            if (response.status.isSuccess()) {
                IndexWebhookRunResult(
                    ok = true,
                    status = "${response.status.value} OK",
                    detail = okDetail,
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

private fun multipartType(boundary: String) =
    ContentType.parse("multipart/form-data; boundary=$boundary")

private fun buttonEventLabel(gesture: RingGesture): String {
    val clicks = gesture.sequence.size
    return if (clicks == 1) "1 click" else "$clicks clicks"
}

/**
 * JSON body for a click gesture. [clickPayload] is embedded as JSON when it parses as JSON,
 * otherwise as a string; blank payloads are omitted.
 */
internal fun buildButtonEventBody(
    gesture: RingGesture,
    clickPayload: String,
    pressedAt: Instant,
    isTest: Boolean,
): ByteArray = buildJsonObject {
    put("event", "button")
    put("gesture", gesture.webhookTriggerValue)
    put("clicks", gesture.sequence.size)
    put("pressedAt", pressedAt.toEpochMilliseconds())
    put("client", "ring")
    if (isTest) put("test", true)
    clickPayload.trim().takeIf { it.isNotEmpty() }?.let { raw ->
        put("payload", try {
            Json.parseToJsonElement(raw)
        } catch (_: SerializationException) {
            JsonPrimitive(raw)
        })
    }
}.toString().encodeToByteArray()

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
