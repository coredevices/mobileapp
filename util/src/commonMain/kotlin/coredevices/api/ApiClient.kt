package coredevices.api

import co.touchlab.kermit.Logger
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpRedirect
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.accept
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.headers
import io.ktor.http.ContentType
import io.ktor.http.userAgent
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.parameter.parametersOf
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

abstract class ApiClient(
    version: String,
    timeout: Duration = 30.seconds,
    // Ktor only follows redirects for GET/HEAD by default; enable for clients that must
    // follow 301/302 on POST (e.g. user-configured webhook endpoints).
    followAllRedirects: Boolean = false,
): KoinComponent {
    private val engine by inject<HttpClientEngine> { parametersOf(timeout) }
    protected val client = HttpClient(engine) {
        if (followAllRedirects) {
            install(HttpRedirect) {
                checkHttpMethod = false
            }
        }
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
                encodeDefaults = true
            })
        }
        install(Logging) {
            logger = object : io.ktor.client.plugins.logging.Logger {
                private val logger = Logger.withTag("API")
                override fun log(message: String) {
                    logger.v { message }
                }
            }
            level = LogLevel.INFO
            sanitizeHeader {
                when (it) {
                    "Authorization" -> true
                    else -> false
                }
            }
        }
        install(ContentEncoding) { // This now refers to the plugin
            gzip()
            deflate()
        }
        defaultRequest {
            headers {
                userAgent("CoreApp/$version")
                accept(ContentType.Application.Json)
            }
        }
    }

    protected suspend fun requireUserToken(): String {
        return try {
            val user = Firebase.auth.currentUser ?: throw ApiAuthException("No user")
            user.getIdToken(false) ?: throw ApiAuthException("Failed to get token")
        } catch (e: ApiAuthException) {
            throw e
        } catch (e: Exception) {
            throw ApiAuthException("Network error retrieving token", e)
        }
    }

    protected suspend fun HttpRequestBuilder.firebaseAuth() {
        val token = requireUserToken()
        bearerAuth(token)
    }
}
