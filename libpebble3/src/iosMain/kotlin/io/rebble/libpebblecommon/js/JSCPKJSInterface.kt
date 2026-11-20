package io.rebble.libpebblecommon.js

import co.touchlab.kermit.Logger

internal class JSCPKJSInterface(jsRunner: JsRunner, device: CompanionAppDevice, remoteTimelineEmulator: RemoteTimelineEmulator, jsTokenUtil: JsTokenUtil) :
    PKJSInterface(jsRunner, device, remoteTimelineEmulator, jsTokenUtil), RegisterableJsInterface {
    private val logger = Logger.withTag("JSCPKJSInterface")
    override val interf: Map<String, *> = mapOf(
        "showSimpleNotificationOnPebble" to this::showSimpleNotificationOnPebble,
        "getAccountToken" to this::getAccountToken,
        "getWatchToken" to this::getWatchToken,
        "showToast" to this::showToast,
        "openURL" to this::openURL,
    )
    override val name: String = "Pebble"

    override fun dispatch(method: String, args: List<Any?>): String? = when (method) {
        "showSimpleNotificationOnPebble" -> { showSimpleNotificationOnPebble(args[0].toString(), args[1].toString()); null }
        "getAccountToken" -> getAccountToken()
        "getWatchToken" -> getWatchToken()
        "showToast" -> { showToast(args[0].toString()); null }
        "openURL" -> openURL(args[0].toString())
        else -> error("Unknown method: $method")
    }

    override fun showToast(toast: String) {
        //TODO: Implement showToast for JSCPKJSInterface
        logger.e { "showToast() not implemented" }
    }

    override fun close() {
        // No-op
    }
}