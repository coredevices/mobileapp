package io.rebble.libpebblecommon.io.rebble.libpebblecommon.notification

import android.service.notification.StatusBarNotification
import io.rebble.libpebblecommon.database.entity.ChannelItem
import io.rebble.libpebblecommon.database.entity.NotificationAppItem

interface NotificationProcessor {
    suspend fun extractNotification(
        sbn: StatusBarNotification,
        app: NotificationAppItem,
        channel: ChannelItem?,
    ): NotificationResult
}