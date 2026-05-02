package io.rebble.libpebblecommon.js

import io.rebble.libpebblecommon.connection.LibPebble
import io.rebble.libpebblecommon.database.entity.buildTimelineNotification
import io.rebble.libpebblecommon.packets.blobdb.TimelineItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.runBlocking
import kotlin.time.Clock
import kotlin.uuid.Uuid

abstract class PKJSInterface(
    protected val jsRunner: JsRunner,
    protected val device: CompanionAppDevice,
    private val libPebble: LibPebble,
    private val jsTokenUtil: JsTokenUtil,
) {
    open fun showSimpleNotificationOnPebble(title: String, notificationText: String) {
        runBlocking {
            libPebble.sendNotification(
                buildTimelineNotification(
                    timestamp = Clock.System.now(),
                    parentId = Uuid.parse(jsRunner.appInfo.uuid),
                ) {
                    layout = TimelineItem.Layout.GenericNotification
                    attributes {
                        title { title }
                        body { notificationText }
                    }
                }
            )
        }
    }

    /**
     * Get account token
     * Sideloaded apps: hash of token and app UUID
     * Appstore apps: hash of token and developer ID
     * //TODO: offline token
     */
    open fun getAccountToken(): String {
        //XXX: This is a blocking call, but it's fine because it's called from a WebView thread, maybe
        return runBlocking(Dispatchers.IO) {
            jsTokenUtil.getAccountToken(Uuid.parse(jsRunner.appInfo.uuid)) ?: ""
        }
    }

    /**
     * Get token of the watch for storing settings
     * Sideloaded apps: hash of watch serial and app UUID
     * Appstore apps: hash of watch serial and developer ID
     */
    open fun getWatchToken(): String {
        return runBlocking(Dispatchers.IO) {
            jsTokenUtil.getWatchToken(
                uuid = Uuid.parse(jsRunner.appInfo.uuid),
                developerId = jsRunner.lockerEntry.appstoreData?.developerId,
                watchInfo = device.watchInfo
            )
        }
    }

    abstract fun showToast(toast: String)

    /**
     * Open a URL e.g. configuration page
     */
    open fun openURL(url: String): String {
        runBlocking {
            jsRunner.loadUrl(url)
        }
        return url
    }

    /**
     * Returns currently-posted notifications matching the watchapp's
     * declared [io.rebble.libpebblecommon.metadata.pbw.appinfo.PbwAppInfo.notificationFilter],
     * narrowed by [packageFilter] if non-blank. Used by watchapps to catch
     * up on existing notification state when their PKJS spins up — otherwise
     * the watchapp only sees notifications that arrive *after* it starts.
     *
     * Returns a JSON-encoded array string with the same per-notification
     * shape as the `'appnotification'` event payload (without the `posted`
     * field, which is implicitly true for currently-active notifications).
     * Returns "[]" if the platform has no live-notification source (iOS today).
     *
     * @param packageFilter Optional comma-separated list of package names.
     *   Empty / blank = use the watchapp's full declared filter.
     */
    abstract fun getActiveNotifications(packageFilter: String): String
}
