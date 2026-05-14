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
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.Uuid

interface IndexWebhookApi {
    /**
     * Upload recording data to the configured webhook endpoint.
     * Runs asynchronously and does not block the caller.
     *
     * @param samples PCM audio samples (16-bit signed, mono). Null when TranscriptionOnly mode.
     * @param sampleRate Sample rate of the audio in Hz
     * @param recordingId Unique identifier for the recording (used in filename)
     * @param transcription Transcription text. Null when RecordingOnly mode.
     */
    fun uploadIfEnabled(samples: ShortArray?, sampleRate: Int, recordingId: String, transcription: String?)
    val isEnabled: StateFlow<Boolean>
}

/**
 * Generic webhook API client for uploading Index recording data.
 * Sends audio (M4A) and/or transcription text to a user-configured endpoint.
 * Reuses the same M4aEncoder and ApiClient infrastructure as the original Vermillion integration.
 */
class IndexWebhookApiImpl(
    config: ApiConfig,
    private val m4aEncoder: M4aEncoder,
    private val webhookPreferences: IndexWebhookPreferences,
    private val scope: CoroutineScope,
) : IndexWebhookApi, ApiClient(config.version, timeout = 2.minutes) {

    companion object {
        private val logger = Logger.withTag("IndexWebhookApi")
        private const val WIDGET_TOKEN_HEADER = "X-Widget-Token"
        private const val AUDIO_SIZE_HEADER = "X-Audio-Size"
    }

    private val _isEnabled = MutableStateFlow(false)
    override val isEnabled = _isEnabled.asStateFlow()

    init {
        scope.launch {
            combine(webhookPreferences.webhookUrl, webhookPreferences.authToken) { url, token ->
                !url.isNullOrBlank() && !token.isNullOrBlank()
            }.collect { enabled ->
                _isEnabled.value = enabled
                logger.d { "Index webhook enabled: $enabled" }
            }
        }
    }

    override fun uploadIfEnabled(samples: ShortArray?, sampleRate: Int, recordingId: String, transcription: String?) {
        val url = webhookPreferences.webhookUrl.value
        val token = webhookPreferences.authToken.value
        if (url.isNullOrBlank() || token.isNullOrBlank()) return

        val payloadMode = webhookPreferences.payloadMode.value

        scope.launch {
            try {
                logger.d { "Starting webhook upload for recording $recordingId (mode=$payloadMode)" }

                // Encode audio to M4A if needed
                val m4aData: ByteArray? = if (
                    samples != null &&
                    payloadMode != IndexWebhookPayloadMode.TranscriptionOnly
                ) {
                    m4aEncoder.encode(samples, sampleRate)
                } else null

                // Determine transcription to send
                val transcriptionToSend: String? = if (
                    payloadMode != IndexWebhookPayloadMode.RecordingOnly
                ) transcription else null

                val result = upload(
                    url = url,
                    authToken = token,
                    audioData = m4aData,
                    filename = "$recordingId.m4a",
                    transcription = transcriptionToSend
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
        authToken: String,
        audioData: ByteArray?,
        filename: String,
        transcription: String?
    ): Result<Unit> {
        return try {
            val recordedAt = Clock.System.now().toEpochMilliseconds()
            val boundary = Uuid.random().toString()

            val bodyBytes = buildMultipartBody(
                boundary = boundary,
                audioData = audioData,
                filename = filename,
                mimeType = "audio/mp4",
                recordedAt = recordedAt,
                client = "ring",
                transcription = transcription
            )

            val response = client.post(url) {
                contentType(ContentType.parse("multipart/form-data; boundary=$boundary"))
                header(WIDGET_TOKEN_HEADER, authToken)
                if (audioData != null) {
                    header(AUDIO_SIZE_HEADER, audioData.size.toString())
                }
                setBody(bodyBytes)
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
