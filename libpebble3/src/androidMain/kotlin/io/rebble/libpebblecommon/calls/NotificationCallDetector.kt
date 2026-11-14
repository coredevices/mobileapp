package io.rebble.libpebblecommon.calls

import android.app.Notification
import android.os.Build
import android.service.notification.StatusBarNotification
import co.touchlab.kermit.Logger
import io.rebble.libpebblecommon.util.PrivateLogger
import io.rebble.libpebblecommon.util.obfuscate
import io.rebble.libpebblecommon.util.stripBidiIsolates
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Store for CATEGORY_CALL notification actions and contact info.
 * Drives call state only through [notificationCalls], for calls the system doesn't report.
 */
class NotificationCallDetector(
    private val privateLogger: PrivateLogger,
) {
    companion object {
        private val logger = Logger.withTag("NotificationCallDetector")
    }

    private class DetectedCall(
        val notificationKey: String,
        val contactName: String?,
        val contactNumber: String?,
        val answerAction: Notification.Action?,
        val declineAction: Notification.Action?,
    )

    private val detectedCall = MutableStateFlow<DetectedCall?>(null)

    val contactName: String? get() = detectedCall.value?.contactName
    val contactNumber: String? get() = detectedCall.value?.contactNumber
    val answerAction: Notification.Action? get() = detectedCall.value?.answerAction
    val declineAction: Notification.Action? get() = detectedCall.value?.declineAction

    val notificationCalls: Flow<NotificationCall?> = detectedCall.map { call ->
        call?.let {
            NotificationCall(
                contactName = it.contactName,
                contactNumber = it.contactNumber,
                ringing = it.answerAction != null,
            )
        }
    }

    fun handleCallNotificationPosted(sbn: StatusBarNotification) {
        val notification = sbn.notification
        if (notification.category != Notification.CATEGORY_CALL) return

        // Dialers bidi-wrap the number; the watch only recognises a caller ID as a number if it is
        // digits and punctuation, otherwise it abbreviates it like a name.
        val title = stripBidiIsolates(notification.extras.getCharSequence(Notification.EXTRA_TITLE))
        val text = stripBidiIsolates(notification.extras.getCharSequence(Notification.EXTRA_TEXT))
        val answer = findAnswerAction(notification)
        val decline = findDeclineAction(notification)

        logger.d { "Call notification from ${sbn.packageName}: ${title.obfuscate(privateLogger)} / ${text.obfuscate(privateLogger)} (answer=${answer != null}, decline=${decline != null})" }

        detectedCall.value = DetectedCall(
            notificationKey = sbn.key,
            contactName = title,
            contactNumber = text,
            answerAction = answer,
            declineAction = decline,
        )
    }

    fun handleCallNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.key != detectedCall.value?.notificationKey) return
        logger.d { "Call notification removed from ${sbn.packageName}" }
        detectedCall.value = null
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
