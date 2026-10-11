package coredevices.pebble.permissions

import coredevices.pebble.RealPebbleDeepLinkHandler
import coredevices.pebble.ui.requestSummary
import io.rebble.libpebblecommon.connection.AppContext
import io.rebble.libpebblecommon.connection.LibPebble
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
) {
    fun init() {
        GlobalScope.launch {
            var shown = emptyMap<Uuid, List<String>>()
            libPebble.activePermissionRequests().collect { active ->
                val current = active.groupBy({ it.appUuid }, { it.permission })
                // Only something new is worth a notification: answering one of several shouldn't re-alert.
                current.filter { (appUuid, keys) -> !shown[appUuid].orEmpty().containsAll(keys) }
                    .forEach { (appUuid, keys) -> notifyPermissionRequest(appContext, notificationFor(appUuid, keys)) }
                (shown.keys - current.keys).forEach { removePermissionRequestNotification(appContext, keyOf(it)) }
                shown = current
            }
        }
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
    }
}

/** Answers those of [permissions] still waiting: the notification may be older than an answer given in the app. */
suspend fun answerPermissionRequests(libPebble: LibPebble, appUuid: Uuid, permissions: List<String>, granted: Boolean) {
    val waiting = libPebble.activePermissionRequests().first()
        .filter { it.appUuid == appUuid && it.permission in permissions }
        .map { it.permission }
    libPebble.setPermissionDecisions(appUuid, waiting.associateWith { granted })
}
