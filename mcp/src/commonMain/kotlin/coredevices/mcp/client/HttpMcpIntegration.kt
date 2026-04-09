package coredevices.mcp.client

import co.touchlab.kermit.Logger
import coredevices.mcp.McpTool
import coredevices.mcp.data.McpPrompt
import coredevices.mcp.data.SemanticResult
import coredevices.mcp.data.ToolCallResult
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.sse.SSE
import io.ktor.client.request.header
import io.ktor.serialization.kotlinx.json.json
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.SseClientTransport
import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpClientTransport
import io.modelcontextprotocol.kotlin.sdk.types.GetPromptRequest
import io.modelcontextprotocol.kotlin.sdk.types.GetPromptRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.decrementAndFetch
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

enum class HttpMcpProtocol {
    Streaming,
    Sse
}

class HttpMcpIntegration(
    override val name: String,
    implementation: Implementation,
    private val url: String,
    protocol: HttpMcpProtocol = HttpMcpProtocol.Sse,
    authHeader: String? = null
): McpIntegration, PromptProvider {
    companion object {
        private val logger = Logger.withTag("HttpMcpIntegration")
    }
    private val client = Client(implementation)
    private val transport = when (protocol) {
        HttpMcpProtocol.Streaming -> StreamableHttpClientTransport(
            HttpClient {
                install(SSE)
                install(ContentNegotiation) {
                    json()
                }
                if (authHeader != null) {
                    defaultRequest {
                        header("Authorization", authHeader)
                    }
                }
            },
            url
        )
        HttpMcpProtocol.Sse -> SseClientTransport(
            HttpClient {
                install(SSE)
                install(ContentNegotiation) {
                    json()
                }
                if (authHeader != null) {
                    defaultRequest {
                        header("Authorization", authHeader)
                    }
                }
            },
            url
        )
    }
    private var toolCache: List<McpTool>? = null
    private var toolCacheTimestamp: Instant = Instant.DISTANT_PAST
    private val cacheDuration: Duration = 30.seconds
    private var connectionOpened = false
    @OptIn(ExperimentalAtomicApi::class)
    private var connectionLocks = AtomicInt(0)
    val title get() = client.serverVersion?.title ?: client.serverVersion?.name

    @OptIn(ExperimentalAtomicApi::class)
    override suspend fun connect() {
        client.connect(transport)
        connectionOpened = true
        connectionLocks.incrementAndFetch()
        logger.d { "Connected to MCP server: ${client.serverVersion}" }
    }

    override suspend fun resetCache() {
        toolCache = null
    }

    override suspend fun listTools(): List<McpTool> {
        val now = Clock.System.now()
        if (toolCache == null || now - toolCacheTimestamp > cacheDuration) {
            val result = client.listTools()
            if (result.nextCursor != null) {
                TODO("Handle pagination" )
            }
            toolCache = result.tools.map { RemoteMcpTool(this, it) }
            toolCacheTimestamp = now
        }
        return toolCache!!
    }

    override suspend fun listPrompts(): List<McpPrompt> {
        return client.listPrompts().prompts.filter {
            it.arguments == null // We don't support prompts with arguments yet
        }.map {
            McpPrompt(
                name = it.name,
                title = it.title,
                description = it.description,
            )
        }
    }

    override suspend fun getPromptContent(promptName: String): String {
        val prompt = client.getPrompt(
            GetPromptRequest(
                GetPromptRequestParams(
                    name = promptName
                )
            )
        )
        return when (val content = prompt.messages.first().content) {
            is TextContent -> content.text
            else -> error("Unsupported prompt content type: ${content.type}")
        }
    }

    override suspend fun callTool(
        toolName: String,
        json: Map<String, JsonElement>
    ): ToolCallResult {
        val result = client.callTool(toolName, json)
        if (result.isError == true) {
            logger.w { "Tool call to $toolName reported error to LM" }
        }
        val isCoreSchema = result.meta?.containsKey("coreSchema") == true
        return when {
            isCoreSchema -> {
                val schemaVersion = result.meta?.get("coreSchema")?.jsonPrimitive?.intOrNull
                schemaVersion?.let {
                    if (it <= 1) {
                        val semanticResult = result.structuredContent!!.getValue("semanticResult")
                        val outputText = result.structuredContent!!.getValue("output")
                        ToolCallResult(
                            resultString = outputText.toString(),
                            semanticResult = Json.decodeFromJsonElement(semanticResult)
                        )
                    } else {
                        logger.w { "Unsupported coreSchema version: $schemaVersion" }
                        ToolCallResult(
                            result.structuredContent.toString(),
                            if (result.isError == true) {
                                SemanticResult.GenericFailure(null, true)
                            } else {
                                SemanticResult.GenericSuccess
                            }
                        )
                    }
                } ?: error("coreSchema meta field is not an integer")
            }
            result.structuredContent != null -> {
                ToolCallResult(
                    resultString = result.structuredContent.toString(),
                    semanticResult = if (result.isError == true) {
                        SemanticResult.GenericFailure(null, true)
                    } else {
                        SemanticResult.GenericSuccess
                    }
                )
            }
            else -> {
                return ToolCallResult(
                    resultString = result.content.joinToString("\n") {
                        if (it is TextContent) {
                            it.text
                        } else {
                            logger.w { "Unsupported content type: ${it.type}" }
                            ""
                        }
                    },
                    semanticResult = if (result.isError == true) {
                        SemanticResult.GenericFailure(null, true)
                    } else {
                        SemanticResult.GenericSuccess
                    }
                )
            }
        }
    }

    override suspend fun getExtraContext(): String? {
        return client.serverInstructions
    }

    override suspend fun getExtraContext(includePromptsFrom: Set<String>?): String? {
        val promptContext = includePromptsFrom?.mapNotNull { promptName ->
            try {
                getPromptContent(promptName)
            } catch (e: Exception) {
                logger.w(e) { "Failed to get prompt content for $promptName" }
                null
            }
        }?.joinToString("\n")
        val serverContext = client.serverInstructions
        return listOfNotNull(
            serverContext,
            promptContext?.takeIf { it.isNotEmpty() }
        ).joinToString("\n").takeIf { it.isNotEmpty() }
    }

    @OptIn(ExperimentalAtomicApi::class)
    override suspend fun close() {
        if (connectionLocks.decrementAndFetch() > 0) return
        client.close()
        connectionOpened = false
    }
}