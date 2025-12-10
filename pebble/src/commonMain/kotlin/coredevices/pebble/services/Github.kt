package coredevices.pebble.services

import co.touchlab.kermit.Logger
import com.russhwolf.settings.Settings
import coredevices.pebble.account.GithubAccount
import coredevices.util.CommonBuildKonfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.accept
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.Url
import io.ktor.http.isSuccess
import io.ktor.http.parseUrl
import io.ktor.serialization.kotlinx.json.json
import kotlin.time.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy

class Github(
    private val githubAccount: GithubAccount,
    private val settings: Settings
): OAuthService(
    authorizeUrl = Url("https://github.com/login/oauth/authorize"),
    tokenUrl = Url("https://github.com/login/oauth/access_token"),
    redirectUri = "https://cloud.repebble.com/githubAuth",
    clientId = CommonBuildKonfig.GITHUB_CLIENT_ID,
    clientSecret = CommonBuildKonfig.GITHUB_CLIENT_SECRET,
) {
    companion object {
        private val logger = Logger.withTag("Github")
        const val STATE_SETTING_KEY = "github_oauth_state"
    }

    private val httpClient = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                namingStrategy = JsonNamingStrategy.SnakeCase
            })
        }
    }

    private suspend fun getOrRefreshToken(): String? {
        val token = githubAccount.loggedIn.value ?: run {
            logger.i { "not logged in" }
            return null
        }
        val now = Clock.System.now()
        if (token.response.refreshToken == null || token.expiresAt > now) {
            return token.response.accessToken
        } else {
            logger.i { "token expired, refreshing" }
            val response = refreshToken(token.response.refreshToken) ?: run {
                logger.w { "refresh token failed, logging out" }
                githubAccount.setToken(null)
                return null
            }
            githubAccount.setToken(response.toOAuthToken())
            return response.accessToken
        }
    }

    suspend fun user(): GithubUser? {
        val token = getOrRefreshToken() ?: return null
        return try {
            val res = httpClient.get {
                url("https://api.github.com/user")
                headers {
                    accept(ContentType.Application.Json)
                    bearerAuth(token)
                }
            }
            if (!res.status.isSuccess()) {
                logger.w { "Failed to fetch user info, status: ${res.status}" }
                githubAccount.setToken(null)
                return null
            }
            return res.body()
        } catch (e: Exception) {
            logger.w(e) { "Error fetching user info: ${e.message}" }
            null
        }
    }

    suspend fun handleRedirect(code: String?, state: String?, error: String?): Boolean {
        val stateExpected = settings.getStringOrNull(STATE_SETTING_KEY)
        if (state == null || stateExpected == null || state != stateExpected) {
            logger.e { "State mismatch in OAuth redirect" }
            return false
        }
        return when {
            error != null -> {
                logger.w { "GitHub OAuth error: $error" }
                return false
            }
            code == null -> {
                logger.w { "GitHub OAuth code is null" }
                return false
            }
            else -> {
                val tokenResponse = getToken(code) ?: run {
                    logger.w { "Failed to fetch token" }
                    return false
                }
                githubAccount.setToken(tokenResponse.toOAuthToken())
                true
            }
        }
    }
}

// Incomplete
@Serializable
data class GithubUser(
    val login: String,
    val id: Long,
    val avatarUrl: String,
    val name: String?,
)