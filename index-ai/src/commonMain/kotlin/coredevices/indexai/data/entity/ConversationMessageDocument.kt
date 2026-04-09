@file:OptIn(ExperimentalSerializationApi::class)

package coredevices.indexai.data.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import co.touchlab.kermit.Logger
import coredevices.mcp.data.SemanticResult
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.Instant

private val logger by lazy { Logger.withTag("ConversationMessage") }

@Serializable
data class ConversationMessageDocument(
    val role: MessageRole,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val content: String? = null,
    @SerialName("tool_calls")
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val tool_calls: List<ToolCall>? = emptyList(),
    @SerialName("tool_call_id")
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val tool_call_id: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val semantic_result: SemanticResult? = null
)

@Entity(
    foreignKeys = [
        ForeignKey(
            entity = LocalRecording::class,
            parentColumns = ["id"],
            childColumns = ["recordingId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("recordingId"),
        Index(value = ["recordingId", "role", "timestamp"]) // For querying latest tool result/first assistant message
    ]
)
data class ConversationMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recordingId: Long,
    val timestamp: Instant = Clock.System.now(),
    @Embedded
    val document: ConversationMessageDocument
)

@Serializable
data class MessageContentPart(
    val type: ContentPartType,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val text: String? = null,
    @SerialName("image_url")
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val image_url: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val refusal: String? = null,
    @SerialName("input_audio")
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val input_audio: ContentInputAudio? = null,
)

@Suppress("EnumEntryName")
enum class ContentPartType {
    text,
    image,
    refusal,
    input_audio
}

@Serializable
data class ContentInputAudio(
    val data: String,
    val format: String
)

@Suppress("EnumEntryName")
enum class MessageRole {
    user,
    assistant,
    tool
}