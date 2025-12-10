package coredevices.coreapp.ui.screens

import co.touchlab.kermit.Logger
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNUserNotificationCenter

actual fun startForegroundService() {
}

actual fun stopForegroundService() {
}

actual fun notifyState(message: String) {
    val content = UNMutableNotificationContent().apply {
        setTitle("Bug Report")
        setBody(message)
        setSound(UNNotificationSound.defaultSound)
    }

    val request = UNNotificationRequest.requestWithIdentifier(
        identifier = "bug_report_upload",
        content = content,
        trigger = null, // Show immediately
    )

    UNUserNotificationCenter.currentNotificationCenter().addNotificationRequest(
        request,
        withCompletionHandler = { error ->
            if (error != null) {
                Logger.e { "Failed to show notification: ${error.localizedDescription}" }
            }
        }
    )
}

