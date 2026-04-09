package coredevices.indexai.data.entity

import kotlinx.serialization.Serializable

@Serializable
data class ConversationToolCallResponse(
    val id: String,
    val name: String,
    val success: Boolean?,
    val data: String? = null
)