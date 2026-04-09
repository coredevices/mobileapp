package coredevices.indexai.data.notion

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonIgnoreUnknownKeys

@Serializable
@JsonIgnoreUnknownKeys
data class NotionErrorResponse(
    val `object`: String,
    val status: Int,
    val code: String,
    val message: String
)
