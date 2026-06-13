package coredevices.api

import co.touchlab.kermit.Logger
import coredevices.util.CommonBuildKonfig
import io.ktor.client.call.body
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.post
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@Serializable
private data class WisprTokenResponse(
    val access_token: String,
    val expires_in: Long,
)

class WisprFlowAuth : ApiClient(CommonBuildKonfig.USER_AGENT_VERSION) {

    companion object {
        private val logger = Logger.withTag("WisprFlowAuth")
        private val TOKEN_REFRESH_BUFFER = 5.minutes
    }

    private val mutex = Mutex()
    private var cachedToken: String? = null
    private var tokenExpiresAt: Instant? = null

    suspend fun getAccessToken(forceRefresh: Boolean = false): String? {
        val authUrl = CommonBuildKonfig.WISPR_AUTH_URL ?: throw IllegalStateException("WISPR_AUTH_URL is not configured")

        mutex.withLock {
            val expires = tokenExpiresAt
            val now = Clock.System.now()
            if (cachedToken != null && expires != null && now < expires && !forceRefresh) {
                logger.d { "Using cached Wispr access token, expires in ${(expires - now).inWholeSeconds}s" }
                return cachedToken
            }

            return try {
                val response = client.post("$authUrl/token") {
                    firebaseAuth()
                    expectSuccess = true
                }
                val tokenResponse = response.body<WisprTokenResponse>()
                val expiresIn = tokenResponse.expires_in
                cachedToken = tokenResponse.access_token
                val effectiveTtl = (expiresIn.seconds - TOKEN_REFRESH_BUFFER).coerceAtLeast(1.seconds)
                tokenExpiresAt = Clock.System.now() + effectiveTtl
                logger.d { "Obtained Wispr access token, expires in ${expiresIn}s" }
                cachedToken
            } catch (e: Exception) {
                logger.e(e) { "Failed to obtain Wispr access token" }
                cachedToken = null
                tokenExpiresAt = null
                null
            }
        }
    }

    fun invalidateToken() {
        cachedToken = null
        tokenExpiresAt = null
    }
}