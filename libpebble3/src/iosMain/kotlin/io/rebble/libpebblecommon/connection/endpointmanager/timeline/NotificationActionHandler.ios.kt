package io.rebble.libpebblecommon.connection.endpointmanager.timeline

import io.rebble.libpebblecommon.connection.DisplayedNotificationReplyResult
import io.rebble.libpebblecommon.connection.LibPebble
import io.rebble.libpebblecommon.connection.NotificationReplies
import io.rebble.libpebblecommon.database.entity.BaseAction
import io.rebble.libpebblecommon.notification.NotificationAppsSync
import io.rebble.libpebblecommon.notification.NotificationListenerConnection
import io.rebble.libpebblecommon.packets.blobdb.TimelineItem
import io.rebble.libpebblecommon.services.blobdb.TimelineActionResult
import kotlin.time.Instant
import kotlin.uuid.Uuid

class IosNotificationActionHandler : PlatformNotificationActionHandler, NotificationReplies {
    override suspend operator fun invoke(
        itemId: Uuid,
        action: BaseAction,
        attributes: List<TimelineItem.Attribute>
    ): TimelineActionResult {
        error("Notification actions are not handled by app on iOS")
    }

    override suspend fun replyToDisplayedNotification(text: String, at: Instant) =
        DisplayedNotificationReplyResult.Unsupported
}

class IosNotificationListenerConnection : NotificationListenerConnection {
    override fun init(libPebble: LibPebble) {
    }
}

class IosNotificationAppsSync : NotificationAppsSync {
    override fun init() {
    }
}
