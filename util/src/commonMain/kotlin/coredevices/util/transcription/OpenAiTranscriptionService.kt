package coredevices.util.transcription

import co.touchlab.kermit.Logger
import coredevices.api.ApiClient
import coredevices.util.AudioEncoding
import coredevices.util.CommonBuildKonfig
import coredevices.util.CoreConfigFlow
import coredevices.util.OpenAiSTTConfig
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable
import kotlin.time.Duration.Companion.seconds

// OpenAiTranscriptionService transcribes against any user-configured OpenAI-compatible
// /audio/transcriptions endpoint. Like KirinkiTranscriptionService it is batch: the whole
// clip is buffered and uploaded as a single multipart request.
class OpenAiTranscriptionService(
    private val coreConfigFlow: CoreConfigFlow,
) : ApiClient(CommonBuildKonfig.USER_AGENT_VERSION, timeout = REQUEST_TIMEOUT),
    TranscriptionService {

    companion object {
        private val logger = Logger.withTag("OpenAiTranscriptionService")
        private const val MODEL_USED = "openai"

        private const val TARGET_SAMPLE_RATE = 16_000
        private const val WAV_HEADER_BYTES = 44
        private const val TRANSCRIPTIONS_PATH = "audio/transcriptions"

        private val REQUEST_TIMEOUT = 120.seconds
    }

    @Serializable
    private data class OpenAiResponse(
        val text: String? = null,
        val error: OpenAiError? = null,
    )

    @Serializable
    private data class OpenAiError(
        val message: String? = null,
        val type: String? = null,
        val code: String? = null,
    )

    private val config: OpenAiSTTConfig
        get() = coreConfigFlow.value.sttConfig.openAi

    override val onInitialized: Channel<Boolean> = Channel()

    override suspend fun isAvailable(): Boolean = config.isConfigured

    override suspend fun transcribe(
        audioStreamFrames: Flow<ByteArray>?,
        sampleRate: Int,
        language: STTLanguage,
        conversationContext: STTConversationContext?,
        dictionaryContext: List<String>?,
        contentContext: String?,
        encoding: AudioEncoding,
    ): Flow<TranscriptionSessionStatus> = flow {
        val openAi = config
        if (!openAi.isConfigured) {
            throw TranscriptionException.TranscriptionServiceUnavailable(MODEL_USED)
        }
        if (audioStreamFrames == null) {
            // No mic capture here; this backend only transcribes supplied audio.
            throw TranscriptionException.TranscriptionServiceError(
                "OpenAI transcription requires audio stream frames", modelUsed = MODEL_USED,
            )
        }

        emit(TranscriptionSessionStatus.Open)

        // The endpoint expects 16 kHz / 16-bit mono PCM.
        val raw = audioStreamFrames.concatenate()
        val pcm16 = raw.toPcm16(encoding)
        val resampled = if (sampleRate != TARGET_SAMPLE_RATE) {
            resamplePcm16(pcm16, sampleRate, TARGET_SAMPLE_RATE)
        } else {
            pcm16
        }
        val wav = wrapWav(resampled, TARGET_SAMPLE_RATE)

        val body = MultiPartFormDataContent(
            formData {
                append("model", openAi.model)
                append("response_format", "json")
                (language as? STTLanguage.Specific)?.languageCodes?.firstOrNull()?.let {
                    append("language", it)
                }
                contentContext?.takeIf { it.isNotBlank() }?.let { append("prompt", it) }
                append(
                    "file",
                    wav,
                    Headers.build {
                        append(HttpHeaders.ContentType, "audio/wav")
                        append(HttpHeaders.ContentDisposition, "filename=\"audio.wav\"")
                    },
                )
            }
        )

        val response = try {
            client.post(openAi.transcriptionsUrl()) {
                if (openAi.apiKey.isNotBlank()) bearerAuth(openAi.apiKey)
                setBody(body)
            }
        } catch (e: Exception) {
            throw TranscriptionException.TranscriptionNetworkError(e, MODEL_USED)
        }

        val text = response.parseTranscription()
        if (text.isBlank()) {
            throw TranscriptionException.NoSpeechDetected("empty_transcript", modelUsed = MODEL_USED)
        }
        emit(TranscriptionSessionStatus.Transcription(text, MODEL_USED))
    }

    private fun OpenAiSTTConfig.transcriptionsUrl(): String {
        val base = endpoint.trim().trimEnd('/')
        return if (base.endsWith(TRANSCRIPTIONS_PATH)) base else "$base/$TRANSCRIPTIONS_PATH"
    }

    private suspend fun HttpResponse.parseTranscription(): String {
        val parsed = runCatching { body<OpenAiResponse>() }.getOrNull()
        if (!status.isSuccess()) {
            val detail = parsed?.error?.message ?: runCatching { bodyAsText() }.getOrNull().orEmpty()
            if (status == HttpStatusCode.Unauthorized || status == HttpStatusCode.Forbidden) {
                throw TranscriptionException.TranscriptionServiceError(
                    "OpenAI endpoint rejected the API key (${status.value}): $detail", modelUsed = MODEL_USED,
                )
            }
            throw TranscriptionException.TranscriptionServiceError(
                "OpenAI endpoint error (${status.value}): $detail", modelUsed = MODEL_USED,
            )
        }
        if (parsed == null) {
            throw TranscriptionException.TranscriptionServiceError(
                "OpenAI endpoint returned an unparseable response", modelUsed = MODEL_USED,
            )
        }
        parsed.error?.message?.let {
            throw TranscriptionException.TranscriptionServiceError("OpenAI error: $it", modelUsed = MODEL_USED)
        }
        return parsed.text.orEmpty()
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
