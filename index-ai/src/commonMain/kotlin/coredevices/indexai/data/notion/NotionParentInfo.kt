package coredevices.indexai.data.notion

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonIgnoreUnknownKeys

@Serializable
@JsonIgnoreUnknownKeys
data class NotionParentInfo(
    val type: String,
    @SerialName("database_id")
    val databaseId: String? = null,
    @SerialName("page_id")
    val pageId: String? = null,
    @SerialName("block_id")
    val blockId: String? = null,
    val workspace: Boolean? = null
) {
    fun getId(): String {
        return databaseId ?: pageId ?: blockId ?: throw IllegalStateException("No id found in NotionParentInfo")
    }
}
