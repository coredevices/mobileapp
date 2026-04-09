package coredevices.indexai.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
data class AssistantSessionDocument(
    val title: String? = null,
    val messages: List<ConversationMessageDocument> = listOf(),
)