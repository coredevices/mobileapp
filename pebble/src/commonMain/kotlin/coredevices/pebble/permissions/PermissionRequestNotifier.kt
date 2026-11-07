package coredevices.pebble.permissions

import coredevices.pebble.RealPebbleDeepLinkHandler
import coredevices.pebble.config.VisibleConfigPage
import coredevices.pebble.ui.requestSummary
import coredevices.util.WatchNotificationAction
import coredevices.util.WatchNotifier
import coredevices.util.setWatchNotificationActionHandler
import io.rebble.libpebblecommon.connection.AppContext
import io.rebble.libpebblecommon.connection.LibPebble
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlin.uuid.Uuid

/** A running app's runtime requests, answered together by the notification's actions. */
data class PermissionRequestNotification(
    val key: Int,
    val appUuid: Uuid,
    val permissions: List<String>,
    val title: String,
    val body: String,
    /** Opens the app's page, where its requests are answered. */
    val deepLink: String,
)

expect fun notifyPermissionRequest(appContext: AppContext, notification: PermissionRequestNotification)

expect fun removePermissionRequestNotification(appContext: AppContext, key: Int)

/**
 * One notification per running app with requests waiting on the user, removed once they're
 * answered (here or elsewhere in the app) or the app stops.
 */
class PermissionRequestNotifier(
    private val libPebble: LibPebble,
    private val appContext: AppContext,
    private val watchNotifier: WatchNotifier,
    private val visibleConfigPage: VisibleConfigPage,
) {
    /** Before the watch connects, so it can answer notifications sent before an app restart. */
    fun registerWatchActions() {
        listOf(true, false).forEach { granted ->
            libPebble.setWatchNotificationActionHandler(watchActionType(granted)) { data ->
                val (appUuid, permissions) = decodeWatchActionData(requireNotNull(data))
                answerPermissionRequests(libPebble, appUuid, permissions, granted)
            }
        }
    }

    fun init() {
        GlobalScope.launch {
            var shown = emptyMap<Uuid, List<String>>()
            libPebble.activePermissionRequests().collect { active ->
                val current = active.groupBy({ it.appUuid }, { it.permission })
                // Only something new is worth a notification: answering one of several shouldn't re-alert.
                // The app's open config page asks instead.
                current.filter { (appUuid, keys) ->
                    !shown[appUuid].orEmpty().containsAll(keys) && appUuid != visibleConfigPage.app.value
                }
                    .forEach { (appUuid, keys) -> notify(notificationFor(appUuid, keys)) }
                (shown.keys - current.keys).forEach { appUuid ->
                    removePermissionRequestNotification(appContext, keyOf(appUuid))
                    watchNotifier.remove(watchKey(appUuid))
                }
                shown = current
            }
        }
    }

    private fun notify(notification: PermissionRequestNotification) {
        notifyPermissionRequest(appContext, notification)
        val suffix = if (notification.permissions.size > 1) " all" else ""
        val data = Json.encodeToString(listOf(notification.appUuid.toString()) + notification.permissions)
        watchNotifier.send(
            key = watchKey(notification.appUuid),
            title = notification.title,
            body = notification.body,
            actions = listOf(
                WatchNotificationAction("Allow$suffix", watchActionType(true), data),
                WatchNotificationAction("Deny$suffix", watchActionType(false), data),
            ),
        )
    }

    private suspend fun notificationFor(appUuid: Uuid, keys: List<String>): PermissionRequestNotification {
        val appName = libPebble.getLockerApp(appUuid).first()?.properties?.title ?: "An app"
        return PermissionRequestNotification(
            key = keyOf(appUuid),
            appUuid = appUuid,
            permissions = keys,
            title = appName,
            body = "Wants to ${requestSummary(keys, libPebble.pluginPermissions().first())}",
            deepLink = RealPebbleDeepLinkHandler.permissionRequestsUri(appUuid),
        )
    }

    companion object {
        fun keyOf(appUuid: Uuid) = "permissions/$appUuid".hashCode()

        private fun watchKey(appUuid: Uuid) = "permission-request/$appUuid"

        private fun watchActionType(granted: Boolean) = if (granted) "permission_allow" else "permission_deny"

        private fun decodeWatchActionData(data: String): Pair<Uuid, List<String>> {
            val parts = Json.decodeFromString<List<String>>(data)
            return Uuid.parse(parts.first()) to parts.drop(1)
        }
    }
}

/** Answers those of [permissions] still waiting: the notification may be older than an answer given in the app. */
suspend fun answerPermissionRequests(libPebble: LibPebble, appUuid: Uuid, permissions: List<String>, granted: Boolean) {
    val waiting = libPebble.activePermissionRequests().first()
        .filter { it.appUuid == appUuid && it.permission in permissions }
        .map { it.permission }
    libPebble.setPermissionDecisions(appUuid, waiting.associateWith { granted })
}
