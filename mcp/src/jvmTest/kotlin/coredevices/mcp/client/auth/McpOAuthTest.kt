package coredevices.mcp.client.auth

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import io.ktor.http.parseQueryString
import java.security.MessageDigest
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

private const val RESOURCE = "https://mcp.example/mcp"
private const val ISSUER = "https://login.example/tenant"
private const val REDIRECT = "https://app.example/oauth/mcp"

class McpOAuthTest {
    private class Store : McpOAuthStore {
        val values = mutableMapOf<String, String>()
        var beforeWrite: (suspend (String) -> Unit)? = null
        override suspend fun read(id: String): String? = values[id]
        override suspend fun write(id: String, value: String) { beforeWrite?.invoke(value); values[id] = value }
        override suspend fun remove(id: String) { values.remove(id) }
    }

    private class Fixture {
        val store = Store()
        var now = 1000L
        var randomValues = 0
        var challengeIncludesMetadata = true
        var refreshes = 0
        var registrations = 0
        var registrationBody: String? = null
        var registrationStatus = HttpStatusCode.Created
        var registeredGrants: List<String>? = null
        var expectedGrants = listOf("authorization_code", "refresh_token")
        var tokenRequests = 0
        var tokenDelayMillis = 0L
        var tokenStarted: CompletableDeferred<Unit>? = null
        var expiresIn = 60
        var includeRefresh = true
        var tokenBody: String? = null
        var tokenStatus = HttpStatusCode.OK
        var firstMetadataBody: String? = null
        var callbackFailure: Exception? = null
        var browserDelayMillis = 0L
        var refreshStatus = HttpStatusCode.OK
        var resourceMetadata = """{"resource":"$RESOURCE","authorization_servers":["$ISSUER"],"scopes_supported":["read","operate"]}"""
        var metadata = """{"issuer":"$ISSUER","authorization_endpoint":"$ISSUER/authorize","token_endpoint":"$ISSUER/token","registration_endpoint":"$ISSUER/register","code_challenge_methods_supported":["S256"],"authorization_response_iss_parameter_supported":true,"grant_types_supported":["authorization_code","refresh_token"]}"""
        var callback: (Url) -> String = { url -> "$redirectUri?code=one-use&state=${url.parameters["state"]}&iss=https%3A%2F%2Flogin.example%2Ftenant" }
        var redirectUri = REDIRECT
        var sessionOpens = 0
        var sessionCloses = 0
        var launched: Url? = null
        val forms = mutableListOf<String>()
        val engine = MockEngine(MockEngineConfig().apply {
            dispatcher = Dispatchers.Unconfined
            addHandler { request ->
                val url = request.url.toString()
                when (url) {
                    RESOURCE -> respond("", HttpStatusCode.Unauthorized, headersOf(HttpHeaders.WWWAuthenticate,
                        if (challengeIncludesMetadata) "Bearer resource_metadata=\"https://mcp.example/metadata\", scope=\"operate\"" else "Bearer scope=\"operate\""))
                    "https://mcp.example/metadata", "https://mcp.example/.well-known/oauth-protected-resource/mcp" -> respond(resourceMetadata)
                    "https://login.example/.well-known/oauth-authorization-server/tenant" -> respond(firstMetadataBody ?: metadata)
                    "https://login.example/.well-known/openid-configuration/tenant" -> respond(metadata)
                    "$ISSUER/register" -> {
                        registrations++
                        val body = (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
                        val grants = Json.parseToJsonElement(body).jsonObject["grant_types"]!!.jsonArray
                        assertEquals(expectedGrants, grants.map { it.toString().trim('"') })
                        assertFalse(request.headers.contains(HttpHeaders.Authorization))
                        val allowedGrants = Json.encodeToString(registeredGrants ?: expectedGrants)
                        respond(registrationBody ?: """{"client_id":"public-client","token_endpoint_auth_method":"none","redirect_uris":["$redirectUri"],"grant_types":$allowedGrants}""", registrationStatus)
                    }
                    "$ISSUER/token" -> {
                        tokenRequests++
                        tokenStarted?.complete(Unit)
                        delay(tokenDelayMillis)
                        if (tokenBody != null) return@addHandler respond(tokenBody!!, tokenStatus)
                        val form = (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
                        forms += form
                        val fields = parseQueryString(form)
                        assertEquals(RESOURCE, fields["resource"])
                        assertEquals("public-client", fields["client_id"])
                        assertFalse(request.headers.contains(HttpHeaders.Authorization))
                        if (fields["grant_type"] == "refresh_token") {
                            refreshes++
                            assertEquals("refresh-1", fields["refresh_token"])
                            respond("""{"access_token":"access-2","token_type":"Bearer","refresh_token":"refresh-2","expires_in":3600}""", refreshStatus)
                        } else {
                            assertEquals("one-use", fields["code"])
                            assertEquals(redirectUri, fields["redirect_uri"])
                            assertEquals("x".repeat(64), fields["code_verifier"])
                            val refresh = if (includeRefresh) ",\"refresh_token\":\"refresh-1\"" else ""
                            respond("""{"access_token":"access-1","token_type":"Bearer","expires_in":$expiresIn$refresh}""")
                        }
                    }
                    else -> error("Unexpected request: $url")
                }
            }
        })
        val client = HttpClient(engine)
        val oauth = McpOAuth(client, store, McpOAuthBrowser {
            sessionOpens++
            McpOAuthBrowserSession(redirectUri, launch = { url ->
                delay(browserDelayMillis)
                callbackFailure?.let { throw it }
                val parsed = Url(url)
                launched = parsed
                assertEquals(redirectUri, parsed.parameters["redirect_uri"])
                callback(parsed)
            }, release = { sessionCloses++ })
        }, object : McpOAuthCrypto {
            override fun randomString(): String = if (randomValues++ % 2 == 0) "s".repeat(64) else "x".repeat(64)
            override fun challenge(verifier: String): String = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()))
        }, object : Clock { override fun now(): Instant = Instant.fromEpochSeconds(now) })
    }

