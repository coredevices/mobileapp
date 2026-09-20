package coredevices.mcp.client

import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpError
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.io.IOException
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds

class HttpMcpIntegrationTest {
    val initialPort = 8080
    val impl = Implementation(
        name = "TestClient",
        version = "1.0.0"
    )

    private fun buildUrl(port: Int, sse: Boolean): String {
        return "http://127.0.0.1:${port}/${if (sse) "sse" else ""}"
    }

    @Ignore
    @Test
    fun basicClientConnectionTest() {
        var port = initialPort
        val server = try {
            runSseMcpServer(port = port, wait = false)
        } catch (e: IOException) {
            // Try next port
            port += 1
            runSseMcpServer(port = port, wait = false)
        }

        try {
            val integration = HttpMcpIntegration(
                "test",
                impl,
                buildUrl(port, true),
                HttpMcpProtocol.Sse
            )
            val tools = try {
                runBlocking(Dispatchers.IO) {
                    integration.listTools()
                }
            } catch (e: StreamableHttpError) {
                throw IOException("Request failed, code = ${e.code}", e)
            }
            assert(tools.isNotEmpty())
        } finally {
            server.stop()
        }
    }

    // The test server advertises only the tools capability, so it stands in for any MCP
    // server that doesn't support prompts: listPrompts must return empty, not throw.
    @Ignore
    @Test
    fun listPromptsEmptyWhenServerLacksPromptsCapability() {
        var port = initialPort
        val server = try {
            runSseMcpServer(port = port, wait = false)
        } catch (e: IOException) {
            port += 1
            runSseMcpServer(port = port, wait = false)
        }

        try {
            val integration = HttpMcpIntegration(
                "test",
                impl,
                buildUrl(port, true),
                HttpMcpProtocol.Sse
            )
            val prompts = runBlocking(Dispatchers.IO) {
                integration.connect()
                integration.listPrompts()
            }
            assert(prompts.isEmpty())
        } finally {
            server.stop()
        }
    }

    // Also proves the transport delivers server-initiated notifications at all: without that the
    // cache would only ever refresh on its age ceiling.

    @Ignore
    @Test
    fun toolListChangedNotificationRefreshesCachedTools() {
        var port = initialPort
        val server = try {
            runSseMcpServer(port = port, wait = false)
        } catch (e: IOException) {
            port += 1
            runSseMcpServer(port = port, wait = false)
        }

        try {
            val integration = HttpMcpIntegration(
                "test",
                impl,
                buildUrl(port, true),
                HttpMcpProtocol.Sse
            )
            runBlocking(Dispatchers.IO) {
                integration.connect()
                val before = integration.listTools().size
                server.mcp.addTool(name = "added-later", description = "Appears after a list_changed") { _ ->
                    CallToolResult(content = listOf(TextContent("later")))
                }
                assertEquals(before, integration.listTools().size)

                server.notifyToolListChanged()
                delay(500.milliseconds)
                assertEquals(before + 1, integration.listTools().size)
                integration.close()
            }
        } finally {
            server.stop()
        }
    }
}
