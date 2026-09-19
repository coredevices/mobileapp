package coredevices.mcp.client

import co.touchlab.kermit.Logger
import coredevices.mcp.McpTool
import coredevices.mcp.SessionContext
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
import io.modelcontextprotocol.kotlin.sdk.types.Method
import io.modelcontextprotocol.kotlin.sdk.types.PromptListChangedNotification
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolListChangedNotification
import kotlinx.coroutines.CompletableDeferred
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.decrementAndFetch
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

enum class HttpMcpProtocol {
    Streaming,
    Sse
}

class HttpMcpIntegration(
    override val name: String,
    implementation: Implementation,
    private val url: String,
    protocol: HttpMcpProtocol = HttpMcpProtocol.Sse,
    authHeader: String? = null,
    private val cache: McpServerCache = McpServerCache(),
): McpIntegration, PromptProvider {
    companion object {
        private val logger = Logger.withTag("HttpMcpIntegration")

        val NOTIFIED_MAX_AGE: Duration = 1.hours
        val POLLED_MAX_AGE: Duration = 5.minutes
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
    private val endpoint = McpEndpoint(url, protocol, authHeader)
    private var entry: McpServerCacheEntry? = null
    private var connectionOpened = false
    @OptIn(ExperimentalAtomicApi::class)
    private var connectionLocks = AtomicInt(0)
    val title get() = client.serverVersion?.title ?: client.serverVersion?.name
    private val supportsPrompts get() = client.serverCapabilities?.prompts != null
    private val toolsMaxAge get() = maxAge(client.serverCapabilities?.tools?.listChanged)
    private val promptsMaxAge get() = maxAge(client.serverCapabilities?.prompts?.listChanged)

    @OptIn(ExperimentalAtomicApi::class)
    override suspend fun connect() {
        if (connectionLocks.incrementAndFetch() > 1) return
        try {
            registerListChangedHandlers(cacheEntry())
            client.connect(transport)
            cacheEntry().invalidateAll()
            connectionOpened = true
            logger.d { "Connected to MCP server: ${client.serverVersion}" }
        } catch (e: Throwable) {
            connectionLocks.decrementAndFetch()
            throw e
        }
    }

    private fun registerListChangedHandlers(entry: McpServerCacheEntry) {
        client.setNotificationHandler<ToolListChangedNotification>(Method.Defined.NotificationsToolsListChanged) {
            entry.tools.invalidate()
            CompletableDeferred(Unit)
        }
        client.setNotificationHandler<PromptListChangedNotification>(Method.Defined.NotificationsPromptsListChanged) {
            entry.invalidatePrompts()
            CompletableDeferred(Unit)
        }
    }

    private suspend fun cacheEntry(): McpServerCacheEntry =
        entry ?: cache.entryFor(endpoint).also { entry = it }

    private fun maxAge(serverNotifiesChanges: Boolean?): Duration =
        if (serverNotifiesChanges == true) NOTIFIED_MAX_AGE else POLLED_MAX_AGE

    override suspend fun listTools(): List<McpTool> {
        val tools = cacheEntry().tools.get(toolsMaxAge) {
            val result = client.listTools()
            if (result.nextCursor != null) {
                TODO("Handle pagination" )
            }
            result.tools
        }
        return tools.map { RemoteMcpTool(this, it) }
    }

    override suspend fun listPrompts(): List<McpPrompt> {
        if (!supportsPrompts) return emptyList()
        return cacheEntry().prompts.get(promptsMaxAge) {
            client.listPrompts().prompts.filter {
                it.arguments == null // We don't support prompts with arguments yet
            }.map {
                McpPrompt(
                    name = it.name,
                    title = it.title,
                    description = it.description,
                )
            }
        }
    }

    override suspend fun getPromptContent(promptName: String): String =
        cacheEntry().promptContent(promptName).get(promptsMaxAge) { fetchPromptContent(promptName) }

    private suspend fun fetchPromptContent(promptName: String): String {
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
        json: Map<String, JsonElement>,
        context: SessionContext
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

    override suspend fun getExtraContext(sessionContext: SessionContext?): String? {
        return client.serverInstructions
    }

    override suspend fun getExtraContext(sessionContext: SessionContext?, includePromptsFrom: Set<String>?): String? {
        val promptContext = includePromptsFrom?.takeIf { supportsPrompts }?.mapNotNull { promptName ->
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