    @Test fun oneManagerUsesAndClosesDifferentRedirectSessionsPerAttempt() = runTest {
        val f = Fixture()
        for ((index, redirect) in listOf("http://127.0.0.1:41001/oauth/mcp", "http://127.0.0.1:41002/oauth/mcp").withIndex()) {
            f.redirectUri = redirect
            f.oauth.authorize("id-$index", RESOURCE)
            assertTrue(f.oauth.isConnected("id-$index"))
            assertEquals(redirect, parseQueryString(f.forms.last())["redirect_uri"])
        }
        assertEquals(2, f.sessionOpens)
        assertEquals(2, f.sessionCloses)
    }

    @Test fun invalidRedirectFailsBeforeRegistrationAndReleasesItsSession() = runTest {
        for (redirect in listOf("pebble://oauth/mcp", "http://evil.example/callback", "https://user:secret@app.example/callback")) {
            val f = Fixture()
            f.redirectUri = redirect
            assertFailsWith<McpOAuthException> { f.oauth.authorize("a", RESOURCE) }
            assertEquals(1, f.sessionCloses)
            assertEquals(0, f.registrations)
            assertEquals(0, f.tokenRequests)
            assertEquals(null, f.launched)
        }
    }

    @Test fun authorizationBindsResourceScopeRedirectAndPkce() = runTest {
        val f = Fixture()
        f.oauth.authorize("a", RESOURCE)
        val url = assertNotNull(f.launched)
        assertEquals("S256", url.parameters["code_challenge_method"])
        assertEquals("operate", url.parameters["scope"])
        assertEquals(RESOURCE, url.parameters["resource"])
        assertEquals(REDIRECT, url.parameters["redirect_uri"])
        assertFalse(url.parameters["code_challenge"]!!.contains('='))
        assertFalse(url.parameters["state"] == parseQueryString(f.forms.single())["code_verifier"])
        assertEquals("access-1", f.oauth.accessToken("a", RESOURCE))
        assertEquals(1, f.registrations)
        assertTrue(f.oauth.isConnected("a"))
    }

