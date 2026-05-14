@file:OptIn(ExperimentalTime::class)

package coredevices.indexai.data.entity

import kotlinx.datetime.LocalTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.InstantComponentSerializer
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Firestore-shape document for an item produced by ingesting a recording.
 * Stored at `items/{uid}/items/{itemId}`. Each recording can produce multiple items.
 *
 * `metadata` is a polymorphic field that captures additional structured data for certain item types
 */
@Serializable
data class ItemDocument(
    @Serializable(with = InstantComponentSerializer::class)
    val createdAt: Instant = Clock.System.now(),
    @Serializable(with = InstantComponentSerializer::class)
    val updatedAt: Instant = Clock.System.now(),
    val title: String = "",
    val body: String = "",
    val done: Boolean = false,
    @Serializable(with = InstantComponentSerializer::class)
    val dueAt: Instant? = null,
    val parentListIds: List<String> = emptyList(),
    val sourceRecordingId: String? = null,
    /**
     * The tool_call id (from `ConversationMessageDocument.tool_calls[].id`) that
     * produced this item. Null for items derived non-tool paths (book mention
     * post-pass, pure Q&A answers).
     */
    val sourceToolCallId: String? = null,
    val metadata: ItemMetadata = ItemMetadata.Note,
    val deleted: Boolean = false,
) {
    @Serializable
    sealed interface ItemMetadata {
        @Serializable
        @SerialName("reminder")
        data class Reminder(val repeat: String, val notification: String) : ItemMetadata

        @Serializable
        @SerialName("scheduled")
        data class Scheduled(
            val fireKind: FireKind,
            val fireTime: LocalTime? = null,
            val duration: Long? = null,
            val repeatDays: List<Int> = emptyList(),
            val enabled: Boolean? = null,
        ) : ItemMetadata {
            @Serializable
            enum class FireKind {
                @SerialName("alarm")
                Alarm,
                @SerialName("timer")
                Timer
            }
        }

        @Serializable
        @SerialName("message")
        data class Message(
            val integration: String,
            val contact: String,
            val recipientName: String,
            val text: String,
            val sentAt: Instant,
            val status: Status,
            val errorMessage: String? = null,
        ) : ItemMetadata {
            @Serializable
            enum class Status {
                @SerialName("sent")
                Sent,
                @SerialName("failed")
                Failed,
            }
        }

        @Serializable
        @SerialName("answer")
        data class Answer(val question: String) : ItemMetadata

        @Serializable
        @SerialName("action_log")
        data class ActionLog(val toolName: String, val success: Boolean) : ItemMetadata

        @Serializable
        @SerialName("mcp_call")
        data class McpCall(val toolName: String, val success: Boolean) : ItemMetadata

        @Serializable
        @SerialName("note")
        object Note : ItemMetadata
    }
}
