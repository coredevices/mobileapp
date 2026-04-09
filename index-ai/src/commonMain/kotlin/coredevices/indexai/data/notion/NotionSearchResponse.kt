package coredevices.indexai.data.notion

import kotlin.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonIgnoreUnknownKeys
import kotlinx.serialization.json.JsonObject

@Serializable
@JsonIgnoreUnknownKeys
data class NotionSearchResponse(
    val `object`: String,
    val results: List<NotionSearchResult>,
    @SerialName("next_cursor")
    val nextCursor: String? = null,
    @SerialName("has_more")
    val hasMore: Boolean,
    val type: String
)

@Serializable
@JsonIgnoreUnknownKeys
data class NotionSearchResult(
    val `object`: String,
    val id: String,
    @SerialName("created_time")
    val createdTime: Instant,
    @SerialName("last_edited_time")
    val lastEditedTime: Instant,
    val archived: Boolean,
    val parent: NotionParentInfo,
    val properties: Map<String, JsonObject>,
)