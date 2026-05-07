package io.rebble.libpebblecommon.js

import co.touchlab.kermit.Logger
import io.rebble.libpebblecommon.connection.CompanionApp
import io.rebble.libpebblecommon.connection.ConnectedPebble
import io.rebble.libpebblecommon.database.entity.LockerEntry
import io.rebble.libpebblecommon.di.ConnectionCoroutineScope
import io.rebble.libpebblecommon.di.LibPebbleKoinComponent
import io.rebble.libpebblecommon.metadata.pbw.appinfo.PbwAppInfo
import io.rebble.libpebblecommon.services.appmessage.AppMessageData
import io.rebble.libpebblecommon.services.appmessage.AppMessageDictionary
import io.rebble.libpebblecommon.services.appmessage.AppMessageResult
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.io.files.Path
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.float
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import org.koin.core.component.get
import org.koin.core.parameter.parameterArrayOf
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

class PKJSApp(
    val device: CompanionAppDevice,
    private val jsPath: Path,
    val appInfo: PbwAppInfo,
    val lockerEntry: LockerEntry,
    private val connectionScope: ConnectionCoroutineScope,
): LibPebbleKoinComponent, CompanionApp {
    companion object {
        private val logger = Logger.withTag(PKJSApp::class.simpleName!!)
        /**
         * Maximum wait for [awaitWatchAppReady] to observe a first inbound
         * appMessage from the watchapp. Sized to give a cold-launching
         * watchapp time to finish its C-side init() and announce itself,
         * without keeping the user waiting unreasonably if the watchapp
         * happens not to message PKJS at startup.
         *
         * Talkative watchapps (those that send anything to PKJS during
         * init — most nontrivial watchapps do) will hit the signal well
         * inside this window. Quiet watchapps that don't message PKJS at
         * startup will hit the timeout and have any pending share intent
         * delivered without a perfect "ready" signal — same behavior as
         * before this state existed, so no regression.
         */
        val WATCHAPP_READY_TIMEOUT = 12.seconds
    }
    val uuid: Uuid by lazy { Uuid.parse(appInfo.uuid) }
    private var jsRunner: JsRunner? = null
    private var runningScope: CoroutineScope? = null
    private val urlOpenRequests = Channel<String>(Channel.RENDEZVOUS)

    private val _logMessages = Channel<String>(2, BufferOverflow.DROP_OLDEST)
    val logMessages: ReceiveChannel<String> = _logMessages
    val sessionIsReady get() = jsRunner?.readyState?.value ?: false

    /**
     * Observable form of [sessionIsReady]. Emits the current PKJS readiness
     * state and any future transitions. Stays at `false` while [jsRunner]
     * is null (before [start]); after start, mirrors the runner's own
     * readyState transitions.
     *
     * Useful for dispatchers that need to observe the false→true transition
     * rather than just sample current state. The pre-existing
     * `sessionIsReady` getter samples once and misses transitions if
     * polled before the runner exists or before its state flips.
     */
    private val _jsRunnerExists = MutableStateFlow(false)
    @OptIn(ExperimentalCoroutinesApi::class)
    val sessionReadyFlow: Flow<Boolean> = _jsRunnerExists
        .flatMapLatest { exists ->
            if (!exists) flowOf(false)
            else jsRunner?.readyState ?: flowOf(false)
        }

    /**
     * Flips to `true` the first time the watchapp on-watch sends an
     * appMessage to PKJS. Stays true for the lifetime of this PKJSApp.
     *
     * This is a more reliable "watchapp is fully ready" signal than
     * [sessionIsReady]: the latter only confirms the JS runtime has
     * started, not that the watch's C-side init() has completed and
     * subscribed to inbox messages. Without waiting for this, share
     * intent dispatches that fire CMD_PHONE_NAV_START can race the
     * watchapp's init and get dropped.
     *
     * "First message received" is a reasonable proxy because: a
     * watchapp that's sending us a message has clearly registered its
     * inbox subscription on the watch side, which is what we need.
     * Watchapps that never message PKJS at startup don't get the
     * benefit of this signal — for them, the dispatcher should fall
     * back to a timeout.
     */
    private val _firstWatchMessageReceived = MutableStateFlow(false)
    val firstWatchMessageReceived: StateFlow<Boolean> = _firstWatchMessageReceived

    /**
     * Suspend until either:
     *  - the watchapp sends its first inbound appMessage (returns true), or
     *  - [WATCHAPP_READY_TIMEOUT] elapses (returns false).
     *
     * Use this before delivering time-sensitive PKJS events (e.g. share
     * intents that translate into appMessages back to the watch) when
     * cold-starting the watchapp. If the watchapp was already running,
     * the state will likely already be true and this returns instantly.
     */
    suspend fun awaitWatchAppReady(): Boolean {
        if (_firstWatchMessageReceived.value) return true
        return withTimeoutOrNull(WATCHAPP_READY_TIMEOUT) {
            _firstWatchMessageReceived.first { it }
        } ?: false
    }

    private suspend fun replyNACK(id: UByte) {
        withTimeoutOrNull(1000) {
            device.sendAppMessageResult(AppMessageResult.ACK(id))
        }
    }

    private suspend fun replyACK(id: UByte) {
        withTimeoutOrNull(1000) {
            device.sendAppMessageResult(AppMessageResult.ACK(id))
        }
    }

    fun debugForceGC() {
        jsRunner?.debugForceGC() ?: error("JsRunner not initialized")
    }

    private fun launchIncomingAppMessageHandler(messages: Flow<AppMessageData>, scope: CoroutineScope) {
        messages.onEach { appMessageData ->
            // First inbound message from the watchapp signals that its
            // C-side init() has completed and its inbox subscription is
            // active — i.e. it's safe to push events that translate into
            // outbound appMessages. See [firstWatchMessageReceived] kdoc.
            if (!_firstWatchMessageReceived.value) {
                _firstWatchMessageReceived.value = true
            }
            jsRunner?.let { runner ->
                if (!runner.readyState.value) {
                    logger.w { "JsRunner not ready, waiting" }
                    val result = withTimeoutOrNull(6.seconds) {
                        runner.readyState.first { it }
                    } ?: false
                    if (!result) {
                        logger.w { "JsRunner still not ready after waiting, sending NACK" }
                        replyNACK(appMessageData.transactionId)
                        return@onEach
                    }
                }
                replyACK(appMessageData.transactionId)
                val dataString = appMessageData.data.toJSData(appInfo.appKeys)
                logger.d("Received app message: ${appMessageData.transactionId}")
                runner.signalNewAppMessageData(dataString)
            } ?: run {
                logger.w { "JsRunner not init'd, sending NACK" }
                replyNACK(appMessageData.transactionId)
                return@onEach
            }
        }.catch {
            logger.e(it) { "Error receiving app message: ${it.message}" }
        }.launchIn(scope)
    }

    private fun launchOutgoingAppMessageHandler(device: ConnectedPebble.AppMessages, scope: CoroutineScope) {
        jsRunner?.outgoingAppMessages?.onEach { request ->
            logger.d { "Sending app message: ${request.data}" }
            val tID = device.transactionSequence.next()
            request.state.value = AppMessageRequest.State.TransactionId(tID)
            val appMessage = try {
                request.data.toAppMessageData(appInfo, tID)
            } catch (e: IllegalArgumentException) {
                logger.e(e) { "Invalid app message data" }
                request.state.value = AppMessageRequest.State.DataError
                return@onEach
            }
            scope.launch {
                val result = device.sendAppMessage(appMessage)
                request.state.value = AppMessageRequest.State.Sent(result)
            }
        }?.catch {
            logger.e(it) { "Error sending app message" }
        }?.launchIn(scope) ?: error("JsRunner not initialized")
    }

    suspend fun requestConfigurationUrl(): String? {
        if (runningScope == null) {
            logger.e { "Cannot show configuration, PKJSApp is not running" }
            return null
        }
        val url = runningScope!!.async { urlOpenRequests.receive() }
        try {
            val jsRunner = jsRunner
            if (jsRunner != null) {
               jsRunner.signalShowConfiguration()
            } else {
               logger.e { "JsRunner not initialized, cannot show configuration" }
               url.cancel()
               return null
            }
        } catch (e: Exception) {
            url.cancel()
            logger.e(e) { "Error signalling show configuration" }
            return null
        }
        return url.await()
    }

    private fun injectJsRunner(scope: CoroutineScope): JsRunner =
        get {
            parameterArrayOf(
                device,
                scope,
                appInfo,
                lockerEntry,
                jsPath,
                urlOpenRequests,
                _logMessages
            )
        }

    override suspend fun start(incomingAppMessages: Flow<AppMessageData>) {
        val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
            logger.e(throwable) { "Unhandled exception in PKJSApp: ${throwable.message}" }
        }
        val scope = connectionScope + Job() + CoroutineName("PKJSApp-$uuid") + exceptionHandler
        runningScope = scope
        jsRunner = injectJsRunner(scope)
        // Toggle the gate that lets [sessionReadyFlow] start observing the
        // newly-created runner's readyState. Without this, observers
        // attached before start() would be stuck on the initial false
        // emission and never re-evaluate when the runner appears.
        _jsRunnerExists.value = true
        launchIncomingAppMessageHandler(incomingAppMessages, scope)
        launchOutgoingAppMessageHandler(device, scope)
        jsRunner?.start() ?: error("JsRunner not initialized")
    }

    override suspend fun stop() {
        jsRunner?.stop()
        runningScope?.cancel()
        jsRunner = null
        _jsRunnerExists.value = false
        // Reset the watch-ready signal so a future restart doesn't
        // observe a stale "ready" from a prior session.
        _firstWatchMessageReceived.value = false
    }

    fun triggerOnWebviewClosed(data: String) {
        runningScope?.launch {
            jsRunner?.signalWebviewClosed(data)
        }
    }

    /**
     * Deliver a share intent to this watchapp's PKJS. Caller must ensure the
     * runner has reached ready state (see [sessionIsReady]) before invoking,
     * otherwise the event will be dispatched into a JS context that has no
     * registered listeners yet.
     */
    fun triggerOnShareIntent(text: String, url: String?, subject: String?) {
        runningScope?.launch {
            jsRunner?.signalShareIntent(text, url, subject)
        }
    }
}

