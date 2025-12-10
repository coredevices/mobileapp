package coredevices.coreapp.api

import CommonApiConfig
import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class PushService(
    private val config: CommonApiConfig,
) {
    private val logger = Logger.withTag("PushService")

    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }

    suspend fun uploadPushToken(request: PushTokenRequest, googleIdToken: String): Boolean {
        val tokenUrl = config.tokenUrl
        if (tokenUrl == null) {
            logger.i { "tokenUrl is null" }
            return false
        }
        try {
            val response = client.post(tokenUrl) {
                header("X-Google-ID-Token", googleIdToken)
                contentType(ContentType.Application.Json)
                setBody(request)
            }

            if (response.status.isSuccess()) {
                val result = response.body<PushTokenResponse>()
                logger.e { "response: $result" }
                return result.success
            } else {
                logger.e { "Backend returned error: ${response.status}" }
                return false
            }
        } catch (e: Exception) {
            logger.e(e) { "Failed to upload push token" }
            return false
        }
    }
}

@Serializable
data class PushTokenRequest(
    val email: String,
    val push_token: String,
    /** 'android' or 'ios' */
    val platform: String,
    val device_id: String,
    val app_version: String,
)

@Serializable
data class PushTokenResponse(
    val success: Boolean,
    val token_id: String? = null,
    val error: String? = null,
)