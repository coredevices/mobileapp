package coredevices.mcp.client.auth

import io.ktor.client.HttpClient
import io.ktor.client.request.accept
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import io.ktor.http.contentType
import io.ktor.http.encodedPath
import io.ktor.http.isSuccess
import io.ktor.http.parameters
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

interface McpOAuthStore {
    suspend fun read(id: String): String?
    suspend fun write(id: String, value: String)
    suspend fun remove(id: String)
}

fun interface McpOAuthBrowser {
    /** Acquire/bind the per-attempt redirect before client registration. */
    suspend fun openSession(): McpOAuthBrowserSession
}

class McpOAuthBrowserSession(
    val redirectUri: String,
    private val launch: suspend (url: String) -> String,
    private val release: suspend () -> Unit = {},
) {
    suspend fun authenticate(url: String): String = launch(url)
    suspend fun close() = release()
}

/** Adapter for a host-owned fixed callback. Protocol validation still runs before registration. */
class FixedMcpOAuthBrowser(
    private val redirectUri: String,
    private val launch: suspend (url: String, redirectUri: String) -> String,
) : McpOAuthBrowser {
    override suspend fun openSession() = McpOAuthBrowserSession(redirectUri, { launch(it, redirectUri) })
}

interface McpOAuthCrypto {
    fun randomString(): String
    fun challenge(verifier: String): String
}

class McpAuthorizationRejected : Exception("Authorization was rejected. This operation was not retried; reconnect if another explicit attempt fails.")
class McpSignInRequired : Exception("Sign in to this MCP server in its settings.")
class McpOAuthException(message: String) : Exception(message)

@Serializable
internal class ResourceMetadata(
    val resource: String,
    @SerialName("authorization_servers") val authorizationServers: List<String>,
    @SerialName("scopes_supported") val scopes: List<String>? = null,
)

@Serializable
internal class ServerMetadata(
    val issuer: String,
    @SerialName("authorization_endpoint") val authorizationEndpoint: String,
    @SerialName("token_endpoint") val tokenEndpoint: String,
    @SerialName("registration_endpoint") val registrationEndpoint: String? = null,
    @SerialName("code_challenge_methods_supported") val challengeMethods: List<String> = emptyList(),
    @SerialName("authorization_response_iss_parameter_supported") val responseIssuer: Boolean = false,
    @SerialName("grant_types_supported") val grants: List<String> = listOf("authorization_code"),
)

@Serializable
private class Registration(
    @SerialName("redirect_uris") val redirects: List<String>,
    @SerialName("client_name") val name: String = "Pebble MCP client",
    @SerialName("token_endpoint_auth_method") val authMethod: String = "none",
    @SerialName("grant_types") val grants: List<String> = listOf("authorization_code", "refresh_token"),
    @SerialName("response_types") val responses: List<String> = listOf("code"),
)

@Serializable
private class RegisteredClient(
    @SerialName("client_id") val id: String,
    @SerialName("token_endpoint_auth_method") val authMethod: String,
    @SerialName("redirect_uris") val redirects: List<String>,
    @SerialName("client_secret") val secret: String? = null,
)

@Serializable
private class TokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("token_type") val type: String,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("expires_in") val expiresIn: Long? = null,
)

@Serializable
private class OAuthError(val error: String)

@Serializable
private class Credential(
    val resource: String,
    val server: ServerMetadata,
    val clientId: String,
    val accessToken: String,
    val refreshToken: String?,
    val expiresAt: Long?,
    val refreshSkewSeconds: Long = 30,
)

