package io.rebble.libpebblecommon.js

import android.content.Context
import android.webkit.JavascriptInterface
import android.widget.Toast
import co.touchlab.kermit.Logger
import io.rebble.libpebblecommon.connection.LibPebble
import io.rebble.libpebblecommon.di.LibPebbleKoinComponent
import io.rebble.libpebblecommon.io.rebble.libpebblecommon.notification.AndroidPebbleNotificationListenerConnection
import io.rebble.libpebblecommon.notification.WatchappNotificationSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import org.koin.core.component.inject

class WebViewPKJSInterface(
    jsRunner: JsRunner,
    device: CompanionAppDevice,
    private val context: Context,
    libPebble: LibPebble,
    jsTokenUtil: JsTokenUtil,
): PKJSInterface(jsRunner, device, libPebble, jsTokenUtil), LibPebbleKoinComponent {
    companion object {
        private val logger = Logger.withTag(WebViewPKJSInterface::class.simpleName!!)
    }

    // Lazy because not every PKJS instance needs notification access; cheap
    // to fetch from Koin when first called.
    private val notificationConnection: AndroidPebbleNotificationListenerConnection by inject()

    @JavascriptInterface
    override fun showSimpleNotificationOnPebble(title: String, notificationText: String) {
        super.showSimpleNotificationOnPebble(title, notificationText)
    }

    @JavascriptInterface
    override fun getAccountToken(): String {
        return super.getAccountToken()
    }

    @JavascriptInterface
    override fun getWatchToken(): String {
        return super.getWatchToken()
    }

    @JavascriptInterface
    override fun showToast(toast: String) {
        Toast.makeText(context, toast, Toast.LENGTH_SHORT).show()
    }

    @JavascriptInterface
    override fun openURL(url: String): String {
        return super.openURL(url)
    }

    /**
     * @param packageFilter Comma-separated list of package names. Empty string
     *   means "use the watchapp's full notificationFilter".
     */
    @JavascriptInterface
    override fun getActiveNotifications(packageFilter: String): String {
        val watchappFilter = jsRunner.appInfo.notificationFilter
        if (watchappFilter.isEmpty()) {
            // Watchapp didn't declare any subscriptions; nothing to return.
            return "[]"
        }

        val effectiveFilter: Set<String> = run {
            val requested = packageFilter
                .split(',')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toSet()
            // Intersect with declared filter so a watchapp can never read
            // notifications from a package it didn't declare interest in,
            // even if it asks. Empty requested set = use full declared set.
            if (requested.isEmpty()) watchappFilter.toSet()
            else requested.intersect(watchappFilter.toSet())
        }
        if (effectiveFilter.isEmpty()) return "[]"

        val service = notificationConnection.getService()
        if (service == null) {
            logger.w { "getActiveNotifications: notification listener not bound" }
            return "[]"
        }

        return try {
            val active = service.activeNotifications ?: return "[]"
            val json = buildJsonArray {
                for (sbn in active) {
                    if (sbn.packageName !in effectiveFilter) continue
                    // serialize() returns a JSON object string; reparse as
                    // JsonElement so we add it as a structured array entry,
                    // not as an embedded string. The notification listener
                    // service IS a Context — pass it through for icon
                    // extraction (smallIcon / largeIcon as base64 PNGs).
                    val element = Json.parseToJsonElement(
                        WatchappNotificationSerializer.serialize(sbn, posted = true, context = service)
                    )
                    add(element)
                }
            }
            json.toString()
        } catch (e: SecurityException) {
            // Listener service can throw if access was just revoked.
            logger.w(e) { "getActiveNotifications: SecurityException reading active notifications" }
            "[]"
        } catch (e: Exception) {
            logger.w(e) { "getActiveNotifications failed" }
            "[]"
        }
    }
}
