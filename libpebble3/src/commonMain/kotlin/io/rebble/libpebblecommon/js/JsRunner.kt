package io.rebble.libpebblecommon.js

import io.rebble.libpebblecommon.database.entity.LockerEntry
import io.rebble.libpebblecommon.metadata.pbw.appinfo.PbwAppInfo
import io.rebble.libpebblecommon.services.appmessage.AppMessageResult
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.io.files.Path

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
    /**
     * Fired when the OS routes a shared payload (e.g. an Android `ACTION_SEND`)
     * to this watchapp. The watchapp must declare itself as a share target via
     * `shareTarget` in `package.json` to receive these events.
     *
     * @param text The shared text or URL string. Always present.
     * @param url Best-effort extraction of a URL from [text], or null if none.
     * @param subject Optional subject (e.g. Android `EXTRA_SUBJECT`).
     */
    abstract suspend fun signalShareIntent(text: String, url: String?, subject: String?)
    /**
     * Fired when the OS surfaces a notification from a package that this
     * watchapp's [PbwAppInfo.notificationFilter] subscribes to, while this
     * watchapp's PKJS is running. The payload is a JSON string the bootstrap
     * `startup.js` parses and dispatches via `'appnotification'` events.
     *
     * @param notificationJson Pre-serialized notification payload. Layout:
     *   `{ "package": String, "posted": Boolean, "key": String, "postTime": Long,
     *      "category": String?, "title": String?, "text": String?,
     *      "subText": String?, "extras": { ... raw extras keys ... } }`
     */
    abstract suspend fun signalAppNotification(notificationJson: String)
    abstract suspend fun eval(js: String)
    abstract suspend fun evalWithResult(js: String): Any?
    abstract fun debugForceGC()

    fun onReadyConfirmed(success: Boolean) {
        _readyState.value = true
    }

    suspend fun loadUrl(url: String) {
        urlOpenRequests.trySend(url)
    }

    protected val _outgoingAppMessages = MutableSharedFlow<AppMessageRequest>(extraBufferCapacity = 1)
    val outgoingAppMessages = _outgoingAppMessages.asSharedFlow()
    protected val _readyState = MutableStateFlow(false)
    val readyState = _readyState.asStateFlow()
}

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
