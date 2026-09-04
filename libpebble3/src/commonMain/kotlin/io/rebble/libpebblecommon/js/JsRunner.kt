package io.rebble.libpebblecommon.js

import io.rebble.libpebblecommon.database.entity.LockerEntry
import io.rebble.libpebblecommon.metadata.pbw.appinfo.PbwAppInfo
import io.rebble.libpebblecommon.services.appmessage.AppMessageResult
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.update
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.io.files.Path
import kotlin.time.Duration.Companion.seconds

abstract class JsRunner(
    val appInfo: PbwAppInfo,
    val lockerEntry: LockerEntry,
    val jsPath: Path,
    val device: CompanionAppDevice,
    private val urlOpenRequests: Channel<String>,
) {
    abstract suspend fun start()
    abstract suspend fun stop()
    abstract suspend fun loadAppJs(jsUrl: String)
    abstract suspend fun signalInterceptResponse(callbackId: String, result: InterceptResponse)
    abstract suspend fun signalNewAppMessageData(data: String?): Boolean
    abstract suspend fun signalTimelineToken(callId: String, token: String)
    abstract suspend fun signalTimelineTokenFail(callId: String)
    abstract suspend fun signalReady()
    abstract suspend fun signalShowConfiguration()
    abstract suspend fun signalWebviewClosed(data: String?)
    abstract suspend fun signalConfigMessage(requestId: Int, json: String)
    abstract suspend fun eval(js: String)
    abstract suspend fun evalWithResult(js: String): Any?
    abstract fun debugForceGC()

    fun onReadyConfirmed(success: Boolean) {
        _readyState.value = true
    }

    suspend fun loadUrl(url: String) {
        urlOpenRequests.trySend(url)
    }

    /**
     * Config-page traffic. Request/reply is correlated here rather than through a flow so a
     * fast reply cannot land before the caller is listening. Atomic because the reply arrives on
     * whichever thread the JS engine calls back on, not the one that sent the message.
     */
    private val configReplies = atomic(mapOf<Int, CompletableDeferred<String>>())
    private val nextConfigRequestId = atomic(0)
    private val _configPushes = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val configPushes = _configPushes.asSharedFlow()

    /** Send [json] to the app's JS and wait for it to answer. Null if it doesn't. */
    suspend fun sendConfigMessage(json: String): String? {
        val id = nextConfigRequestId.getAndIncrement()
        val reply = CompletableDeferred<String>()
        configReplies.update { it + (id to reply) }
        return try {
            signalConfigMessage(id, json)
            withTimeoutOrNull(CONFIG_MESSAGE_TIMEOUT) { reply.await() }
        } finally {
            configReplies.update { it - id }
        }
    }

    fun onConfigReply(requestId: Int, json: String) {
        configReplies.value[requestId]?.complete(json)
    }

    fun onConfigPush(json: String) {
        _configPushes.tryEmit(json)
    }

    protected val _outgoingAppMessages = MutableSharedFlow<AppMessageRequest>(extraBufferCapacity = 1)
    val outgoingAppMessages = _outgoingAppMessages.asSharedFlow()
    protected val _readyState = MutableStateFlow(false)
    val readyState = _readyState.asStateFlow()
}

private val CONFIG_MESSAGE_TIMEOUT = 30.seconds

class AppMessageRequest(
    val data: String
) {
    sealed class State {
        object Pending : State()
        object DataError : State()
        data class TransactionId(val transactionId: UByte) : State()
        data class Sent(val result: AppMessageResult) : State()
    }
    val state = MutableStateFlow<State>(State.Pending)
}
