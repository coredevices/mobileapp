package io.rebble.libpebblecommon.io.rebble.libpebblecommon.notification

import android.app.Notification.WearableExtender
import android.service.notification.StatusBarNotification
import io.rebble.libpebblecommon.NotificationConfig
import io.rebble.libpebblecommon.SystemAppIDs.ANDROID_NOTIFICATIONS_UUID
import io.rebble.libpebblecommon.database.asMillisecond
import io.rebble.libpebblecommon.database.entity.ChannelItem
import io.rebble.libpebblecommon.database.entity.ContactEntity
import io.rebble.libpebblecommon.database.entity.NotificationAppItem
import io.rebble.libpebblecommon.database.entity.NotificationEntity
import io.rebble.libpebblecommon.database.entity.TimelineNotification
import io.rebble.libpebblecommon.database.entity.buildTimelineNotification
import io.rebble.libpebblecommon.io.rebble.libpebblecommon.notification.LibPebbleNotificationAction.ActionType
import io.rebble.libpebblecommon.notification.NotificationDecision
import io.rebble.libpebblecommon.packets.blobdb.TimelineIcon
import io.rebble.libpebblecommon.packets.blobdb.TimelineItem
import io.rebble.libpebblecommon.util.PebbleColor
import io.rebble.libpebblecommon.util.toPebbleColor
import kotlin.time.Instant
import kotlin.uuid.Uuid

data class LibPebbleNotification(
    val packageName: String,
    val uuid: Uuid,
    val groupKey: String?,
    val key: String,
    val timestamp: Instant,
    val title: String?,
    val body: String?,
    val icon: TimelineIcon,
    val actions: List<LibPebbleNotificationAction>,
    val people: List<ContactEntity>,
    val vibrationPattern: List<UInt>?,
    val color: Int? = null, // ARGB
) {
    fun displayDataEquals(other: LibPebbleNotification): Boolean {
        return packageName == other.packageName &&
                title == other.title &&
                body == other.body
    }

    companion object {
        fun actionsFromStatusBarNotification(
            sbn: StatusBarNotification,
            app: NotificationAppItem,
            channel: ChannelItem?,
            notificationConfig: NotificationConfig,
        ): List<LibPebbleNotificationAction> {
            val dismissAction = LibPebbleNotificationAction.dismissActionFromNotification(
                packageName = sbn.packageName,
                notification = sbn.notification
            )
            val contentAction = LibPebbleNotificationAction.contentActionFromNotification(
                packageName = sbn.packageName,
                notification = sbn.notification
            )
            val muteAction = LibPebbleNotificationAction.muteActionFrom(app)
            val muteChannelAction = LibPebbleNotificationAction.muteChannelActionFrom(
                app = app,
                channel = channel,
            )
            val wearableActions = WearableExtender(sbn.notification).actions
            val actionsToUse = when {
                wearableActions != null && wearableActions.isNotEmpty() -> wearableActions
                else -> sbn.notification.actions?.asList() ?: emptyList()
            }
            val actions = actionsToUse.mapNotNull {
                LibPebbleNotificationAction.fromNotificationAction(
                    packageName = sbn.packageName,
                    action = it,
                    notificationConfig = notificationConfig,
                )
            }
            val replyActions = actions.filter { it.type == ActionType.Reply }
            val nonReplyActions = actions.filterNot { it.type == ActionType.Reply }
            return buildList {
                dismissAction?.let { add(it) }
                addAll(replyActions)
                addAll(nonReplyActions)
                contentAction?.let { add(it) }
                muteAction?.let { add(it) }
                muteChannelAction?.let { add(it) }
            }
        }
    }

    fun toTimelineNotification(): TimelineNotification = buildTimelineNotification(
        timestamp = timestamp,
        parentId = ANDROID_NOTIFICATIONS_UUID,
    ) {
        itemID = uuid

        layout = TimelineItem.Layout.GenericNotification
        attributes {
            title?.let {
                title { it }
            }
            body?.let {
                body { it }
            }
            color?.let {
                backgroundColor { it.toPebbleColor() }
            }
            vibrationPattern?.let {
                vibrationPattern { it }
            }
            tinyIcon { icon }
        }
        actions {
            actions.forEach { action ->
                action(action.type.toProtocolType()) {
                    attributes {
                        title { action.title }
                        action.remoteInput?.suggestedResponses?.let {
                            cannedResponse { it.take(8) }
                        }
                    }
                }
            }
        }
    }
}

fun LibPebbleNotification.toEntity(
    decision: NotificationDecision,
    channelId: String?,
): NotificationEntity = NotificationEntity(
    pkg = packageName,
    key = key,
    groupKey = groupKey,
    timestamp = timestamp.asMillisecond(),
    title = title,
    body = body,
    decision = decision,
    channelId = channelId,
    people = people.map { it.lookupKey },
)

fun NotificationResult.notification(): LibPebbleNotification? = when (this) {
    is NotificationResult.Extracted -> notification
    NotificationResult.NotProcessed -> null
}