/** MCP 2025-11-25: public clients using code + S256, DCR or a pre-registered client ID. */
class McpOAuth(
    httpClient: HttpClient,
    private val store: McpOAuthStore,
    private val browser: McpOAuthBrowser,
    private val crypto: McpOAuthCrypto,
    private val clock: Clock = Clock.System,
) {
    private val client = httpClient.config { followRedirects = false; expectSuccess = false }
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val credentialsLock = Mutex()
    private val authorizationLock = Mutex()

    suspend fun authorize(id: String, resource: String, clientId: String? = null) = authorizationLock.withLock {
        secureUrl(resource)
        val challenge = client.get(resource) { accept(ContentType("application", "json")) }
        val auth = bearerChallenge(challenge.headers.getAll(HttpHeaders.WWWAuthenticate).orEmpty())
        val metadataUrls = auth["resource_metadata"]?.let { listOf(it) }.orEmpty() + resourceMetadataUrls(resource)
        val resourceInfo = discover(metadataUrls) { json.decodeFromString<ResourceMetadata>(it) }
        if (Url(resourceInfo.resource) != Url(resource)) fail("MCP resource metadata does not match this server.")
        val issuer = resourceInfo.authorizationServers.firstOrNull() ?: fail("No authorization server was advertised.")
        secureUrl(issuer, issuer = true)
        val server = discover(issuerMetadataUrls(issuer)) { json.decodeFromString<ServerMetadata>(it) }
        if (server.issuer != issuer) fail("Authorization server issuer mismatch.")
        secureUrl(server.authorizationEndpoint)
        secureUrl(server.tokenEndpoint)
        if ("S256" !in server.challengeMethods) fail("This server does not advertise PKCE S256.")
        if (!server.responseIssuer) fail("This MCP client requires authorization response issuer identification (RFC 9207), which this server does not advertise.")
        val session = browser.openSession()
        try {
            val redirectUri = session.redirectUri
            validateMcpRedirectUri(redirectUri)
            val registeredId = clientId?.takeIf { it.isNotBlank() } ?: register(server, redirectUri)
            val state = crypto.randomString()
            val verifier = crypto.randomString()
            val scope = auth["scope"] ?: resourceInfo.scopes?.joinToString(" ")
            val authorizationUrl = URLBuilder(server.authorizationEndpoint).apply {
                parameters["response_type"] = "code"
                parameters["client_id"] = registeredId
                parameters["redirect_uri"] = redirectUri
                parameters["resource"] = resourceInfo.resource
                parameters["state"] = state
                parameters["code_challenge"] = crypto.challenge(verifier)
                parameters["code_challenge_method"] = "S256"
                scope?.takeIf { it.isNotBlank() }?.let { parameters["scope"] = it }
            }.buildString()
            val callback = try {
                withTimeout(5.minutes) { session.authenticate(authorizationUrl) }
            } catch (_: TimeoutCancellationException) {
                fail("Sign-in timed out. Try connecting again.")
            }
            val code = validateCallback(callback, redirectUri, state, server)
            val tokens = exchange(server.tokenEndpoint, parameters {
                append("grant_type", "authorization_code")
                append("client_id", registeredId)
                append("redirect_uri", redirectUri)
                append("resource", resourceInfo.resource)
                append("code", code)
                append("code_verifier", verifier)
            })
            withContext(NonCancellable) {
                credentialsLock.withLock {
                    store.write(id, json.encodeToString(credential(resource, server, registeredId, tokens)))
                }
            }
        } finally {
            withContext(NonCancellable) { session.close() }
        }
    }

    suspend fun isConnected(id: String): Boolean = credentialsLock.withLock {
        val credential = try {
            readCredential(id)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return@withLock false
        } ?: return@withLock false
        credential.refreshToken != null || credential.expiresAt == null || credential.expiresAt > clock.now().epochSeconds + credential.refreshSkewSeconds
    }

    suspend fun disconnect(id: String) = credentialsLock.withLock { store.remove(id) }

    suspend fun accessToken(id: String, resource: String): String = credentialsLock.withLock {
        val credential = readCredential(id) ?: throw McpSignInRequired()
        if (Url(credential.resource) != Url(resource)) throw McpSignInRequired()
        if (credential.expiresAt == null || credential.expiresAt > clock.now().epochSeconds + credential.refreshSkewSeconds) {
            return@withLock credential.accessToken
        }
        val refresh = credential.refreshToken ?: throw McpSignInRequired()
        // A refresh may consume a rotating token even if its response is lost.
        store.remove(id)
        val tokens = try {
            exchange(credential.server.tokenEndpoint, parameters {
                append("grant_type", "refresh_token")
                append("client_id", credential.clientId)
                append("resource", credential.resource)
                append("refresh_token", refresh)
            })
        } catch (e: McpSignInRequired) {
            store.remove(id)
            throw e
        }
        val updated = credential(credential.resource, credential.server, credential.clientId, tokens, refresh)
        withContext(NonCancellable) { store.write(id, json.encodeToString(updated)) }
        updated.accessToken
    }

    // Do not replay MCP operations. Expire only the token the server actually rejected.
    suspend fun rejected(id: String, rejectedToken: String) = credentialsLock.withLock {
        val credential = readCredential(id) ?: return@withLock
        if (credential.accessToken == rejectedToken) {
            store.write(id, json.encodeToString(Credential(
                credential.resource, credential.server, credential.clientId,
                credential.accessToken, credential.refreshToken, 0,
            )))
        }
    }

    private suspend fun <T> discover(urls: List<String>, decode: (String) -> T): T {
        for (url in urls.distinct()) {
            secureUrl(url)
            val response = client.get(url) { accept(ContentType.Application.Json) }
            if (response.status.isSuccess()) {
                val body = response.bodyAsText()
                try {
                    return decode(body)
                } catch (_: SerializationException) {
                    // Some sites serve their HTML application for unknown well-known paths.
                }
            }
        }
        fail("Could not discover OAuth metadata for this server.")
    }

    private suspend fun register(server: ServerMetadata, redirectUri: String): String {
        val endpoint = server.registrationEndpoint ?: fail("Enter a client ID registered with this authorization server.")
        secureUrl(endpoint)
        val response = client.post(endpoint) {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(Registration(
                listOf(redirectUri),
                grants = listOf("authorization_code") + listOf("refresh_token").filter { it in server.grants },
            )))
        }
        if (!response.status.isSuccess()) fail("Client registration failed (${response.status.value}).")
        val registered = try {
            json.decodeFromString<RegisteredClient>(response.bodyAsText())
        } catch (_: SerializationException) {
            fail("Invalid public-client registration response.")
        }
        if (registered.id.isBlank() || registered.authMethod != "none" || registered.secret != null ||
            redirectUri !in registered.redirects) fail("This server did not register a public client with the expected redirect.")
        return registered.id
    }

    private suspend fun exchange(endpoint: String, form: io.ktor.http.Parameters): TokenResponse {
        secureUrl(endpoint)
        val response = client.submitForm(endpoint, form)
        if (response.status == HttpStatusCode.BadRequest || response.status == HttpStatusCode.Unauthorized) {
            val error = try {
                json.decodeFromString<OAuthError>(response.bodyAsText()).error
            } catch (_: SerializationException) {
                null
            }
            if (error == "invalid_grant") throw McpSignInRequired()
            val known = setOf("invalid_request", "invalid_client", "unauthorized_client", "unsupported_grant_type", "invalid_scope")
            fail(if (error in known) "OAuth token request failed: $error." else "OAuth token request failed (${response.status.value}).")
        }
        if (!response.status.isSuccess()) fail("Token request failed (${response.status.value}).")
        val tokens = try {
            json.decodeFromString<TokenResponse>(response.bodyAsText())
        } catch (_: SerializationException) {
            fail("Invalid OAuth token response.")
        }
        if (!tokens.type.equals("Bearer", ignoreCase = true) || tokens.accessToken.isBlank() ||
            !Regex("[A-Za-z0-9._~+/-]+=*").matches(tokens.accessToken) ||
            tokens.expiresIn?.let { it <= 0 || it > 315360000L } == true) fail("Invalid OAuth token response.")
        return tokens
    }

    private suspend fun readCredential(id: String): Credential? {
        val saved = store.read(id) ?: return null
        return try {
            json.decodeFromString<Credential>(saved)
        } catch (_: SerializationException) {
            store.remove(id)
            throw McpSignInRequired()
        }
    }

    private fun credential(resource: String, server: ServerMetadata, id: String, tokens: TokenResponse, refresh: String? = null) =
        Credential(resource, server, id, tokens.accessToken, tokens.refreshToken ?: refresh,
            tokens.expiresIn?.let { clock.now().epochSeconds + it },
            tokens.expiresIn?.let { minOf(30, it / 2) } ?: 0)
}

