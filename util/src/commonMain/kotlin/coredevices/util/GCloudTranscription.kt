package coredevices.util

import androidx.compose.ui.text.intl.Locale
import co.touchlab.kermit.Logger
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.accept
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.io.bytestring.ByteString
import kotlinx.io.bytestring.encode
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

class GCloudTranscription(private val url: String) {
    companion object {
        private val logger = Logger.withTag("GCloudTranscription")
    }
    enum class Encoding {
        ENCODING_UNSPECIFIED,
        LINEAR16,
        FLAC,
        MULAW,
        AMR,
        AMR_WB,
        OGG_OPUS,
        SPEEX_WITH_HEADER_BYTE,
        MP3
    }
    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                encodeDefaults = false
            })
        }
        install(Logging) {
            level = LogLevel.ALL
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    suspend fun recognize(sampleRateHertz: Int, audioContent: ByteString, encoding: Encoding, languageCode: String): String? {
        val token = Firebase.auth.currentUser?.getIdToken(false) ?: throw Exception("User is not authenticated")
        val body = RecognizeRequest(
            audioContent = Base64.encode(audioContent),
            languageCode = languageCode,
            audioFormat = encoding,
            sampleRateHertz = sampleRateHertz
        )
        val response = client.post {
            url(this@GCloudTranscription.url)
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            bearerAuth(token)
            setBody(body)
        }
        if (!response.status.isSuccess()) {
            throw Exception("Failed to recognize audio: ${response.status.value} ${response.bodyAsText()}")
        }
        val responseBody = response.body<RecognizeResponse>()
        logger.d { "results: $responseBody" }
        val result = responseBody.results.firstOrNull()
        return if (result != null) {
            logger.d("Transcription result: ${result.transcript}, confidence: ${result.confidence ?: "N/A"}")
            result.transcript
        } else {
            logger.w("No transcription results found")
            null
        }
    }
}
@Serializable
private data class RecognizeRequest(
    val audioContent: String,
    val languageCode: String,
    val audioFormat: GCloudTranscription.Encoding,
    val sampleRateHertz: Int
)

@Serializable
private data class TranscriptionResult(
    val transcript: String,
    val confidence: Double? = null
)

@Serializable
private data class RecognizeResponse(
    val results: List<TranscriptionResult>
)