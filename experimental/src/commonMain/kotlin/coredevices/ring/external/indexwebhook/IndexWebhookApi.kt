package coredevices.ring.external.indexwebhook

import co.touchlab.kermit.Logger
import coredevices.api.ApiClient
import coredevices.ring.api.ApiConfig
import coredevices.ring.audio.M4aEncoder
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.content.ByteArrayContent
import io.ktor.http.isSuccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlin.uuid.Uuid

interface IndexWebhookApi {
    /**
     * Upload recording data to the given webhook config's endpoint.
     * Runs asynchronously and does not block the caller.
     *
     * @param config The webhook config resolved for the triggering gesture
     * @param samples PCM audio samples (16-bit signed, mono). Null when TranscriptionOnly mode.
     * @param sampleRate Sample rate of the audio in Hz
     * @param recordingId Unique identifier for the recording (used in filename)
     * @param transcription Transcription text. Null when RecordingOnly mode.
     * @param recordedAt When the recording was actually made
     * @param gesture Button gesture that started the recording, or null when unknown (header omitted)
     */
    fun upload(
        config: IndexWebhookConfig,
        samples: ShortArray?,
        sampleRate: Int,
        recordingId: String,
        transcription: String?,
        recordedAt: Instant,
        gesture: IndexWebhookGesture?,
    )
}

/**
 * Generic webhook API client for uploading Index recording data.
 * Sends audio (M4A) and/or transcription text to a user-configured endpoint.
 * Reuses the same M4aEncoder and ApiClient infrastructure as the original Vermillion integration.
 */
class IndexWebhookApiImpl(
    config: ApiConfig,
    private val m4aEncoder: M4aEncoder,
    private val scope: CoroutineScope,
) : IndexWebhookApi, ApiClient(config.version, timeout = 2.minutes) {

    companion object {
        private val logger = Logger.withTag("IndexWebhookApi")
        private const val AUDIO_SIZE_HEADER = "X-Audio-Size"
    }

    override fun upload(
        config: IndexWebhookConfig,
        samples: ShortArray?,
        sampleRate: Int,
        recordingId: String,
        transcription: String?,
        recordedAt: Instant,
        gesture: IndexWebhookGesture?,
    ) {
        val url = config.url
        if (url.isNullOrBlank()) return

        scope.launch {
            try {
                logger.d { "Starting webhook upload for recording $recordingId (mode=${config.payloadMode})" }

                // Encode audio to M4A if needed
                val m4aData: ByteArray? = if (
                    samples != null &&
                    config.payloadMode != IndexWebhookPayloadMode.TranscriptionOnly
                ) {
                    m4aEncoder.encode(samples, sampleRate)
                } else null

                // Determine transcription to send
                val transcriptionToSend: String? = if (
                    config.payloadMode != IndexWebhookPayloadMode.RecordingOnly
                ) transcription else null

                val result = upload(
                    url = url,
                    headers = config.headers,
                    audioData = m4aData,
                    filename = "$recordingId.m4a",
                    transcription = transcriptionToSend,
                    recordedAt = recordedAt,
                    gesture = gesture,
                )

                result.fold(
                    onSuccess = { logger.i { "Webhook upload succeeded for $recordingId" } },
                    onFailure = { e -> logger.e(e) { "Webhook upload failed for $recordingId" } }
                )
            } catch (e: Exception) {
                logger.e(e) { "Error during webhook upload for $recordingId" }
            }
        }
    }

    private suspend fun upload(
        url: String,
        headers: Map<String, String>,
        audioData: ByteArray?,
        filename: String,
        transcription: String?,
        recordedAt: Instant,
        gesture: IndexWebhookGesture?,
    ): Result<Unit> {
        return try {
            val boundary = Uuid.random().toString()

            val bodyBytes = buildMultipartBody(
                boundary = boundary,
                audioData = audioData,
                filename = filename,
                mimeType = "audio/mp4",
                recordedAt = recordedAt.toEpochMilliseconds(),
                client = "ring",
                transcription = transcription
            )

            val response = client.post(url) {
                headers
                    .filterKeys { !it.equals(IndexWebhookGesture.HEADER_NAME, ignoreCase = true) }
                    .forEach { (name, value) -> header(name, value) }
                gesture?.let { header(IndexWebhookGesture.HEADER_NAME, it.headerValue) }
                if (audioData != null) {
                    header(AUDIO_SIZE_HEADER, audioData.size.toString())
                }
                setBody(ByteArrayContent(
                    bytes = bodyBytes,
                    contentType = ContentType.parse("multipart/form-data; boundary=$boundary"),
                ))
            }

            if (response.status.isSuccess()) {
                Result.success(Unit)
            } else {
                val body = response.bodyAsText()
                logger.e { "Webhook upload failed: ${response.status} - $body" }
                Result.failure(Exception("Upload failed: ${response.status}"))
            }
        } catch (e: Exception) {
            logger.e(e) { "Failed to upload to webhook" }
            Result.failure(e)
        }
    }

    /**
     * Build a multipart/form-data body with conditional audio and transcription parts.
     * Format is compatible with the original Vermillion API when using RecordingOnly mode.
     */
    private fun buildMultipartBody(
        boundary: String,
        audioData: ByteArray?,
        filename: String,
        mimeType: String,
        recordedAt: Long,
        client: String,
        transcription: String?
    ): ByteArray {
        val crlf = "\r\n"
        val parts = mutableListOf<ByteArray>()

        // Audio part (conditional)
        if (audioData != null) {
            val header = StringBuilder()
            header.append("--$boundary$crlf")
            header.append("Content-Disposition: form-data; name=\"audio\"; filename=\"$filename\"$crlf")
            header.append("Content-Type: $mimeType$crlf$crlf")
            parts.add(header.toString().encodeToByteArray())
            parts.add(audioData)
            parts.add(crlf.encodeToByteArray())
        }

        // Transcription part (conditional)
        if (transcription != null) {
            val text = StringBuilder()
            text.append("--$boundary$crlf")
            text.append("Content-Disposition: form-data; name=\"transcription\"$crlf$crlf")
            text.append("$transcription$crlf")
            parts.add(text.toString().encodeToByteArray())
        }

        // Metadata parts (always included)
        val metadata = StringBuilder()
        metadata.append("--$boundary$crlf")
        metadata.append("Content-Disposition: form-data; name=\"recordedAt\"$crlf$crlf")
        metadata.append("$recordedAt$crlf")

        metadata.append("--$boundary$crlf")
        metadata.append("Content-Disposition: form-data; name=\"client\"$crlf$crlf")
        metadata.append("$client$crlf")

        metadata.append("--$boundary--$crlf")
        parts.add(metadata.toString().encodeToByteArray())

        // Combine all parts
        val totalSize = parts.sumOf { it.size }
        val result = ByteArray(totalSize)
        var offset = 0
        for (part in parts) {
            part.copyInto(result, offset)
            offset += part.size
        }
        return result
    }
}
