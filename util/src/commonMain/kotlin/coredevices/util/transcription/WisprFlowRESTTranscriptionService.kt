package coredevices.util.transcription

import co.touchlab.kermit.Logger
import coredevices.api.AppInfo
import coredevices.api.WisprContext
import coredevices.api.WisprConversationContext
import coredevices.api.WisprConversationMessage
import coredevices.api.WisprFlowAuth
import coredevices.api.WisprJson
import coredevices.api.WisprTranscribeRequest
import coredevices.api.WisprTranscribeResponse
import coredevices.util.AudioEncoding
import coredevices.util.CommonBuildKonfig
import coredevices.util.writeWavHeader
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import kotlinx.io.write
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Single-shot transcription via the Wispr Flow REST API.
 */
class WisprFlowRESTTranscriptionService(
    private val wisprFlowAuth: WisprFlowAuth,
) : TranscriptionService {
    companion object {
        private val logger = Logger.withTag("WisprFlowRESTTranscriptionService")
        private const val WISPR_REST_URL = "https://platform-api.wisprflow.ai/api/v1/dash/client_api"
        private const val TARGET_SAMPLE_RATE = 16000
        private const val MODEL = "wisprflow"
        // API limit is 25MB / ~6 minutes of 16kHz mono PCM16.
        private const val MAX_AUDIO_BYTES = 25 * 1024 * 1024
    }

    private val client = HttpClient {
        install(ContentNegotiation) {
            json(WisprJson)
        }
    }

    override val onInitialized: Channel<Boolean> = Channel()

    private suspend fun resolveAccessToken(forceRefresh: Boolean = false): String {
        return withTimeout(5.seconds) {
            checkNotNull(wisprFlowAuth.getAccessToken(forceRefresh = forceRefresh)) {
                "WISPR access token unavailable"
            }
        }
    }

    override suspend fun isAvailable(): Boolean = CommonBuildKonfig.WISPR_AUTH_URL != null

    @OptIn(ExperimentalEncodingApi::class)
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
        if (audioStreamFrames == null) {
            return@flow
        }

        emit(TranscriptionSessionStatus.Open)

        // Buffer the whole stream, resampling to the target rate as chunks arrive.
        val pcm = Buffer()
        audioStreamFrames.collect { chunk ->
            val resampled = if (sampleRate != TARGET_SAMPLE_RATE) {
                resamplePcm16(chunk, sampleRate, TARGET_SAMPLE_RATE)
            } else {
                chunk
            }
            pcm.write(resampled)
            if (pcm.size > MAX_AUDIO_BYTES) {
                throw TranscriptionException.TranscriptionServiceError(
                    "Audio exceeds Wispr REST size limit (25MB)",
                    modelUsed = MODEL,
                )
            }
        }

        val pcmBytes = pcm.readByteArray()
        if (pcmBytes.isEmpty()) {
            throw TranscriptionException.NoSpeechDetected("empty_audio", modelUsed = MODEL)
        }

        val wavBytes = Buffer().apply {
            writeWavHeader(TARGET_SAMPLE_RATE, pcmBytes.size)
            write(pcmBytes)
        }.readByteArray()

        val request = WisprTranscribeRequest(
            audio = Base64.encode(wavBytes),
            language = when (language) {
                // Omit to enable server-side auto-detection.
                is STTLanguage.Automatic -> null
                is STTLanguage.Specific -> language.languageCodes.toList()
            },
            context = buildContext(conversationContext, dictionaryContext, contentContext),
        )

        try {
            val text = postTranscribe(request)
            if (text.isNullOrBlank()) {
                throw TranscriptionException.NoSpeechDetected("no_transcript", modelUsed = MODEL)
            }
            emit(TranscriptionSessionStatus.Transcription(text, MODEL))
        } catch (e: TranscriptionException) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            // Network failures are transient and worth retrying.
            logger.e(e) { "WisprFlow REST network error: ${e.message}" }
            throw TranscriptionException.TranscriptionNetworkError(e, modelUsed = MODEL)
        } catch (e: Exception) {
            logger.e(e) { "WisprFlow REST transcription failed: ${e.message}" }
            throw TranscriptionException.TranscriptionServiceError(
                "WisprFlow REST error: ${e.message}",
                cause = e,
                modelUsed = MODEL,
            )
        }
    }

    private suspend fun postTranscribe(request: WisprTranscribeRequest): String? {
        var response = sendRequest(request, resolveAccessToken())
        if (response.status == HttpStatusCode.Unauthorized) {
            logger.w { "REST request returned 401, refreshing token and retrying" }
            response = sendRequest(request, resolveAccessToken(forceRefresh = true))
        }
        if (!response.status.isSuccess()) {
            throw TranscriptionException.TranscriptionServiceError(
                "WisprFlow REST returned ${response.status}",
                modelUsed = MODEL,
            )
        }
        val body = response.body<WisprTranscribeResponse>()
        logger.d { "WisprFlow request id: ${body.id}" }
        return body.text
    }

    private suspend fun sendRequest(request: WisprTranscribeRequest, accessToken: String): HttpResponse {
        return client.post(WISPR_REST_URL) {
            headers { append(HttpHeaders.Authorization, "Bearer $accessToken") }
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    private fun buildContext(
        conversationContext: STTConversationContext?,
        dictionaryContext: List<String>?,
        contentContext: String?,
    ): WisprContext {
        val nameSplit = Firebase.auth.currentUser?.displayName?.split(" ", limit = 2)
        return WisprContext(
            app = AppInfo(name = "Core Devices", type = "other"),
            dictionaryContext = dictionaryContext,
            userFirstName = nameSplit?.firstOrNull(),
            userLastName = nameSplit?.lastOrNull(),
            contentText = contentContext,
            conversation = conversationContext
                ?.takeIf { it.messages.isNotEmpty() || it.participants.isNotEmpty() }
                ?.let {
                    WisprConversationContext(
                        id = it.id,
                        participants = it.participants,
                        messages = it.messages.map { msg ->
                            WisprConversationMessage(
                                role = when (msg.role) {
                                    STTConvoRole.User -> "user"
                                    STTConvoRole.Human -> "human"
                                    STTConvoRole.Assistant -> "assistant"
                                },
                                content = msg.content
                            )
                        }
                    )
                }
        )
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
            val interpolated = (s0 + (s1 - s0) * frac).toInt().coerceIn(-32768, 32767).toShort()

            output[i * 2] = (interpolated.toInt() and 0xFF).toByte()
            output[i * 2 + 1] = (interpolated.toInt() shr 8).toByte()
        }

        return output
    }

    private fun readPcm16Sample(data: ByteArray, sampleIndex: Int): Double {
        val byteIndex = sampleIndex * 2
        val value = (data[byteIndex].toInt() and 0xFF) or (data[byteIndex + 1].toInt() shl 8)
        return value.toShort().toDouble()
    }
}