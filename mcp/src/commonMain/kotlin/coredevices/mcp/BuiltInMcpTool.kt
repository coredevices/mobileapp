package coredevices.mcp

import io.modelcontextprotocol.kotlin.sdk.types.Tool
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.json.internal.FormatLanguage

abstract class BuiltInMcpTool @OptIn(InternalSerializationApi::class) constructor(
    override val definition: Tool,
    private val extraContext: String? = null,
): McpTool {
    override suspend fun getExtraContext(): String? = extraContext
}