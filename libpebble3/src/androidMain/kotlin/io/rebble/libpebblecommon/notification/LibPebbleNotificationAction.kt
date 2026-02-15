package io.rebble.libpebblecommon.io.rebble.libpebblecommon.notification

import android.app.Notification
import android.app.Notification.Action
import android.app.PendingIntent
import android.app.RemoteInput
import android.os.Build
import io.rebble.libpebblecommon.NotificationConfig
import io.rebble.libpebblecommon.database.entity.ChannelItem
import io.rebble.libpebblecommon.database.entity.NotificationAppItem
import io.rebble.libpebblecommon.notification.processor.NotificationProperties
import io.rebble.libpebblecommon.packets.blobdb.TimelineItem
import io.rebble.libpebblecommon.util.stripBidiIsolates

data class ActionRemoteInput(
    val remoteInput: RemoteInput,
    val suggestedResponses: List<String>? = null,
)

data class LibPebbleNotificationAction(
    val packageName: String,
    val title: String,
    val semanticAction: SemanticAction,
    val pendingIntent: PendingIntent?,
    val remoteInput: ActionRemoteInput?,
    val type: ActionType,
    val channelId: String? = null,
) {
    enum class ActionType {
        Generic,
        OpenOnPhone,
        Dismiss,
        Reply,
        MuteApp,
        MuteChannel,
        ;

        fun toProtocolType(): TimelineItem.Action.Type {
            return when (this) {
                Generic -> TimelineItem.Action.Type.Generic
                OpenOnPhone -> TimelineItem.Action.Type.Generic
                Dismiss -> TimelineItem.Action.Type.Dismiss
                Reply -> TimelineItem.Action.Type.Response
                MuteApp -> TimelineItem.Action.Type.Generic
                MuteChannel -> TimelineItem.Action.Type.Generic
            }
        }
    }

    companion object {
        fun fromNotificationAction(
            packageName: String,
            action: Action,
            notificationConfig: NotificationConfig,
            notificationProperties: NotificationProperties?,
        ): LibPebbleNotificationAction? {
            if (action.showsUserInterface()) {
                val showUserInterfaceActions = notificationConfig.addShowsUserInterfaceActions || notificationProperties?.addShowsUserInterfaceActions == true
                if (!showUserInterfaceActions) {
                    return null
                }
            }
            val semanticAction = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                SemanticAction.fromId(action.semanticAction)
            } else {
                SemanticAction.None
            }
            val title = stripBidiIsolates(action.title) ?: return null
            val pendingIntent = action.actionIntent ?: return null
            val input = action.remoteInputs?.firstOrNull {
                it.allowFreeFormInput
            }
            val suggestedResponses = input?.choices?.map { stripBidiIsolates(it.toString()) ?: "" }
            return LibPebbleNotificationAction(
                packageName = packageName,
                title = title,
                semanticAction = semanticAction,
                pendingIntent = pendingIntent,
                remoteInput = input?.let { ActionRemoteInput(input, suggestedResponses) },
                type = if (input != null) {
                    ActionType.Reply
                } else {
                    ActionType.Generic
                }
            )
        }

        fun contentActionFromNotification(
            packageName: String,
            notification: Notification
        ): LibPebbleNotificationAction? {
            val pendingIntent = notification.contentIntent ?: return null
            return LibPebbleNotificationAction(
                packageName = packageName,
                title = "Open on phone",
                semanticAction = SemanticAction.None,
                pendingIntent = pendingIntent,
                type = ActionType.OpenOnPhone,
                remoteInput = null,
            )
        }

        fun dismissActionFromNotification(
            packageName: String,
            notification: Notification
        ): LibPebbleNotificationAction? {
            return LibPebbleNotificationAction(
                packageName = packageName,
                title = "Dismiss",
                semanticAction = SemanticAction.None,
                pendingIntent = null,
                type = ActionType.Dismiss,
                remoteInput = null,
            )
        }

        fun muteActionFrom(app: NotificationAppItem): LibPebbleNotificationAction? {
            return LibPebbleNotificationAction(
                packageName = app.packageName,
                title = "Mute ${app.name}",
                semanticAction = SemanticAction.Mute,
                pendingIntent = null,
                type = ActionType.MuteApp,
                remoteInput = null,
            )
        }

        fun muteChannelActionFrom(
            app: NotificationAppItem,
            channel: ChannelItem?,
        ): LibPebbleNotificationAction? {
            if (channel == null) return null
            return LibPebbleNotificationAction(
                packageName = app.packageName,
                title = "Mute Channel ${channel.name}",
                semanticAction = SemanticAction.Mute,
                pendingIntent = null,
                type = ActionType.MuteChannel,
                remoteInput = null,
                channelId = channel.id,
            )
        }
    }

    enum class SemanticAction(val id: Int) {
        None(0),
        Reply(1),
        MarkAsRead(2),
        MarkAsUnread(3),
        Delete(4),
        Archive(5),
        Mute(6),
        Unmute(7),
        ThumbsUp(8),
        ThumbsDown(9),
        Call(10);

        companion object {
            fun fromId(id: Int): SemanticAction {
                return entries.firstOrNull { it.id == id } ?: None
            }
        }
    }
}