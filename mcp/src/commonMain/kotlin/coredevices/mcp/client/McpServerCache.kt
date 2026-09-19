package coredevices.mcp.client

import coredevices.mcp.data.McpPrompt
import io.ktor.util.Digest
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.io.encoding.Base64
import kotlin.time.Clock

data class McpEndpoint(val url: String, val protocol: HttpMcpProtocol, val authHeader: String?)

class McpServerCacheEntry(private val clock: Clock) {
    private val promptsGeneration = CacheGeneration()
    private val promptContents = mutableMapOf<String, CachedValue<String>>()
    private val promptContentsLock = Mutex()

    val tools = CachedValue<List<Tool>>(clock)
    val prompts = CachedValue<List<McpPrompt>>(clock, promptsGeneration)

    suspend fun promptContent(name: String): CachedValue<String> = promptContentsLock.withLock {
        promptContents.getOrPut(name) { CachedValue(clock, promptsGeneration) }
    }

    fun invalidatePrompts() = promptsGeneration.bump()

    fun invalidateAll() {
        tools.invalidate()
        invalidatePrompts()
    }
}

/**
 * Process-wide memory of what each HTTP MCP server offers, shared by every integration that
 * connects to the same endpoint so concurrent sessions fetch each list once between them. MCP
 * gives no way to tell whether a list fetched over an earlier connection is still current, so
 * integrations invalidate their entry on every connect. Freshness is the reader's decision; see
 * [CachedValue].
 */
class McpServerCache(private val clock: Clock = Clock.System) {
    private val entries = mutableMapOf<String, McpServerCacheEntry>()
    private val lock = Mutex()

    suspend fun entryFor(endpoint: McpEndpoint): McpServerCacheEntry {
        val key = endpoint.cacheKey()
        return lock.withLock { entries.getOrPut(key) { McpServerCacheEntry(clock) } }
    }
}

/** Endpoints include auth headers, so only their digest is ever kept. */
internal suspend fun McpEndpoint.cacheKey(): String {
    val digest = Digest("SHA-256")
    digest += "$url\n$protocol\n${authHeader.orEmpty()}".encodeToByteArray()
    val bytes = digest.build()
    return Base64.UrlSafe.encode(bytes)
}