internal fun validateMcpRedirectUri(value: String) {
    val url = Url(value)
    val loopback = url.protocol == URLProtocol.HTTP && url.host in setOf("localhost", "127.0.0.1", "::1", "[::1]")
    if ((!loopback && url.protocol != URLProtocol.HTTPS) || url.host.isBlank() ||
        !url.user.isNullOrEmpty() || !url.password.isNullOrEmpty() || url.fragment.isNotEmpty() ||
        url.parameters.names().isNotEmpty()) {
        fail("MCP sign-in requires an app-owned HTTPS or loopback callback without credentials, query or fragment. This browser adapter is not configured for it.")
    }
}

internal fun secureUrl(value: String, issuer: Boolean = false): Url {
    val url = Url(value)
    if (url.protocol != URLProtocol.HTTPS || url.host.isBlank() || !url.user.isNullOrEmpty() ||
        !url.password.isNullOrEmpty() || url.fragment.isNotEmpty() ||
        (issuer && url.parameters.names().isNotEmpty())) fail("OAuth requires an HTTPS URL without user credentials or a fragment.")
    return url
}

internal fun resourceMetadataUrls(resource: String): List<String> {
    val url = secureUrl(resource)
    val origin = URLBuilder(url).apply { encodedPath = ""; parameters.clear() }.buildString().trimEnd('/')
    return listOf("$origin/.well-known/oauth-protected-resource${url.encodedPath.trimEnd('/')}",
        "$origin/.well-known/oauth-protected-resource")
}