    @Test fun htmlAuthorizationMetadataCandidateFallsBack() = runTest {
        val f = Fixture()
        f.firstMetadataBody = "<html>application fallback</html>"
        f.oauth.authorize("a", RESOURCE)
        assertEquals("access-1", f.oauth.accessToken("a", RESOURCE))
    }

    @Test fun tokenErrorsDistinguishInvalidGrantFromClientDefectsWithoutEchoingDescriptions() = runTest {
        for (code in listOf("invalid_grant", "invalid_request")) {
            val f = Fixture()
            f.tokenStatus = HttpStatusCode.BadRequest
            f.tokenBody = "{\"error\":\"$code\",\"error_description\":\"secret-do-not-echo\"}"
            val error = assertFailsWith<Exception> { f.oauth.authorize("a", RESOURCE) }
            if (code == "invalid_grant") assertTrue(error is McpSignInRequired)
            else assertTrue(error is McpOAuthException)
            assertFalse(error.toString().contains("secret-do-not-echo"))
        }
    }

    @Test fun issuerResponsePolicyRejectsUnsupportedServersBeforeRegistrationOrBrowser() = runTest {
        for (replacement in listOf("false", "null")) {
            val f = Fixture()
            f.metadata = if (replacement == "null") f.metadata.replace(",\"authorization_response_iss_parameter_supported\":true", "")
                else f.metadata.replace("\"authorization_response_iss_parameter_supported\":true", "\"authorization_response_iss_parameter_supported\":false")
            val error = assertFailsWith<McpOAuthException> { f.oauth.authorize("a", RESOURCE) }
            assertTrue(error.message!!.contains("RFC 9207"))
            assertEquals(0, f.registrations)
            assertEquals(null, f.launched)
            assertEquals(0, f.tokenRequests)
        }
    }

    @Test fun successfulAuthorizationIsDurableWhenCallerCancelsDuringStorage() = runTest {
        val f = Fixture()
        lateinit var authorization: Job
        f.store.beforeWrite = { authorization.cancel(); yield() }
        authorization = launch(start = CoroutineStart.LAZY) { f.oauth.authorize("a", RESOURCE) }
        authorization.start()
        authorization.join()
        assertTrue(f.oauth.isConnected("a"))
        assertEquals(1, f.tokenRequests)
    }

    @Test fun preregistrationSkipsDcr() = runTest {
        val f = Fixture()
        f.oauth.authorize("a", RESOURCE, "public-client")
        assertEquals(0, f.registrations)
    }

    @Test fun callbackMismatchNeverExchangesCode() = runTest {
        val callbacks = listOf<(Url) -> String>(
            { "$REDIRECT?state=wrong&code=one-use" },
            { "$REDIRECT?state=${it.parameters["state"]}&code=one-use&iss=https://evil.example" },
            { "$REDIRECT?state=${it.parameters["state"]}&code=one-use" },
            { "pebble://oauth/other?state=${it.parameters["state"]}&code=one-use" },
            { "$REDIRECT?state=${it.parameters["state"]}&state=duplicate&code=one-use" },
            { "$REDIRECT?state=${it.parameters["state"]}&iss=$ISSUER&error=access_denied" },
        )
        for (callback in callbacks) {
            val f = Fixture()
            f.callback = callback
            assertFailsWith<McpOAuthException> { f.oauth.authorize("a", RESOURCE) }
            assertEquals(0, f.tokenRequests)
            assertFalse(f.oauth.isConnected("a"))
        }
    }

