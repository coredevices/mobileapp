package io.rebble.libpebblecommon.js

import co.touchlab.kermit.Logger
import io.rebble.libpebblecommon.connection.LibPebble

class JSCPKJSInterface(jsRunner: JsRunner, device: CompanionAppDevice, libPebble: LibPebble, jsTokenUtil: JsTokenUtil) :
    PKJSInterface(jsRunner, device, libPebble, jsTokenUtil), RegisterableJsInterface {
    private val logger = Logger.withTag("JSCPKJSInterface")
    override val interf = mapOf(
        "showSimpleNotificationOnPebble" to this::showSimpleNotificationOnPebble,
        "getAccountToken" to this::getAccountToken,
        "getWatchToken" to this::getWatchToken,
        "showToast" to this::showToast,
        "openURL" to this::openURL,
        "getActiveNotifications" to this::getActiveNotifications,
    )
    override val name = "Pebble"

    override fun dispatch(method: String, args: List<Any?>) = when (method) {
        "showSimpleNotificationOnPebble" -> { showSimpleNotificationOnPebble(args[0].toString(), args[1].toString()); null }
        "getAccountToken" -> getAccountToken()
        "getWatchToken" -> getWatchToken()
        "showToast" -> { showToast(args[0].toString()); null }
        "openURL" -> openURL(args[0].toString())
        "getActiveNotifications" -> getActiveNotifications(args.getOrNull(0)?.toString().orEmpty())
        else -> error("Unknown method: $method")
    }

    override fun showToast(toast: String) {
        //TODO: Implement showToast for JSCPKJSInterface
        logger.e { "showToast() not implemented" }
    }

    /**
     * iOS notification source-of-truth (UserNotifications framework / a Share
     * Extension subscribing to NotificationCenter delegate callbacks) is not
     * yet wired up. Return empty array so PKJS apps that try the API on iOS
     * degrade gracefully — they'll still get nothing useful, but won't error.
     */
    override fun getActiveNotifications(packageFilter: String): String {
        logger.v { "getActiveNotifications() not yet implemented on iOS" }
        return "[]"
    }

    override fun close() {
        // No-op
    }
}