internal fun issuerMetadataUrls(issuer: String): List<String> {
    val url = secureUrl(issuer, issuer = true)
    val origin = URLBuilder(url).apply { encodedPath = ""; parameters.clear() }.buildString().trimEnd('/')
    val path = url.encodedPath.trimEnd('/')
    return listOf("$origin/.well-known/oauth-authorization-server$path",
        "$origin/.well-known/openid-configuration$path", "${issuer.trimEnd('/')}/.well-known/openid-configuration")
}

internal fun validateCallback(callback: String, redirectUri: String, state: String, server: ServerMetadata): String {
    val url = Url(callback)
    val expected = Url(redirectUri)
    if (url.protocol != expected.protocol || url.host != expected.host || url.port != expected.port ||
        url.encodedPath != expected.encodedPath || url.fragment.isNotEmpty() || !url.user.isNullOrEmpty() ||
        !url.password.isNullOrEmpty()) fail("OAuth callback does not match the redirect URI.")
    fun single(name: String): String? {
        val values = url.parameters.getAll(name) ?: return null
        if (values.size != 1) fail("Duplicate OAuth callback parameter.")
        return values.single()
    }
    if (single("state") != state) fail("OAuth callback state mismatch.")
    val issuer = single("iss")
    if (issuer == null) fail("Authorization server did not identify itself in the callback (RFC 9207).")
    if (issuer != server.issuer) fail("OAuth callback issuer mismatch.")
    if (single("error") != null) fail("Authorization was declined or failed.")
    return single("code")?.takeIf { it.isNotBlank() } ?: fail("OAuth callback returned no code.")
}

internal fun bearerChallenge(headers: List<String>): Map<String, String> {
    val parameter = Regex("""\s*([A-Za-z_][A-Za-z_0-9-]*)\s*=\s*(?:"((?:[^"\\]|\\.)*)"|([^\s,"]+))\s*""")
    for (header in headers) {
        val parts = mutableListOf<String>()
        var quoted = false
        var escaped = false
        var start = 0
        header.forEachIndexed { index, char ->
            when {
                escaped -> escaped = false
                quoted && char == '\\' -> escaped = true
                char == '"' -> quoted = !quoted
                !quoted && char == ',' -> { parts += header.substring(start, index).trim(); start = index + 1 }
            }
        }
        if (quoted || escaped) fail("Malformed authentication challenge.")
        parts += header.substring(start).trim()
        val scheme = Regex("^Bearer(?:\\s+|$)", RegexOption.IGNORE_CASE)
        val index = parts.indexOfFirst { scheme.containsMatchIn(it) }
        if (index == -1) continue
        val result = mutableMapOf<String, String>()
        for ((offset, part) in parts.drop(index).withIndex()) {
            val value = if (offset == 0) part.replaceFirst(scheme, "") else part
            if (value.isEmpty()) continue
            if (offset > 0 && Regex("^[A-Za-z][A-Za-z0-9_-]*\\s+[^\\s=]").containsMatchIn(value)) break
            val match = parameter.matchEntire(value) ?: fail("Malformed Bearer challenge parameter.")
            val key = match.groupValues[1].lowercase()
            val decoded = match.groups[2]?.value?.replace(Regex("\\\\(.)"), "$1") ?: match.groupValues[3]
            if (result.put(key, decoded) != null) fail("Duplicate Bearer challenge parameter.")
        }
        return result
    }
    return emptyMap()
}

private fun fail(message: String): Nothing = throw McpOAuthException(message)
