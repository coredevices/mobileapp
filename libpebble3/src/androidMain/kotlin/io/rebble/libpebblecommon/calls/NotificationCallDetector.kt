package io.rebble.libpebblecommon.calls

import android.app.Notification
import android.os.Build
import android.service.notification.StatusBarNotification
import co.touchlab.kermit.Logger
import io.rebble.libpebblecommon.util.PrivateLogger
import io.rebble.libpebblecommon.util.obfuscate

/**
 * Passive store for CATEGORY_CALL notification actions and contact info.
 * Does not drive call state.
 */
class NotificationCallDetector(
    private val privateLogger: PrivateLogger,
) {
    companion object {
        private val logger = Logger.withTag("NotificationCallDetector")
    }

    private var activeNotificationKey: String? = null

    var contactName: String? = null
        private set
    var contactNumber: String? = null
        private set
    var answerAction: Notification.Action? = null
        private set
    var declineAction: Notification.Action? = null
        private set

    fun handleCallNotificationPosted(sbn: StatusBarNotification) {
        val notification = sbn.notification
        if (notification.category != Notification.CATEGORY_CALL) return

        val title = notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        val answer = findAnswerAction(notification)
        val decline = findDeclineAction(notification)

        logger.d { "Call notification from ${sbn.packageName}: ${title.obfuscate(privateLogger)} / ${text.obfuscate(privateLogger)} (answer=${answer != null}, decline=${decline != null})" }

        activeNotificationKey = sbn.key
        contactName = title
        contactNumber = text
        answerAction = answer
        declineAction = decline
    }

    fun handleCallNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.key != activeNotificationKey) return
        logger.d { "Call notification removed from ${sbn.packageName}" }
        clear()
    }

    private fun clear() {
        activeNotificationKey = null
        contactName = null
        contactNumber = null
        answerAction = null
        declineAction = null
    }

    private fun findAnswerAction(notification: Notification): Notification.Action? {
        val actions = notification.actions ?: return null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            actions.firstOrNull {
                it.semanticAction == Notification.Action.SEMANTIC_ACTION_CALL
            }?.let { return it }
        }
        return actions.firstOrNull { action ->
            val label = action.title?.toString()?.lowercase() ?: return@firstOrNull false
            label.contains("answer") || label.contains("accept") || label.contains("pick up")
        }
    }

    private fun findDeclineAction(notification: Notification): Notification.Action? {
        val actions = notification.actions ?: return null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            actions.firstOrNull {
                it.semanticAction == Notification.Action.SEMANTIC_ACTION_DELETE
            }?.let { return it }
        }
        return actions.firstOrNull { action ->
            val label = action.title?.toString()?.lowercase() ?: return@firstOrNull false
            label.contains("decline") || label.contains("reject") || label.contains("hang up")
                    || label.contains("dismiss") || label.contains("end")
        }
    }
}
