package coredevices.firestore

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class User(
    @SerialName("notion_token")
    val notionToken: String? = null,
    @SerialName("notion_error_reason")
    val notionOauthErrorReason: String? = null,
    @SerialName("todo_block_id")
    val todoBlockId: String? = null,
    @SerialName("rebble_user_token")
    val rebbleUserToken: String? = null,
    @SerialName("pebble_user_token")
    val pebbleUserToken: String? = null,
    @SerialName("last_connected_watch")
    val lastConnectedWatch: String? = null,
)