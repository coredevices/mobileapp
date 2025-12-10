package coredevices.pebble.services

import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.invoke
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.buildUrl
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.http.parameters
import io.ktor.http.takeFrom
import io.ktor.serialization.kotlinx.json.json
import kotlin.time.Instant
import kotlinx.serialization.Serializable
import kotlin.math.exp
import kotlin.time.Clock
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import kotlin.time.Duration.Companion.seconds

/**
 * A generic OAuth2 service implementation.
 *
 */
abstract class OAuthService(
    private val authorizeUrl: Url,
    private val tokenUrl: Url,
    private val redirectUri: String,
    private val clientId: String?,
    private val clientSecret: String?,
) {
    companion object {
        private val logger = Logger.withTag("OAuthService")
    }

    @OptIn(ExperimentalSerializationApi::class)
    private val httpClient = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                namingStrategy = JsonNamingStrategy.SnakeCase
            })
        }
    }

    fun getAuthorizationUrl(state: String, scope: Set<String>): Url {
        return URLBuilder().apply {
            takeFrom(authorizeUrl)
            parameters.apply {
                append("response_type", "code")
                append("client_id", clientId ?: "")
                append("redirect_uri", redirectUri)
                append("state", state)
                if (scope.isNotEmpty()) {
                    append("scope", scope.joinToString(" "))
                }
            }
        }.build()
    }

    suspend fun getToken(code: String): OAuthTokenResponse? {
        val response = httpClient.post(tokenUrl) {
            contentType(ContentType.Application.Json)
            setBody(
                mapOf(
                    "client_id" to clientId,
                    "client_secret" to clientSecret,
                    "code" to code,
                    "redirect_uri" to redirectUri,
                    "grant_type" to "authorization_code"
                )
            )
        }
        if (!response.status.isSuccess()) {
            logger.e { "Error fetching token: ${response.status}" }
            return null
        }
        val body = response.body<OAuthTokenResponse>()
        return body
    }

    suspend fun refreshToken(refreshToken: String): OAuthTokenResponse? {
        val response = httpClient.post(tokenUrl) {
            contentType(ContentType.Application.Json)
            setBody(
                mapOf(
                    "client_id" to clientId,
                    "client_secret" to clientSecret,
                    "refresh_token" to refreshToken,
                    "grant_type" to "refresh_token"
                )
            )
        }
        if (!response.status.isSuccess()) {
            logger.e { "Error refreshing token: ${response.status}" }
            return null
        }
        val body = response.body<OAuthTokenResponse>()
        return body
    }
}

@Serializable
data class OAuthTokenResponse(
    val accessToken: String,
    val tokenType: String,
    val refreshToken: String? = null,
    val expiresIn: Int? = null,
    val scope: String? = null,
)

@Serializable
data class OAuthToken(
    val response: OAuthTokenResponse,
    val expiresAt: Instant
)

fun OAuthTokenResponse.toOAuthToken(): OAuthToken {
    val expiresAt = if (expiresIn != null) {
        Clock.System.now() + expiresIn.seconds
    } else {
        // No expiration
        Instant.DISTANT_FUTURE
    }
    return OAuthToken(
        response = this,
        expiresAt = expiresAt
    )
}
