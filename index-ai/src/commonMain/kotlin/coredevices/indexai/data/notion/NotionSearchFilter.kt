package coredevices.indexai.data.notion

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.Serializable

@Serializable
data class NotionSearchFilter(
    val value: Value,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val property: String = "object"
) {
    @Suppress("EnumEntryName")
    enum class Value {
        page,
        database
    }
}
