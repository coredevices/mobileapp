package coredevices.pebble.firmware

import io.rebble.libpebblecommon.connection.AppContext
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNTimeIntervalNotificationTrigger
import platform.UserNotifications.UNUserNotificationCenter

actual fun postWatchFullyChargedNotification(appContext: AppContext, watchName: String) {
    val content = UNMutableNotificationContent()
    content.setTitle("Watch Fully Charged")
    content.setBody("$watchName is fully charged")
    val request = UNNotificationRequest.requestWithIdentifier(
        "watch-fully-charged",
        content,
        UNTimeIntervalNotificationTrigger.triggerWithTimeInterval(1.0, false)
    )
    UNUserNotificationCenter.currentNotificationCenter().addNotificationRequest(request, null)
}
