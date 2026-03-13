package coredevices.coreapp.push

import kotlinx.serialization.Serializable

/**
 * Root response from the sync endpoint
 */
@Serializable
data class SyncResponse(
    val updates: List<SyncUpdate>,
    val syncURL: String? = null,
)

/**
 * Individual update item in the sync response
 */
@Serializable
data class SyncUpdate(
    val type: String,
    val data: PinData,
)

/**
 * Timeline pin data structure matching the actual server response
 */
@Serializable
data class PinData(
    val createTime: String? = null,
    val dataSource: String? = null,
    val guid: String? = null,
    val layout: LayoutData? = null,
    val source: String? = null,
    val time: String? = null,
    val topicKeys: List<String> = emptyList(),
    val updateTime: String? = null,
)

/**
 * Layout data structure
 */
@Serializable
data class LayoutData(
    val type: String? = null,
    val title: String? = null,
    val subtitle: String? = null,
    val tinyIcon: String? = null,
)