    @Test fun mixedAuthorizationEndpointsWithoutIssuerSupportAreRejectedBeforeBrowser() = runTest {
        val f = Fixture()
        f.metadata = f.metadata
            .replace("$ISSUER/authorize", "https://honest.example/authorize")
            .replace("\"authorization_response_iss_parameter_supported\":true", "\"authorization_response_iss_parameter_supported\":false")
        f.callback = { "$REDIRECT?code=honest-code&state=${it.parameters["state"]}" }
        assertFailsWith<McpOAuthException> { f.oauth.authorize("a", RESOURCE) }
        assertEquals(null, f.launched)
        assertEquals(0, f.registrations)
        assertEquals(0, f.tokenRequests)
        assertFalse(f.oauth.isConnected("a"))
    }

    @Test fun discoveryRejectsResourceAndIssuerMixupsAndPkceDowngrade() = runTest {
        for (mismatch in listOf("resource", "issuer", "pkce", "http")) {
            val f = Fixture()
            when (mismatch) {
                "resource" -> f.resourceMetadata = f.resourceMetadata.replace(RESOURCE, "https://evil.example/mcp")
                "issuer" -> f.metadata = f.metadata.replace("\"issuer\":\"$ISSUER\"", "\"issuer\":\"https://evil.example\"")
                "pkce" -> f.metadata = f.metadata.replace("S256", "plain")
                "http" -> f.metadata = f.metadata.replace("$ISSUER/token", "http://login.example/token")
            }
            assertFailsWith<McpOAuthException> { f.oauth.authorize("a", RESOURCE) }
            assertEquals(null, f.launched)
            assertEquals(0, f.tokenRequests)
        }
    }

    @Test fun concurrentRefreshRotatesOnceAndPersistsBeforeReturning() = runTest {
        val f = Fixture()
        f.oauth.authorize("a", RESOURCE)
        f.now += 50
        val tokens = List(10) { async { f.oauth.accessToken("a", RESOURCE) } }.awaitAll()
        assertTrue(tokens.all { it == "access-2" })
        assertEquals(1, f.refreshes)
        assertTrue(f.store.values["a"]!!.contains("refresh-2"))
    }

    @Test fun successfulRefreshIsPersistedWhenTheCallerCancelsDuringTheWrite() = runTest {
        val f = Fixture()
        f.oauth.authorize("a", RESOURCE)
        f.now += 50
        var caller: Job? = null
        f.store.beforeWrite = { value ->
            if (value.contains("access-2")) {
                caller!!.cancel()
                yield()
            }
        }
        caller = launch(start = CoroutineStart.LAZY) { f.oauth.accessToken("a", RESOURCE) }
        caller.start()
        caller.join()
        assertTrue(f.store.values["a"]!!.contains("refresh-2"))
        assertEquals(1, f.refreshes)
    }

    @Test fun failedRefreshCannotReplayRotatingCredential() = runTest {
        val f = Fixture()
        f.oauth.authorize("a", RESOURCE)
        f.now += 50
        f.refreshStatus = HttpStatusCode.InternalServerError
        assertFailsWith<McpOAuthException> { f.oauth.accessToken("a", RESOURCE) }
        assertFailsWith<McpSignInRequired> { f.oauth.accessToken("a", RESOURCE) }
        assertEquals(1, f.refreshes)
    }

    @Test fun shortLivedTokenCanBeUsedBeforeItsRefreshWindow() = runTest {
        val f = Fixture()
        f.expiresIn = 10
        f.oauth.authorize("a", RESOURCE)
        assertEquals("access-1", f.oauth.accessToken("a", RESOURCE))
        assertEquals(0, f.refreshes)
        f.now += 6
        assertEquals("access-2", f.oauth.accessToken("a", RESOURCE))
    }

    @Test fun noRefreshTokenRequiresForegroundSignIn() = runTest {
        val f = Fixture()
        f.includeRefresh = false
        f.oauth.authorize("a", RESOURCE)
        f.now += 50
        assertFailsWith<McpSignInRequired> { f.oauth.accessToken("a", RESOURCE) }
        assertEquals(0, f.refreshes)
    }

