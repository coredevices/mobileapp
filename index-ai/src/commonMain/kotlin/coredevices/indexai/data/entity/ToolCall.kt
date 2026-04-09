package coredevices.indexai.data.entity

import kotlinx.serialization.Serializable

@Serializable
data class ToolCall(
    val id: String,
    val type: String,
    val function: FunctionToolCall? = null,
)

@Serializable
data class FunctionToolCall(
    val name: String,
    val arguments: String
)

@Serializable
data class ToolCallResponse(
    val tool_call_id: String,
    val content: String,
    val role: String = "tool"
)