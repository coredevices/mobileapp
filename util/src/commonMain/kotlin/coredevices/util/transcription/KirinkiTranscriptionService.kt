package coredevices.util.transcription

import androidx.compose.ui.text.intl.Locale
import co.touchlab.kermit.Logger
import coredevices.api.ApiClient
import coredevices.api.ApiAuthException
import coredevices.util.AudioEncoding
import coredevices.util.CommonBuildKonfig
import io.ktor.client.call.body
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Transcription against the "kirinki" STT backend
 *
 * Unlike the streaming services, kirinki is a batch endpoint: the full clip is
 * buffered, encoded as a 16 kHz / 16-bit mono WAV and POSTed in one request,
 * authorized with the user's Firebase ID token. The model returns the full
 * transcription (and timing metadata we currently ignore) in a single response.
 */
class KirinkiTranscriptionService : ApiClient(CommonBuildKonfig.USER_AGENT_VERSION, timeout = REQUEST_TIMEOUT),
    TranscriptionService {

    companion object {
        private val logger = Logger.withTag("KirinkiTranscriptionService")
        private const val MODEL_USED = "kirinki"

        // Mirrors the server contract in kirinki.py.
        private const val TARGET_SAMPLE_RATE = 16_000
        private const val MAX_DURATION_SECONDS = 150
        private const val MAX_UPLOAD_BYTES = 30 * 1024 * 1024
        private const val WAV_HEADER_BYTES = 44

        private val REQUEST_TIMEOUT = 120.seconds
    }

    @Serializable
    private data class KirinkiResponse(
        val text: String? = null,
        val model: String? = null,
        val error: String? = null,
    )

    override val onInitialized: Channel<Boolean> = Channel()

    override suspend fun isAvailable(): Boolean = CommonBuildKonfig.KIRINKI_URL != null

    override suspend fun transcribe(
        audioStreamFrames: Flow<ByteArray>?,
        sampleRate: Int,
        language: STTLanguage,
        conversationContext: STTConversationContext?,
        dictionaryContext: List<String>?,
        contentContext: String?,
        encoding: AudioEncoding,
        initialTimeout: Duration?,
    ): Flow<TranscriptionSessionStatus> = flow {
        val url = CommonBuildKonfig.KIRINKI_URL
            ?: throw TranscriptionException.TranscriptionServiceUnavailable(MODEL_USED)
        if (audioStreamFrames == null) {
            // kirinki has no mic capture of its own; it only transcribes supplied audio.
            throw TranscriptionException.TranscriptionServiceError(
                "kirinki requires audio stream frames", modelUsed = MODEL_USED,
            )
        }

        emit(TranscriptionSessionStatus.Open)

        // Buffer the whole clip, then convert to the 16 kHz / 16-bit mono PCM the model expects.
        val raw = audioStreamFrames.concatenate()
        val pcm16 = raw.toPcm16(encoding)
        val resampled = if (sampleRate != TARGET_SAMPLE_RATE) {
            resamplePcm16(pcm16, sampleRate, TARGET_SAMPLE_RATE)
        } else {
            pcm16
        }

        val durationSeconds = resampled.size / 2.0 / TARGET_SAMPLE_RATE
        if (durationSeconds > MAX_DURATION_SECONDS) {
            throw TranscriptionException.TranscriptionServiceError(
                "Audio is ${durationSeconds}s; kirinki limit is ${MAX_DURATION_SECONDS}s", modelUsed = MODEL_USED,
            )
        }

        val wav = wrapWav(resampled, TARGET_SAMPLE_RATE)
        if (wav.size > MAX_UPLOAD_BYTES) {
            throw TranscriptionException.TranscriptionServiceError(
                "Audio too large: ${wav.size} bytes (limit $MAX_UPLOAD_BYTES)", modelUsed = MODEL_USED,
            )
        }

        val response = try {
            client.post(url) {
                if (language is STTLanguage.Specific && language.languageCodes.isNotEmpty()) {
                    parameter("language_code", toBcp47(language.languageCodes.first(), Locale.current.region))
                }
                firebaseAuth()
                contentType(ContentType.Application.OctetStream)
                setBody(wav)
            }
        } catch (e: ApiAuthException) {
            throw TranscriptionException.TranscriptionServiceError(
                "kirinki auth failed: ${e.message}", cause = e, modelUsed = MODEL_USED,
            )
        } catch (e: Exception) {
            throw TranscriptionException.TranscriptionNetworkError(e, MODEL_USED)
        }

        val text = response.parseTranscription()
        if (text.isBlank()) {
            throw TranscriptionException.NoSpeechDetected("empty_transcript", modelUsed = MODEL_USED)
        }
        emit(TranscriptionSessionStatus.Transcription(text, MODEL_USED))
    }

    private suspend fun HttpResponse.parseTranscription(): String {
        val body = runCatching { body<KirinkiResponse>() }.getOrNull()
        if (!status.isSuccess()) {
            val detail = body?.error ?: runCatching { bodyAsText() }.getOrNull().orEmpty()
            if (status == HttpStatusCode.Unauthorized) {
                throw TranscriptionException.TranscriptionServiceError(
                    "kirinki rejected the request (401): $detail", modelUsed = MODEL_USED,
                )
            }
            throw TranscriptionException.TranscriptionServiceError(
                "kirinki error (${status.value}): $detail", modelUsed = MODEL_USED,
            )
        }
        if (body == null) {
            throw TranscriptionException.TranscriptionServiceError(
                "kirinki returned an unparseable response", modelUsed = MODEL_USED,
            )
        }
        body.error?.let {
            throw TranscriptionException.TranscriptionServiceError("kirinki error: $it", modelUsed = MODEL_USED)
        }
        return body.text.orEmpty()
    }

    private suspend fun Flow<ByteArray>.concatenate(): ByteArray {
        val chunks = mutableListOf<ByteArray>()
        var total = 0
        collect { chunk ->
            chunks += chunk
            total += chunk.size
        }
        val out = ByteArray(total)
        var offset = 0
        for (chunk in chunks) {
            chunk.copyInto(out, offset)
            offset += chunk.size
        }
        return out
    }

    /** Normalize the captured audio into little-endian 16-bit PCM. */
    private fun ByteArray.toPcm16(encoding: AudioEncoding): ByteArray = when (encoding) {
        AudioEncoding.PCM_16BIT -> this
        AudioEncoding.PCM_FLOAT_32BIT -> {
            val sampleCount = size / 4
            val out = ByteArray(sampleCount * 2)
            for (i in 0 until sampleCount) {
                val b = i * 4
                val bits = (this[b].toInt() and 0xFF) or
                    ((this[b + 1].toInt() and 0xFF) shl 8) or
                    ((this[b + 2].toInt() and 0xFF) shl 16) or
                    ((this[b + 3].toInt() and 0xFF) shl 24)
                val sample = (Float.fromBits(bits) * 32767f)
                    .toInt().coerceIn(-32768, 32767)
                out[i * 2] = (sample and 0xFF).toByte()
                out[i * 2 + 1] = (sample shr 8).toByte()
            }
            out
        }
    }

    /** Prepend a canonical 44-byte PCM WAV header to mono 16-bit samples. */
    private fun wrapWav(pcm16: ByteArray, sampleRate: Int): ByteArray {
        val channels = 1
        val bitsPerSample = 16
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8
        val out = ByteArray(WAV_HEADER_BYTES + pcm16.size)

        fun ascii(offset: Int, s: String) {
            for (i in s.indices) out[offset + i] = s[i].code.toByte()
        }
        fun le32(offset: Int, value: Int) {
            out[offset] = (value and 0xFF).toByte()
            out[offset + 1] = ((value shr 8) and 0xFF).toByte()
            out[offset + 2] = ((value shr 16) and 0xFF).toByte()
            out[offset + 3] = ((value shr 24) and 0xFF).toByte()
        }
        fun le16(offset: Int, value: Int) {
            out[offset] = (value and 0xFF).toByte()
            out[offset + 1] = ((value shr 8) and 0xFF).toByte()
        }

        ascii(0, "RIFF")
        le32(4, 36 + pcm16.size)
        ascii(8, "WAVE")
        ascii(12, "fmt ")
        le32(16, 16)                 // PCM fmt chunk size
        le16(20, 1)                  // audio format = PCM
        le16(22, channels)
        le32(24, sampleRate)
        le32(28, byteRate)
        le16(32, blockAlign)
        le16(34, bitsPerSample)
        ascii(36, "data")
        le32(40, pcm16.size)
        pcm16.copyInto(out, WAV_HEADER_BYTES)
        return out
    }

    private fun resamplePcm16(input: ByteArray, inputRate: Int, outputRate: Int): ByteArray {
        if (inputRate == outputRate) return input

        val inputSamples = input.size / 2
        val outputSamples = (inputSamples.toLong() * outputRate / inputRate).toInt()
        val output = ByteArray(outputSamples * 2)

        for (i in 0 until outputSamples) {
            val srcPos = i.toDouble() * (inputSamples - 1) / (outputSamples - 1).coerceAtLeast(1)
            val srcIndex = srcPos.toInt().coerceIn(0, inputSamples - 2)
            val frac = srcPos - srcIndex

            val s0 = readPcm16Sample(input, srcIndex)
            val s1 = readPcm16Sample(input, srcIndex + 1)
            val interpolated = (s0 + (s1 - s0) * frac).toInt().coerceIn(-32768, 32767)

            output[i * 2] = (interpolated and 0xFF).toByte()
            output[i * 2 + 1] = (interpolated shr 8).toByte()
        }
        return output
    }

    private fun readPcm16Sample(data: ByteArray, sampleIndex: Int): Double {
        val byteIndex = sampleIndex * 2
        val value = (data[byteIndex].toInt() and 0xFF) or (data[byteIndex + 1].toInt() shl 8)
        return value.toShort().toDouble()
    }
}
