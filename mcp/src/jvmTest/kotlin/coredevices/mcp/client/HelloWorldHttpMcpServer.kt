package coredevices.mcp.client

import coredevices.mcp.data.SemanticResult
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.sse.SSE
import io.ktor.server.sse.sse
import io.ktor.util.collections.ConcurrentMap
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.ServerSession
import io.modelcontextprotocol.kotlin.sdk.server.SseServerTransport
import io.modelcontextprotocol.kotlin.sdk.server.mcp
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.coroutines.awaitCancellation
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject

private fun configureServer(): Server {
    val server = Server(
        Implementation(
            name = "mcp-kotlin test server",
            version = "0.1.0",
        ),
        ServerOptions(
            capabilities = ServerCapabilities(
                tools = ServerCapabilities.Tools(listChanged = true),
            ),
        ),
    )

    // Add a tool
    server.addTool(
        name = "kotlin-sdk-tool",
        description = "A test tool",
    ) { _ ->
        CallToolResult(
            content = listOf(TextContent("Hello, world!")),
        )
    }

    server.addTool(
        name = "core-schema-tool",
        description = "A test tool",
    ) { _ ->
        CallToolResult(
            content = listOf(TextContent("Hello, world!")),
            structuredContent = Json.encodeToJsonElement(
                mapOf(
                    "semanticResult" to (SemanticResult.SupportingData("some data") as SemanticResult)
                )
            ).jsonObject
        )
    }

    return server
}

fun runSseMcpServer(port: Int, wait: Boolean = true): EmbeddedServer<*, *> {
    val serverSessions = ConcurrentMap<String, ServerSession>()

    val server = configureServer()

    val ktorServer = embeddedServer(Netty, host = "127.0.0.1", port = port) {
        install(SSE)
        routing {
            sse("/sse") {
                val transport = SseServerTransport("/message", this)
                val serverSession = server.createSession(transport)
                serverSessions[transport.sessionId] = serverSession

                serverSession.onClose {
                    println("Server session closed for: ${transport.sessionId}")
                    serverSessions.remove(transport.sessionId)
                }
                awaitCancellation()
            }
            post("/message") {
                val sessionId: String? = call.request.queryParameters["sessionId"]
                if (sessionId == null) {
                    call.respond(HttpStatusCode.BadRequest, "Missing sessionId parameter")
                    return@post
                }

                val transport = serverSessions[sessionId]?.transport as? SseServerTransport
                if (transport == null) {
                    call.respond(HttpStatusCode.NotFound, "Session not found")
                    return@post
                }

                transport.handlePostMessage(call)
            }
        }
    }.start(wait = wait)

    return ktorServer
}