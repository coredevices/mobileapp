package coredevices.indexai.data.notion

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonIgnoreUnknownKeys
import kotlinx.serialization.json.JsonObject

@Serializable
@JsonIgnoreUnknownKeys
data class NotionBlock(
    val `object`: String = "block",
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val id: String? = null,
    val type: NotionBlockType,
    @SerialName("bulleted_list_item")
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val bulletedListItem: NotionGenericBlockContent? = null,
    @SerialName("heading_1")
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val heading1: NotionGenericBlockContent? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val archived: Boolean = false,
    val parent: NotionParentInfo? = null,
) {
    companion object {
        fun bulletedListItem(content: String): NotionBlock {
            return NotionBlock(type = NotionBlockType.bulleted_list_item, bulletedListItem = NotionGenericBlockContent(listOf(
                NotionRichText.fromString(content)
            ))
            )
        }

        fun heading1(content: String): NotionBlock {
            return NotionBlock(type = NotionBlockType.heading_1, heading1 = NotionGenericBlockContent(listOf(
                NotionRichText.fromString(content)
            ))
            )
        }
    }
}

@Suppress("EnumEntryName")
enum class NotionBlockType {
    bulleted_list_item,
    heading_1,
}

@Serializable
@JsonIgnoreUnknownKeys
data class NotionGenericBlockContent(
    @SerialName("rich_text")
    val richText: List<NotionRichText>,
)

@Serializable
data class NotionRichText(
    val text: NotionText,
    val type: String = "text",
    val annotations: JsonObject? = null,
    @SerialName("plain_text")
    val plainText: String? = null,
    val href: String? = null,
) {
    companion object {
        fun fromString(content: String): NotionRichText {
            return NotionRichText(NotionText(content))
        }
    }
}

@Serializable
data class NotionText(
    val content: String,
    val link: JsonObject? = null,
)