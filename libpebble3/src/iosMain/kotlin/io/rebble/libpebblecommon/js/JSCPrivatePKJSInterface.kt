package io.rebble.libpebblecommon.js

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.io.files.Path

class JSCPrivatePKJSInterface(
    private val jsPath: Path,
    jsRunner: JsRunner,
    device: CompanionAppDevice,
    scope: CoroutineScope,
    outgoingAppMessages: MutableSharedFlow<AppMessageRequest>,
    logMessages: Channel<String>,
    jsTokenUtil: JsTokenUtil,
    remoteTimelineEmulator: RemoteTimelineEmulator,
    httpInterceptorManager: HttpInterceptorManager,
): PrivatePKJSInterface(jsRunner, device, scope, outgoingAppMessages, logMessages, jsTokenUtil, remoteTimelineEmulator, httpInterceptorManager), RegisterableJsInterface {
    private val logger = Logger.withTag("JSCPrivatePKJSInterface")

    override val interf = mapOf(
        "sendAppMessageString" to this::sendAppMessageString,
        "privateLog" to this::privateLog,
        "onConsoleLog" to { level: Any?, message: Any?, trace: Any? ->
            if (level == null) {
                logger.w { "onConsoleLog called with null level" }
                return@to
            }
            val sourceLine = trace
                ?.toString()
                ?.split("\n")
                ?.getOrNull(2)
                ?.trim()
                ?.substringAfter("code@")
            this.onConsoleLog(level.toString(), message.toString(), sourceLine)
        },
        "onError" to this::onError,
        "onUnhandledRejection" to this::onUnhandledRejection,
        "logInterceptedSend" to this::logInterceptedSend,
        "getVersionCode" to this::getVersionCode,
        "getTimelineTokenAsync" to this::getTimelineTokenAsync,
        "privateFnConfirmReadySignal" to this::privateFnConfirmReadySignal,
        "getActivePebbleWatchInfo" to this::getActivePebbleWatchInfo,
        "insertTimelinePin" to this::insertTimelinePin,
        "deleteTimelinePin" to this::deleteTimelinePin,
    )

    override val name: String = "_Pebble"

    override fun close() {
        // No-op
    }
}