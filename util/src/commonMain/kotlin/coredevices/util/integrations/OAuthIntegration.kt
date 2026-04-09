package coredevices.util.integrations

import PlatformUiContext
import co.touchlab.kermit.Logger
import coredevices.util.OAuthRedirectHandler
import coredevices.util.Platform
import kotlinx.coroutines.flow.first
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

internal expect fun generateSecureRandomString(length: Int, charset: List<Char>): String
internal expect fun sha256(input: String): String

abstract class OAuthIntegration(
    private val api: OAuthProxyApi,
    private val tokenStorage: IntegrationTokenStorage,
    private val tokenStorageKey: String
): Integration, KoinComponent {
    private val platform: Platform by inject()
    private val oAuthRedirectHandler: OAuthRedirectHandler by inject()

    /**
     * The path segment used to identify OAuth redirects for this integration.
     * This is matched against the last path segment of the redirect URI.
     */
    protected abstract val oauthPathSegment: String

    companion object {
        private val logger = Logger.withTag("OAuthIntegration")
    }

    private fun generateCodeVerifier(): String {
        val letters = ('0'..'9') + ('a'..'z') + ('A'..'Z')
        return generateSecureRandomString(128, letters)
    }

    private var token: String? = null
    protected suspend fun requireToken() = token ?: tokenStorage.getToken(tokenStorageKey)?.also {
        token = it
    } ?: error("No token")
    private suspend fun saveToken(token: String) {
        this.token = token
        tokenStorage.saveToken(tokenStorageKey, token)
    }

    override suspend fun unlink() {
        token = null
        tokenStorage.deleteToken(tokenStorageKey)
        try {
            api.revokeToken()
        } catch (e: Exception) {
            logger.e(e) { "Failed to revoke token" }
        }
    }

    suspend fun isAuthorized(): Boolean = tokenStorage.getToken(tokenStorageKey) != null

    private fun String.toChallenge(): String {
        return sha256(this)
    }

    override suspend fun signIn(uiContext: PlatformUiContext): Boolean {
        val verifier = generateCodeVerifier()
        val challenge = verifier.toChallenge()
        val url = api.getAuthorizationUrl(challenge)
        platform.openUrl(url)
        val uri = oAuthRedirectHandler.oauthRedirects.first {
            it.host == "oauth" && it.lastPathSegment == oauthPathSegment
        }
        val error = uri.getQueryParameter("error")
        if (error != null) {
            error("OAuth error: $error")
        }
        val code = uri.getQueryParameter("code") ?: error("No code in redirect URI")
        val token = api.exchangeCodeForToken(code, verifier)
        saveToken(token)
        return true
    }
}