    @Test fun isolationLogoutAndRejectionDoNotAffectOtherCredentials() = runTest {
        val f = Fixture()
        f.oauth.authorize("a", RESOURCE)
        f.oauth.authorize("b", RESOURCE)
        assertFailsWith<McpSignInRequired> { f.oauth.accessToken("a", "https://other.example/mcp") }
        f.oauth.rejected("a", "stale-token")
        assertEquals("access-1", f.oauth.accessToken("a", RESOURCE))
        f.oauth.disconnect("a")
        assertFailsWith<McpSignInRequired> { f.oauth.accessToken("a", RESOURCE) }
        assertEquals("access-1", f.oauth.accessToken("b", RESOURCE))
    }

    @Test fun pluginRejectsUnauthorizedWithoutReplayAndChecksInitialDestination() = runTest {
        val f = Fixture()
        f.oauth.authorize("a", RESOURCE)
        var calls = 0
        val engine = MockEngine { request ->
            calls++
            assertEquals("Bearer access-1", request.headers[HttpHeaders.Authorization])
            respond("", HttpStatusCode.Unauthorized)
        }
        val client = HttpClient(engine) {
            followRedirects = false
            install(McpOAuthAccess("a", RESOURCE, f.oauth).plugin())
        }
        assertFailsWith<McpAuthorizationRejected> { client.get(RESOURCE) }
        assertEquals(1, calls)
        assertFailsWith<McpOAuthException> { client.get("https://evil.example/mcp") }
        assertEquals(1, calls)
        client.close()
    }


    @Test fun malformedTokensAndCorruptStorageDoNotExposeSecretsInErrors() = runTest {
        val f = Fixture()
        f.tokenBody = "{invalid-secret-token-value"
        val error = assertFailsWith<McpOAuthException> { f.oauth.authorize("a", RESOURCE) }
        assertFalse(error.toString().contains("invalid-secret-token-value"))
        f.store.values["a"] = "{invalid-stored-secret"
        val stored = assertFailsWith<McpSignInRequired> { f.oauth.accessToken("a", RESOURCE) }
        assertFalse(stored.toString().contains("invalid-stored-secret"))
        assertFalse(f.store.values.containsKey("a"))
    }

    @Test fun cancelledBrowserNeverExchangesOrStoresTokens() = runTest {
        val f = Fixture()
        f.callbackFailure = kotlinx.coroutines.CancellationException("cancelled")
        assertFailsWith<kotlinx.coroutines.CancellationException> { f.oauth.authorize("a", RESOURCE) }
        assertEquals(0, f.tokenRequests)
        assertFalse(f.oauth.isConnected("a"))
    }

    @Test fun unreadableSavedCredentialIsDisconnected() = runTest {
        val f = Fixture()
        f.store.values["a"] = "{unreadable"
        assertFalse(f.oauth.isConnected("a"))
        assertFalse(f.store.values.containsKey("a"))
    }

    @Test fun browserTimeoutShowsAnActionableErrorWithoutSavingCredentials() = runTest {
        val f = Fixture()
        f.browserDelayMillis = 300_001
        val error = assertFailsWith<McpOAuthException> { f.oauth.authorize("a", RESOURCE) }
        assertTrue(error.message!!.contains("timed out"))
        assertEquals(0, f.tokenRequests)
        assertFalse(f.oauth.isConnected("a"))
    }