fun AppMessageDictionary.toJSData(appKeys: Map<String, Int>): String {
    val data = this.mapKeys {
        val id = it.key
        appKeys.entries.firstOrNull { it.value == id }?.key ?: id
    }
    return buildJsonObject {
        for ((key, value) in data) {
            when (value) {
                is String -> put(key.toString(), value)
                is Number -> put(key.toString(), value)
                // Unsigned types apparently don't inherit from Number (what??)
                is ULong -> put(key.toString(), value.toLong())
                is UInt -> put(key.toString(), value.toLong())
                is UShort -> put(key.toString(), value.toInt())
                is UByte -> put(key.toString(), value.toInt())
                is UByteArray -> {
                    val array = buildJsonArray {
                        for (byte in value) {
                            add(byte.toInt())
                        }
                    }
                    put(key.toString(), array)
                }
                is ByteArray -> {
                    val array = buildJsonArray {
                        for (byte in value) {
                            add(byte.toInt())
                        }
                    }
                    put(key.toString(), array)
                }
                is Boolean -> put(key.toString(), value)
                else -> error("Invalid JSON value, unsupported type ${value::class.simpleName}")
            }
        }
    }.toString()
}

private fun String.toAppMessageData(appInfo: PbwAppInfo, transactionId: UByte): AppMessageData {
    val jsonElement = Json.parseToJsonElement(this)
    require(jsonElement !is JsonNull) { "Invalid JSON data" }
    val jsonObject = jsonElement.jsonObject
    val tuples = jsonObject.mapNotNull { objectEntry ->
        val key = objectEntry.key
        val keyId = appInfo.appKeys[key] ?: key.toIntOrNull() ?: return@mapNotNull null
        when (objectEntry.value) {
            is JsonArray -> {
                Pair(keyId, objectEntry.value.jsonArray.map { it.jsonPrimitive.long.toUByte() }.toUByteArray())
            }
            is JsonObject -> error("Invalid JSON value, JsonObject not supported")
            else -> {
                when {
                    objectEntry.value.jsonPrimitive.isString -> {
                        Pair(keyId, objectEntry.value.jsonPrimitive.content)
                    }
                    objectEntry.value.jsonPrimitive.intOrNull != null -> {
                        Pair(keyId, objectEntry.value.jsonPrimitive.long.toInt())
                    }
                    objectEntry.value.jsonPrimitive.longOrNull != null -> {
                        Pair(keyId, objectEntry.value.jsonPrimitive.long.toUInt())
                    }
                    objectEntry.value.jsonPrimitive.booleanOrNull != null -> {
                        Pair(keyId, objectEntry.value.jsonPrimitive.boolean)
                    }
                    objectEntry.value.jsonPrimitive.doubleOrNull != null -> {
                        val value = objectEntry.value.jsonPrimitive.double.toInt()
                        Pair(keyId, value)
                    }
                    objectEntry.value.jsonPrimitive.floatOrNull != null -> {
                        val value = objectEntry.value.jsonPrimitive.float.toInt()
                        Pair(keyId, value)
                    }
                    else -> {
                        Logger.e("toAppMessageData") { "Invalid JSON value for key $key: ${objectEntry.value}" }
                        null // Skip unsupported types
                    }
                }
            }
        }
    }.toMap()
    return AppMessageData(
        transactionId = transactionId,
        uuid = Uuid.parse(appInfo.uuid),
        data = tuples
    )
}
