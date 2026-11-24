package coredevices.pebble.permissions

import coredevices.pebble.RealPebbleDeepLinkHandler
import coredevices.pebble.config.VisibleConfigPage
import coredevices.pebble.ui.joinRequests
import coredevices.pebble.ui.requestLabels
import coredevices.util.WatchNotificationAction
import coredevices.util.WatchNotifier
import coredevices.util.setWatchNotificationActionHandler
import io.rebble.libpebblecommon.connection.AppContext
import io.rebble.libpebblecommon.connection.LibPebble
import io.rebble.libpebblecommon.packets.blobdb.TimelineAttribute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlin.uuid.Uuid
import coreapp.pebble.generated.resources.Res
import coreapp.pebble.generated.resources.perm_request_unknown_app
import coreapp.pebble.generated.resources.perm_request_wants
import coreapp.pebble.generated.resources.perm_request_wants_review
import coreapp.pebble.generated.resources.perm_request_n_more
import coreapp.pebble.generated.resources.perm_request_wants_one
import coreapp.pebble.generated.resources.perm_request_wants_many
import coreapp.pebble.generated.resources.plugin_permission_list_and
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString

/** A running app's runtime requests, answered together by the notification's actions. */
data class PermissionRequestNotification(
    val key: Int,
    val appUuid: Uuid,
    val permissions: List<String>,
    val title: String,
    val body: String,
    /** False when [body] can't list them all, so they can only be allowed after reviewing them in the app. */
    val canAllow: Boolean,
    /** Opens the app's page, where its requests are answered. */
    val deepLink: String,
)

expect fun notifyPermissionRequest(appContext: AppContext, notification: PermissionRequestNotification)

expect fun removePermissionRequestNotification(appContext: AppContext, key: Int)

/**
 * One notification per running app with requests waiting on the user, removed once they're
 * answered (here or elsewhere in the app) or the app stops, but not until [MIN_NOTIFICATION_LIFETIME] after it was posted.
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
        GlobalScope.launch(Dispatchers.Default.limitedParallelism(1)) {
            val shown = mutableMapOf<Uuid, List<String>>()
            val postedAt = mutableMapOf<Uuid, TimeMark>()
            val removals = mutableMapOf<Uuid, Job>()
            fun remove(appUuid: Uuid) {
                removals.remove(appUuid)?.cancel()
                shown.remove(appUuid)
                postedAt.remove(appUuid)
                removePermissionRequestNotification(appContext, keyOf(appUuid))
                watchNotifier.remove(watchKey(appUuid))
            }
            combine(libPebble.activePermissionRequests(), libPebble.appsWithPendingPermissionRequests, ::Pair)
                .collect { (active, waiting) ->
                    val current = active.groupBy({ it.appUuid }, { it.permission })
                    current.forEach { (appUuid, keys) ->
                        removals.remove(appUuid)?.cancel()
                        // Only something new is worth a notification: answering one of several shouldn't re-alert.
                        // The app's open config page asks instead.
                        if (!shown[appUuid].orEmpty().containsAll(keys) && appUuid != visibleConfigPage.app.value) {
                            notify(notificationFor(appUuid, keys))
                            postedAt[appUuid] = TimeSource.Monotonic.markNow()
                        }
                        shown[appUuid] = keys
                    }
                    (shown.keys - current.keys).forEach { appUuid ->
                        when {
                            appUuid !in waiting -> remove(appUuid)
                            // Background apps may only run for a second: give the user time to see it.
                            appUuid !in removals -> removals[appUuid] = launch {
                                delay(postedAt[appUuid]?.let { MIN_NOTIFICATION_LIFETIME - it.elapsedNow() } ?: Duration.ZERO)
                                removals.remove(appUuid)
                                remove(appUuid)
                            }
                        }
                    }
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
            actions = listOfNotNull(
                WatchNotificationAction("Allow$suffix", watchActionType(true), data).takeIf { notification.canAllow },
                WatchNotificationAction("Deny$suffix", watchActionType(false), data),
            ),
        )
    }

    private suspend fun notificationFor(appUuid: Uuid, keys: List<String>): PermissionRequestNotification {
        val appName = libPebble.getLockerApp(appUuid).first()?.properties?.title ?: getString(Res.string.perm_request_unknown_app)
        val labels = requestLabels(keys, libPebble.pluginPermissions().first())
        val (body, complete) = requestBody(labels)
        return PermissionRequestNotification(
            key = keyOf(appUuid),
            appUuid = appUuid,
            permissions = keys,
            title = appName,
            body = body,
            canAllow = complete,
            deepLink = RealPebbleDeepLinkHandler.permissionRequestsUri(appUuid),
        )
    }

    companion object {
        private val MIN_NOTIFICATION_LIFETIME = 5.minutes

        fun keyOf(appUuid: Uuid) = "permissions/$appUuid".hashCode()

        /** As many of [labels] as the watch can show, then how many more; and whether that's all of them. */
        internal suspend fun requestBody(
            labels: List<String>,
            string: suspend (StringResource, Array<out Any>) -> String = { res, args -> getString(res, *args) },
        ): Pair<String, Boolean> {
            suspend fun summary(labels: List<String>): String =
                joinRequests(labels) { rest, last -> string(Res.string.plugin_permission_list_and, arrayOf(rest, last)) }

            val full = string(Res.string.perm_request_wants, arrayOf(summary(labels)))
            if (full.fitsOnWatch()) return full to true
            for (shown in labels.size - 1 downTo 1) {
                val listed = labels.take(shown) + string(Res.string.perm_request_n_more, arrayOf(labels.size - shown))
                val body = string(Res.string.perm_request_wants_review, arrayOf(summary(listed)))
                if (body.fitsOnWatch()) return body to false
            }
            return if (labels.size == 1) {
                string(Res.string.perm_request_wants_one, emptyArray())
            } else {
                string(Res.string.perm_request_wants_many, arrayOf(labels.size))
            } to false
        }

        private fun String.fitsOnWatch() = encodeToByteArray().size <= TimelineAttribute.Body.maxLength

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
    val decided = libPebble.permissionDecisions().first().filter { it.appUuid == appUuid }.map { it.permission }
    val waiting = permissions - decided.toSet()
    libPebble.setPermissionDecisions(appUuid, waiting.associateWith { granted })
}
