package coredevices.mcp.client

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

class McpServerCacheTest {

    private val endpoint = McpEndpoint("https://mcp.example.com/sse", HttpMcpProtocol.Sse, "Bearer secret-token")

    @Test
    fun theSameEndpointSharesOneEntry() = runBlocking {
        val cache = McpServerCache()
        assertSame(cache.entryFor(endpoint), cache.entryFor(endpoint.copy()))
    }

    @Test
    fun aDifferentAuthHeaderGetsItsOwnEntry() = runBlocking {
        val cache = McpServerCache()
        assertNotSame(cache.entryFor(endpoint), cache.entryFor(endpoint.copy(authHeader = "Bearer other-token")))
    }

    @Test
    fun cacheKeysAreDigestsThatRevealNothingAboutTheEndpoint() = runBlocking {
        val key = endpoint.cacheKey()
        assertFalse(key.contains("secret-token"))
        assertFalse(key.contains("example.com"))
        assertEquals(key, endpoint.copy().cacheKey())
        assertNotEquals(key, endpoint.copy(protocol = HttpMcpProtocol.Streaming).cacheKey())
        assertNotEquals(key, endpoint.copy(authHeader = null).cacheKey())
    }

    @Test
    fun invalidateAllDropsToolsAndPrompts() = runBlocking {
        val entry = McpServerCacheEntry(Clock.System)
        var fetches = 0
        suspend fun readAll() {
            entry.tools.get(1.hours) { fetches++; emptyList() }
            entry.prompts.get(1.hours) { fetches++; emptyList() }
        }
        readAll()
        entry.invalidateAll()
        readAll()
        assertEquals(4, fetches)
    }

    @Test
    fun promptContentsGoStaleWithThePromptList() = runBlocking {
        val entry = McpServerCacheEntry(Clock.System)
        var fetches = 0
        suspend fun readAll() {
            entry.prompts.get(1.hours) { fetches++; emptyList() }
            entry.promptContent("greeting").get(1.hours) { fetches++; "hello" }
        }
        readAll()
        readAll()
        assertEquals(2, fetches)
        entry.invalidatePrompts()
        readAll()
        assertEquals(4, fetches)
    }
}
