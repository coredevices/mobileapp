package io.rebble.libpebblecommon.notification

import co.touchlab.kermit.Logger
import io.rebble.libpebblecommon.connection.ConnectedPebbleDevice
import io.rebble.libpebblecommon.connection.LibPebble
import io.rebble.libpebblecommon.js.PKJSApp
import io.rebble.libpebblecommon.metadata.pbw.appinfo.NotificationSubscription

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
 *
 * Per-watchapp serialization: each subscribed watchapp may have asked for
 * a different set of fields ([NotificationSubscription.fields]).
 * Serialization is therefore deferred until the dispatch loop knows which
 * watchapp it's serializing for; the platform listener passes a
 * [SerializerCallback] rather than a pre-built JSON string. Field
 * extraction is gated on what each watchapp asked for so heavyweight
 * extractors (BigPicture decoding, MediaSession lookup, RemoteViews
 * traversal) are skipped entirely for watchapps that didn't request them.
 */
class WatchappNotificationDispatcher(
    private val libPebble: LibPebble,
) {
    companion object {
        private val logger = Logger.withTag(WatchappNotificationDispatcher::class.simpleName!!)
    }

    /**
     * Build a notification payload tailored to one subscribing watchapp.
     * Implemented platform-side because Android's `StatusBarNotification`
     * extraction can't be expressed in commonMain.
     */
    fun interface SerializerCallback {
        /**
         * @param subscription Which watchapp is receiving — its
         *   [NotificationSubscription.fields] determines what the
         *   platform side extracts and emits.
         * @return Serialized JSON ready for `triggerOnAppNotification`,
         *   or null if extraction failed unrecoverably (notification
         *   will be silently skipped for this watchapp).
         */
        fun build(subscription: NotificationSubscription): String?
    }

    /**
     * @param packageName Source package of the notification.
     * @param serializer Per-watchapp payload builder; called once per
     *   subscribing watchapp.
     */
    fun dispatch(packageName: String, serializer: SerializerCallback) {
        val targets = currentSubscribedApps(packageName)
        if (targets.isEmpty()) return
        logger.v { "dispatching $packageName notification to ${targets.size} watchapp(s)" }
        for ((app, subscription) in targets) {
            try {
                val json = serializer.build(subscription) ?: continue
                app.triggerOnAppNotification(json)
            } catch (e: Exception) {
                logger.w(e) { "failed to deliver notification to ${app.uuid}" }
            }
        }
    }

    /**
     * Find currently-running PKJS watchapps subscribed to [packageName],
     * paired with the matching subscription so the dispatch loop knows
     * which fields each watchapp asked for. Cheap to call on every
     * notification — typically O(connected_watches) with usually 1
     * watch and 1 running PKJS.
     */
    private fun currentSubscribedApps(
        packageName: String,
    ): List<Pair<PKJSApp, NotificationSubscription>> {
        return libPebble.watches.value
            .asSequence()
            .filterIsInstance<ConnectedPebbleDevice>()
            .flatMap { it.currentCompanionAppSessions.value.asSequence() }
            .filterIsInstance<PKJSApp>()
            .mapNotNull { app ->
                val sub = app.subscriptionFor(packageName) ?: return@mapNotNull null
                app to sub
            }
            .toList()
    }
}

private fun PKJSApp.subscriptionFor(packageName: String): NotificationSubscription? =
    appInfo.notificationFilter.firstOrNull { it.packageName == packageName }