    @Test fun disabledRedirectsReturnWithoutFollowing() = runTest {
        val f = Fixture()
        f.oauth.authorize("a", RESOURCE)
        var calls = 0
        val engine = MockEngine {
            calls++
            respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "https://other.example/mcp"))
        }
        val client = HttpClient(engine) {
            followRedirects = false
            install(McpOAuthAccess("a", RESOURCE, f.oauth).plugin())
        }
        assertEquals(HttpStatusCode.Found, client.get(RESOURCE).status)
        assertEquals(1, calls)
        client.close()
    }

    @Test fun malformedCallbackUrlsDoNotExposeAuthorizationValues() {
        val server = ServerMetadata(ISSUER, "$ISSUER/authorize", "$ISSUER/token")
        val callback = "https://app.example:invalid/oauth/mcp?code=callback-secret&state=state-secret"
        val error = assertFailsWith<McpOAuthException> { validateCallback(callback, REDIRECT, "state-secret", server) }
        assertFalse(error.stackTraceToString().contains("callback-secret"))
        assertFalse(error.stackTraceToString().contains("state-secret"))
        val resource = assertFailsWith<McpOAuthException> { secureUrl("https://mcp.example:invalid/?secret=resource-secret") }
        assertFalse(resource.stackTraceToString().contains("resource-secret"))
    }

    @Test fun stalledRefreshTimesOutAndReleasesOtherCredentialOperations() = runTest {
        val f = Fixture()
        f.oauth.authorize("a", RESOURCE)
        f.oauth.authorize("b", RESOURCE)
        f.now += 50
        f.tokenDelayMillis = 30_001
        val error = assertFailsWith<McpOAuthException> { f.oauth.accessToken("a", RESOURCE) }
        assertEquals("OAuth request timed out.", error.message)
        assertTrue(f.oauth.isConnected("b"))
        assertFailsWith<McpSignInRequired> { f.oauth.accessToken("a", RESOURCE) }
        assertEquals(3, f.tokenRequests)
    }

    @Test fun callerCancellationDuringRefreshDoesNotReuseItsCredential() = runTest {
        val f = Fixture()
        f.oauth.authorize("a", RESOURCE)
        f.now += 50
        val failure = CancellationException("caller cancelled")
        f.tokenDelayMillis = 30_001
        f.tokenStarted = CompletableDeferred()
        val request = launch { f.oauth.accessToken("a", RESOURCE) }
        f.tokenStarted!!.await()
        request.cancel(failure)
        request.join()
        assertTrue(request.isCancelled)
        assertFailsWith<McpSignInRequired> { f.oauth.accessToken("a", RESOURCE) }
        assertEquals(2, f.tokenRequests)
    }

    @Test fun refreshTokensAreNotUsedUnlessDynamicRegistrationAllowsRefresh() = runTest {
        for (advertisesRefresh in listOf(false, true)) {
            val f = Fixture()
            if (!advertisesRefresh) {
                f.metadata = f.metadata.replace(",\"grant_types_supported\":[\"authorization_code\",\"refresh_token\"]", "")
                f.expectedGrants = listOf("authorization_code")
            } else {
                f.registeredGrants = listOf("authorization_code")
            }
            f.oauth.authorize("a", RESOURCE)
            f.now += 50
            assertFailsWith<McpSignInRequired> { f.oauth.accessToken("a", RESOURCE) }
            assertEquals(1, f.tokenRequests)
        }
    }

    @Test fun rejectedCurrentTokenRefreshesOnlyOnTheNextExplicitRequest() = runTest {
        val f = Fixture()
        f.oauth.authorize("a", RESOURCE)
        var calls = 0
        val client = HttpClient(MockEngine { calls++; respond("", HttpStatusCode.Unauthorized) }) {
            followRedirects = false
            install(McpOAuthAccess("a", RESOURCE, f.oauth).plugin())
        }
        try {
            assertFailsWith<McpAuthorizationRejected> { client.get(RESOURCE) }
            assertEquals(1, calls)
            assertEquals(0, f.refreshes)
            assertEquals("access-2", f.oauth.accessToken("a", RESOURCE))
            assertEquals(1, f.refreshes)
        } finally { client.close() }
    }

    @Test fun forbiddenDoesNotRefreshOrReplay() = runTest {
        val f = Fixture()
        f.oauth.authorize("a", RESOURCE)
        var calls = 0
        val client = HttpClient(MockEngine { calls++; respond("", HttpStatusCode.Forbidden) }) {
            install(McpOAuthAccess("a", RESOURCE, f.oauth).plugin())
        }
        try {
            val error = assertFailsWith<McpOAuthException> { client.get(RESOURCE) }
            assertTrue(error.message!!.contains("Reconnect"))
            assertEquals(1, calls)
            assertEquals("access-1", f.oauth.accessToken("a", RESOURCE))
            assertEquals(0, f.refreshes)
        } finally { client.close() }
    }

    @Test fun invalidTokenShapeTypeAndLifetimeAreRejectedBeforePersistence() = runTest {
        for (body in listOf(
            """{"access_token":"header\r\ninjection","token_type":"Bearer"}""",
            """{"access_token":"token","token_type":"Other"}""",
            """{"access_token":"token","token_type":"Bearer","expires_in":0}""",
            """{"access_token":"token","token_type":"Bearer","expires_in":315360001}""",
        )) {
            val f = Fixture()
            f.tokenBody = body
            assertFailsWith<McpOAuthException> { f.oauth.authorize("a", RESOURCE) }
            assertFalse(f.oauth.isConnected("a"))
        }
    }

    @Test fun invalidPublicRegistrationFailsBeforeBrowserOrCodeExchange() = runTest {
        for ((status, body) in listOf(
            HttpStatusCode.BadRequest to "{}",
            HttpStatusCode.Created to """{"client_id":"c","token_endpoint_auth_method":"none","redirect_uris":["$REDIRECT"],"client_secret":"secret"}""",
            HttpStatusCode.Created to """{"client_id":"c","token_endpoint_auth_method":"none","redirect_uris":["https://other.example/callback"]}""",
            HttpStatusCode.Created to """{"client_id":"c","token_endpoint_auth_method":"none","redirect_uris":["$REDIRECT"],"grant_types":["refresh_token"]}""",
        )) {
            val f = Fixture()
            f.registrationStatus = status
            f.registrationBody = body
            assertFailsWith<McpOAuthException> { f.oauth.authorize("a", RESOURCE) }
            assertEquals(null, f.launched)
            assertEquals(0, f.tokenRequests)
            assertEquals(1, f.sessionCloses)
        }
        val f = Fixture()
        f.metadata = f.metadata.replace(",\"registration_endpoint\":\"$ISSUER/register\"", "")
        assertFailsWith<McpOAuthException> { f.oauth.authorize("a", RESOURCE) }
        assertEquals(0, f.registrations)
        assertEquals(0, f.tokenRequests)
    }

    @Test fun protectedResourceWellKnownDiscoveryWorksWithoutAChallengeMetadataUrl() = runTest {
        val f = Fixture()
        f.challengeIncludesMetadata = false
        f.oauth.authorize("a", RESOURCE)
        assertEquals("access-1", f.oauth.accessToken("a", RESOURCE))
    }

    @Test fun discoveryPreservesIssuerPathsAndChallengeQuotedCommas() {
        assertEquals(listOf("https://mcp.example/.well-known/oauth-protected-resource/mcp", "https://mcp.example/.well-known/oauth-protected-resource"), resourceMetadataUrls(RESOURCE))
        assertEquals("https://login.example/.well-known/oauth-authorization-server/tenant", issuerMetadataUrls(ISSUER).first())
        assertTrue(bearerChallenge(listOf("Basic realm=\"some, Bearer other\"")).isEmpty())
        assertEquals("read", bearerChallenge(listOf("Bearer scope=\"read\", Basic realm=\"other\""))["scope"])
        assertEquals("read", bearerChallenge(listOf("Bearer realm = \"example\", scope = \"read\""))["scope"])
        assertEquals("read,write", bearerChallenge(listOf("Basic realm=\"a\", Bearer scope=\"read,write\", resource_metadata=\"https://example/metadata\""))["scope"])
        assertFailsWith<McpOAuthException> { bearerChallenge(listOf("Bearer scope=\"a\", scope=\"b\"")) }
        for (url in listOf("http://example.com", "https://user:secret@example.com", "https://example.com/#fragment")) {
            assertFailsWith<McpOAuthException> { secureUrl(url) }
        }
    }
}
