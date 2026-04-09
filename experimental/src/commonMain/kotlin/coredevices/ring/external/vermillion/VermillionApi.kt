package coredevices.ring.external.vermillion

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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.Uuid

interface VermillionApi {
    fun uploadIfEnabled(samples: ShortArray, sampleRate: Int, recordingId: String)
    val isEnabled: StateFlow<Boolean>
}

/**
 * API client for uploading audio recordings to Vermillion.
 * Handles M4A encoding and async uploads.
 */
class VermillionApiImpl(
    config: ApiConfig,
    private val m4aEncoder: M4aEncoder,
    private val vermillionPreferences: VermillionPreferences,
    private val scope: CoroutineScope,
) : VermillionApi, ApiClient(config.version, timeout = 2.minutes) {

    companion object {
        private val logger = Logger.withTag("VermillionApi")
        private const val UPLOAD_URL = "https://vermillion.ai/v1/external/spooled-audio"
        private const val WIDGET_TOKEN_HEADER = "X-Widget-Token"
        private const val AUDIO_SIZE_HEADER = "X-Audio-Size"
    }

    private val _isEnabled = MutableStateFlow(false)
    override val isEnabled = _isEnabled.asStateFlow()

    init {
        scope.launch {
            vermillionPreferences.widgetToken.collect { token ->
                _isEnabled.value = !token.isNullOrBlank()
                logger.d { "Vermillion upload enabled: ${_isEnabled.value}" }
            }
        }
    }

    /**
     * Upload audio to Vermillion if configured.
     * This runs asynchronously and does not block the caller.
     * @param samples PCM audio samples (16-bit signed, mono)
     * @param sampleRate Sample rate of the audio in Hz
     * @param recordingId Unique identifier for the recording (used in filename)
     */
    override fun uploadIfEnabled(samples: ShortArray, sampleRate: Int, recordingId: String) {
        val token = vermillionPreferences.widgetToken.value
        if (token.isNullOrBlank()) {
            return
        }

        scope.launch {
            try {
                logger.d { "Starting Vermillion upload for recording $recordingId" }

                val m4aData = m4aEncoder.encode(samples, sampleRate)
                logger.d { "Encoded ${samples.size} samples to ${m4aData.size} bytes M4A" }

                val result = uploadAudio(
                    widgetToken = token,
                    audioData = m4aData,
                    filename = "$recordingId.m4a"
                )

                result.fold(
                    onSuccess = { logger.i { "Successfully uploaded recording $recordingId to Vermillion" } },
                    onFailure = { e -> logger.e(e) { "Failed to upload recording $recordingId to Vermillion" } }
                )
            } catch (e: Exception) {
                logger.e(e) { "Error during Vermillion upload for recording $recordingId" }
            }
        }
    }

    private suspend fun uploadAudio(
        widgetToken: String,
        audioData: ByteArray,
        filename: String,
        userTimeZone: String? = null
    ): Result<Unit> {
        return try {
            logger.d { "Uploading ${audioData.size} bytes to Vermillion" }

            val recordedAt = Clock.System.now().toEpochMilliseconds()
            val boundary = Uuid.random().toString()
            val mimeType = "audio/mp4"

            val bodyBytes = buildMultipartBody(
                boundary = boundary,
                audioData = audioData,
                filename = filename,
                mimeType = mimeType,
                recordedAt = recordedAt,
                client = "ring",
                userTimeZone = userTimeZone
            )

            val response = client.post(UPLOAD_URL) {
                contentType(ContentType.parse("multipart/form-data; boundary=$boundary"))
                header(WIDGET_TOKEN_HEADER, widgetToken)
                header(AUDIO_SIZE_HEADER, audioData.size.toString())
                setBody(bodyBytes)
            }

            if (response.status.isSuccess()) {
                logger.i { "Successfully uploaded audio to Vermillion" }
                Result.success(Unit)
            } else {
                val body = response.bodyAsText()
                logger.e { "Vermillion upload failed: ${response.status} - $body" }
                Result.failure(Exception("Upload failed: ${response.status}"))
            }
        } catch (e: Exception) {
            logger.e(e) { "Failed to upload audio to Vermillion" }
            Result.failure(e)
        }
    }

    private fun buildMultipartBody(
        boundary: String,
        audioData: ByteArray,
        filename: String,
        mimeType: String,
        recordedAt: Long,
        client: String,
        userTimeZone: String?
    ): ByteArray {
        val builder = StringBuilder()
        val crlf = "\r\n"

        builder.append("--$boundary$crlf")
        builder.append("Content-Disposition: form-data; name=\"audio\"; filename=\"$filename\"$crlf")
        builder.append("Content-Type: $mimeType$crlf$crlf")

        val headerBytes = builder.toString().encodeToByteArray()

        val textFields = StringBuilder()
        textFields.append(crlf)

        textFields.append("--$boundary$crlf")
        textFields.append("Content-Disposition: form-data; name=\"recordedAt\"$crlf$crlf")
        textFields.append("$recordedAt$crlf")

        textFields.append("--$boundary$crlf")
        textFields.append("Content-Disposition: form-data; name=\"client\"$crlf$crlf")
        textFields.append("$client$crlf")

        if (userTimeZone != null) {
            textFields.append("--$boundary$crlf")
            textFields.append("Content-Disposition: form-data; name=\"userTimeZone\"$crlf$crlf")
            textFields.append("$userTimeZone$crlf")
        }

        textFields.append("--$boundary--$crlf")

        val textBytes = textFields.toString().encodeToByteArray()

        return headerBytes + audioData + textBytes
    }
}
