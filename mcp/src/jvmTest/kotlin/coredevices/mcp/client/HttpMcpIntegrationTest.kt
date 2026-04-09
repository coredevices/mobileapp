package coredevices.mcp.client

import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpError
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.netty.util.internal.logging.Slf4JLoggerFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.IOException
import kotlin.test.Ignore
import kotlin.test.Test

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
            server.stop(100, 300)
        }
    }
}