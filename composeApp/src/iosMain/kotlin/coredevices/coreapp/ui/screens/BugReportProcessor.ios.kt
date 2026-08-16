package coredevices.coreapp.ui.screens

import co.touchlab.kermit.Logger
import platform.UIKit.UIApplication
import platform.UIKit.UIBackgroundTaskIdentifier
import platform.UIKit.UIBackgroundTaskInvalid
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNUserNotificationCenter
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

private var bugReportBackgroundTask: UIBackgroundTaskIdentifier = UIBackgroundTaskInvalid

// iOS has no foreground service; keep the app running briefly after backgrounding
// so an in-progress attachment upload isn't suspended mid-transfer.
actual fun startForegroundService() {
    dispatch_async(dispatch_get_main_queue()) {
        val app = UIApplication.sharedApplication
        bugReportBackgroundTask = app.beginBackgroundTaskWithExpirationHandler {
            app.endBackgroundTask(bugReportBackgroundTask)
            bugReportBackgroundTask = UIBackgroundTaskInvalid
        }
    }
}

actual fun stopForegroundService() {
    dispatch_async(dispatch_get_main_queue()) {
        if (bugReportBackgroundTask != UIBackgroundTaskInvalid) {
            UIApplication.sharedApplication.endBackgroundTask(bugReportBackgroundTask)
            bugReportBackgroundTask = UIBackgroundTaskInvalid
        }
    }
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

