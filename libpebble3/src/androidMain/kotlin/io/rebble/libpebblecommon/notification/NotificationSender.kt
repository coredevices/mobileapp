package io.rebble.libpebblecommon.notification

import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import io.rebble.libpebblecommon.util.stripBidiIsolates

/**
 * Who the notification is from, so the watch can group its history by conversation: the
 * conversation title when set, otherwise the sender of the newest incoming message.
 */
fun StatusBarNotification.extractSender(): String? {
    val style = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification)
        ?: return null
    val user = style.user
    val newestIncoming = style.messages.asReversed().firstOrNull { message ->
        val person = message.person
        person != null && person != user
    }
    val sender = selectSender(
        conversationTitle = style.conversationTitle,
        isGroupConversation = style.isGroupConversation,
        newestIncomingSender = newestIncoming?.person?.name,
    )
    return stripBidiIsolates(sender)?.trim()?.takeIf { it.isNotEmpty() }
}

internal fun selectSender(
    conversationTitle: CharSequence?,
    isGroupConversation: Boolean,
    newestIncomingSender: CharSequence?,
): CharSequence? = when {
    !conversationTitle.isNullOrBlank() -> conversationTitle
    isGroupConversation -> null
    else -> newestIncomingSender
}
