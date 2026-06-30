package coredevices.mcp.client

import coredevices.mcp.BuiltInMcpTool
import coredevices.mcp.SessionContext
import coredevices.mcp.data.SemanticResult
import coredevices.mcp.data.ToolCallResult
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class SessionContextDispatchTest {
    private class CapturingTool : BuiltInMcpTool(
        definition = Tool(
            name = "capture",
            description = "Captures the session context",
            inputSchema = ToolSchema(
                properties = JsonObject(emptyMap()),
                required = emptyList()
            )
        )
    ) {
        var receivedContext: SessionContext? = null
        override suspend fun call(jsonInput: String, context: SessionContext): ToolCallResult {
            receivedContext = context
            return ToolCallResult("ok", SemanticResult.GenericSuccess)
        }
    }

    @Test
    fun sessionContextReachesBuiltInTool() = runBlocking {
        val tool = CapturingTool()
        val session = McpSession(
            integrations = listOf(BuiltInMcpIntegration("builtin", listOf(tool))),
            scope = CoroutineScope(Dispatchers.Default)
        )
        val context = SessionContext(
            timeBase = Instant.fromEpochMilliseconds(1234567890L),
            userMessageText = CompletableDeferred("Hello, world!"),
        )
        session.callTool("builtin", "capture", emptyMap(), context)
        assertEquals(context, tool.receivedContext)
    }
}
