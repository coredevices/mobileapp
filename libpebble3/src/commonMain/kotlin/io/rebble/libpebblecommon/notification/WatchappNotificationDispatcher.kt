package io.rebble.libpebblecommon.notification

import co.touchlab.kermit.Logger
import io.rebble.libpebblecommon.connection.ConnectedPebbleDevice
import io.rebble.libpebblecommon.connection.LibPebble
import io.rebble.libpebblecommon.js.PKJSApp

/**
 * Fans notifications received by the platform notification listener out to
 * any currently-running PKJS watchapp whose
 * [io.rebble.libpebblecommon.metadata.pbw.appinfo.PbwAppInfo.notificationFilter]
 * subscribes to the source package.
 *
 * Foreground-only by design: PKJS only exists for the watchapp currently
 * running on the watch, so notifications only flow while the user has the
 * watchapp open. Watchapps catch up on existing state via
 * `Pebble.getActiveNotifications` when they spin up.
 *
 * Consent model: a watchapp's declaration in `package.json` is the consent
 * signal. The user explicitly installed a watchapp that announced it would
 * read these notifications; no separate per-watchapp prompt is needed. The
 * Pebble app's existing notification access grant (a single OS-level
 * permission already granted by the user) is the only OS-level gate.
 */
class WatchappNotificationDispatcher(
    private val libPebble: LibPebble,
) {
    companion object {
        private val logger = Logger.withTag(WatchappNotificationDispatcher::class.simpleName!!)
    }

    /**
     * @param packageName Source package of the notification.
     * @param notificationJson Pre-serialized payload (Android: built by
     *   [WatchappNotificationSerializer]; iOS: TBD when iOS receiving side
     *   lands).
     */
    fun dispatch(packageName: String, notificationJson: String) {
        val targets = currentSubscribedApps(packageName)
        if (targets.isEmpty()) return
        logger.v { "dispatching $packageName notification to ${targets.size} watchapp(s)" }
        for (app in targets) {
            try {
                app.triggerOnAppNotification(notificationJson)
            } catch (e: Exception) {
                logger.w(e) { "failed to deliver notification to ${app.uuid}" }
            }
        }
    }

    /**
     * Find currently-running PKJS watchapps that subscribe to [packageName].
     * Cheap to call on every notification — typically O(connected_watches)
     * with usually 1 watch and 1 running PKJS.
     */
    private fun currentSubscribedApps(packageName: String): List<PKJSApp> {
        return libPebble.watches.value
            .asSequence()
            .filterIsInstance<ConnectedPebbleDevice>()
            .flatMap { it.currentCompanionAppSessions.value.asSequence() }
            .filterIsInstance<PKJSApp>()
            .filter { it.subscribesTo(packageName) }
            .toList()
    }
}

private fun PKJSApp.subscribesTo(packageName: String): Boolean =
    appInfo.notificationFilter.contains(packageName)
