package io.rebble.libpebblecommon.js

import co.touchlab.kermit.Logger
import io.rebble.cobble.shared.data.js.ActivePebbleWatchInfo
import io.rebble.cobble.shared.data.js.fromWatchInfo
import io.rebble.libpebblecommon.services.appmessage.AppMessageResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

abstract class PrivatePKJSInterface(
    protected val jsRunner: JsRunner,
    private val device: CompanionAppDevice,
    protected val scope: CoroutineScope,
    private val outgoingAppMessages: MutableSharedFlow<AppMessageRequest>,
    private val logMessages: Channel<String>,
    private val jsTokenUtil: JsTokenUtil,
    private val remoteTimelineEmulator: RemoteTimelineEmulator,
) {
    companion object {
        private val logger = Logger.withTag("PrivatePKJSInterface")
        private val sensitiveTerms = setOf(
            "token", "password", "secret", "key", "credentials",
            "auth", "bearer", "lat", "lon", "location"
        )
    }

    open fun privateLog(message: String) {
        logger.i { "privateLog: $message" }
    }

    open fun onConsoleLog(level: String, message: String, source: String?) {
        logger.i {
            val containsSensitiveInfo = sensitiveTerms.any { term ->
                message.contains(term, ignoreCase = true)
            }
            buildString {
                append("[PKJS:${level.uppercase()}] \"")
                if (containsSensitiveInfo) {
                    append("<REDACTED>")
                } else {
                    append(message)
                }
                append("\" ")
                source?.let {
                    append(source)
                }
            }
        }
        val lineNumber = source
            ?.substringAfterLast("${jsRunner.appInfo.uuid}.js:", "")
            ?.ifBlank { "?" }
        logMessages.trySend("${jsRunner.appInfo.longName}:$lineNumber $message")
    }

    open fun onError(message: String?, source: String?, line: Double?, column: Double?) {
        logger.e { "JS Error: $message at $source:${line?.toInt()}:${column?.toInt()}" }
    }

    open fun onUnhandledRejection(reason: String) {
        logger.e { "Unhandled Rejection: $reason" }
    }

    open fun logInterceptedSend() {
        logger.v { "logInterceptedSend" }
    }

    open fun shouldIntercept(url: String): Boolean {
        return remoteTimelineEmulator.shouldIntercept(url)
    }

    open fun onIntercepted(url: String, method: String, body: String): String {
        val uuid = Uuid.parse(jsRunner.appInfo.uuid)
        return runBlocking { remoteTimelineEmulator.onIntercepted(url, method, body, uuid) }
    }

    open fun getVersionCode(): Int {
        logger.v { "getVersionCode" }
        TODO("Not yet implemented")
    }

    open fun getTimelineTokenAsync(): String {
        val uuid = Uuid.parse(jsRunner.appInfo.uuid)
        //TODO: Get token from locker or sandbox token if app is sideloaded
        scope.launch {
            val token = jsTokenUtil.getTimelineToken(uuid)
            if (token != null) {
                jsRunner.signalTimelineToken(callId = uuid.toString(), token = token)
            } else {
                jsRunner.signalTimelineTokenFail(callId = uuid.toString())
            }
        }
        return uuid.toString()
    }

    open fun signalAppScriptLoadedByBootstrap() {
        logger.v { "signalAppScriptLoadedByBootstrap" }
        scope.launch {
            jsRunner.signalReady()
        }
    }

    private fun buildAppMessageNack(transactionId: Int, error: String): String {
        return Json.encodeToString(Json.encodeToString(buildJsonObject {
            put("data", buildJsonObject {
                put("transactionId", transactionId)
            })
            put("error", error)
        }))
    }

    private suspend fun sendAppMessageNack(transactionId: Int) {
        val payload = buildAppMessageNack(transactionId, "nack")
        logger.v { "AppMessage NACK: $transactionId" }
        jsRunner.eval("signalAppMessageNack($payload)")
    }

    open fun sendAppMessageString(jsonAppMessage: String): Int {
        logger.v { "sendAppMessageString" }
        val request = AppMessageRequest(jsonAppMessage)
        val job = scope.launch(Dispatchers.IO) {
            val result = request.state.filterIsInstance<AppMessageRequest.State.Sent>().first().result
            when (result) {
                is AppMessageResult.ACK -> {
                    val payload = buildJsonObject {
                        put("data", buildJsonObject {
                            put("transactionId", result.transactionId.toInt())
                        })
                    }
                    logger.v { "AppMessage ACK: ${result.transactionId}" }
                    jsRunner.eval("signalAppMessageAck(${Json.encodeToString(Json.encodeToString(payload))})")
                }
                is AppMessageResult.NACK -> {
                    sendAppMessageNack(result.transactionId.toInt())
                }
            }
        }
        try {
            if (!outgoingAppMessages.tryEmit(request)) {
                logger.e { "Failed to emit outgoing AppMessage" }
                job.cancel("Failed to emit outgoing AppMessage")
                scope.launch { sendAppMessageNack(-1) }
                return -1
            }
            val transactionId = runBlocking {
                withTimeoutOrNull(5.seconds) {
                    request.state
                        .filter { it is AppMessageRequest.State.TransactionId || it is AppMessageRequest.State.DataError}
                        .first()
                }
            }
            return when (transactionId) {
                null -> {
                    logger.e { "Timeout while waiting for AppMessage transaction ID" }
                    scope.launch { sendAppMessageNack(-1) }
                    job.cancel("Timeout while waiting for AppMessage transaction ID")
                    -1
                }
                is AppMessageRequest.State.DataError -> {
                    logger.e { "Data error while sending AppMessage" }
                    scope.launch { sendAppMessageNack(-1) }
                    job.cancel("Data error while sending AppMessage")
                    -1
                }
                is AppMessageRequest.State.TransactionId -> {
                    transactionId.transactionId.toInt()
                }
                else -> error("Unexpected state: $transactionId")
            }
        } catch (e: Exception) {
            job.cancel("Error while sending AppMessage")
            throw e
        }
    }

    open fun getActivePebbleWatchInfo(): String {
        val info = device.watchInfo
        val targetPlatforms = jsRunner.appInfo.targetPlatforms
        val jsInfo = ActivePebbleWatchInfo.fromWatchInfo(info).let {
            it.copy(
                platform = if (it.platform in targetPlatforms) {
                    it.platform
                } else {
                    // Use closest old platform if new platform not supported
                    when (it.platform) {
                        "flint" -> if ("diorite" in targetPlatforms) "diorite" else "aplite"
                        "emery" -> "basalt"
                        else -> "aplite"
                    }
                }
            )
        }
        return Json.encodeToString(jsInfo)
    }

    open fun privateFnConfirmReadySignal(success: Boolean) {
        logger.v { "privateFnConfirmReadySignal($success)" }
        jsRunner.onReadyConfirmed(success)
    }

    open fun insertTimelinePin(pinJson: String) {
        val uuid = Uuid.parse(jsRunner.appInfo.uuid)
        runBlocking { remoteTimelineEmulator.insertPin(pinJson = pinJson, appUuid = uuid) }
    }

    open fun deleteTimelinePin(id: String) {
        val uuid = Uuid.parse(jsRunner.appInfo.uuid)
        runBlocking { remoteTimelineEmulator.deletePin(appUuid = uuid, pinIdentifier = id) }
    }
}
