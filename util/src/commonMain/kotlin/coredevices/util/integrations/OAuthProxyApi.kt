package coredevices.util.integrations

interface OAuthProxyApi {
    suspend fun getAuthorizationUrl(challenge: String): String
    suspend fun exchangeCodeForToken(code: String, verifier: String): String
    suspend fun refreshToken(): String
    suspend fun revokeToken()
}