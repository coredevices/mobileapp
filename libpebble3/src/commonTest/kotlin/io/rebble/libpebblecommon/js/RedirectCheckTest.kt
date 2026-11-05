package io.rebble.libpebblecommon.js

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.rebble.libpebblecommon.plugin.PluginNetworkVerdict
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class RedirectCheckTest {
    private val requested = mutableListOf<String>()
    private val authorization = mutableListOf<String?>()

    private fun client(redirects: Map<String, String>) = HttpClient(MockEngine { request ->
        val url = request.url.toString()
        requested += url
        authorization += request.headers[HttpHeaders.Authorization]
        val location = redirects[url]
        if (location != null) {
            respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, location))
        } else {
            respond("ok", HttpStatusCode.OK)
        }
    }) { followRedirects = false }

    private fun allowOnly(vararg hosts: String): suspend (String, Map<String, Any>) -> PluginNetworkVerdict = { url, _ ->
        if (hosts.any { url.substringAfter("://").startsWith("$it/") }) PluginNetworkVerdict.Allowed
        else PluginNetworkVerdict.Denied("not allowed: $url")
    }

    @Test
    fun redirectToADeniedHostIsNeverRequested() = runTest {
        val client = client(mapOf("http://allowed.com/r" to "http://192.168.1.1/admin"))
        assertFailsWith<RedirectDeniedException> {
            client.requestCheckingRedirects("http://allowed.com/r", HttpMethod.Get, emptyMap(), allowOnly("allowed.com")) {}
        }
        assertEquals(listOf("http://allowed.com/r"), requested)
    }

    @Test
    fun httpsIsNotDowngradedToHttp() = runTest {
        val client = client(mapOf("https://a.com/r" to "http://a.com/plain"))
        val response = client.requestCheckingRedirects("https://a.com/r", HttpMethod.Get, emptyMap(), allowOnly("a.com")) {}
        assertEquals(HttpStatusCode.Found, response.status)
        assertEquals(listOf("https://a.com/r"), requested)
    }

    @Test
    fun redirectToAnAllowedHostIsFollowedWithoutAuthorization() = runTest {
        val client = client(mapOf("https://a.com/r" to "https://b.com/next"))
        val response = client.requestCheckingRedirects("https://a.com/r", HttpMethod.Get, emptyMap(), allowOnly("a.com", "b.com")) {
            header(HttpHeaders.Authorization, "Bearer secret")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(listOf("https://a.com/r", "https://b.com/next"), requested)
        assertEquals(listOf("Bearer secret", null), authorization)
    }

    @Test
    fun relativeRedirectStaysOnTheSameHost() = runTest {
        val client = client(mapOf("https://a.com/r?x=1" to "/next"))
        client.requestCheckingRedirects("https://a.com/r?x=1", HttpMethod.Get, emptyMap(), allowOnly("a.com")) {}
        assertEquals(listOf("https://a.com/r?x=1", "https://a.com/next"), requested)
    }

    @Test
    fun postIsNotRedirected() = runTest {
        val client = client(mapOf("https://a.com/r" to "https://evil.com/"))
        val response = client.requestCheckingRedirects("https://a.com/r", HttpMethod.Post, emptyMap(), allowOnly("a.com")) {
            method = HttpMethod.Post
        }
        assertEquals(HttpStatusCode.Found, response.status)
        assertEquals(listOf("https://a.com/r"), requested)
        assertNull(authorization.single())
    }
}
