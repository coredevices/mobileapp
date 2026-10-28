package io.rebble.libpebblecommon.connection.endpointmanager.timeline

import io.rebble.libpebblecommon.database.entity.BaseAction
import io.rebble.libpebblecommon.packets.blobdb.TimelineItem
import io.rebble.libpebblecommon.services.blobdb.TimelineActionResult
import kotlin.uuid.Uuid

interface PlatformNotificationActionHandler {
    suspend operator fun invoke(
        itemId: Uuid,
        action: BaseAction,
        attributes: List<TimelineItem.Attribute>
    ): TimelineActionResult

    fun onDisplayedItem(itemId: Uuid?) {}
}