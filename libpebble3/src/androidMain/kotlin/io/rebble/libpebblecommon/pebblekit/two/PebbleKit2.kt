package io.rebble.libpebblecommon.pebblekit.two

import co.touchlab.kermit.Logger
import io.rebble.libpebblecommon.ErrorTracker
import io.rebble.libpebblecommon.connection.CompanionApp
import io.rebble.libpebblecommon.connection.UserFacingError
import io.rebble.libpebblecommon.di.ConnectionCoroutineScope
import io.rebble.libpebblecommon.di.LibPebbleCoroutineScope
import io.rebble.libpebblecommon.di.LibPebbleKoinComponent
import io.rebble.libpebblecommon.js.CompanionAppDevice
import io.rebble.libpebblecommon.metadata.pbw.appinfo.PbwAppInfo
import io.rebble.libpebblecommon.services.appmessage.AppMessageData
import io.rebble.libpebblecommon.services.appmessage.AppMessageDictionary
import io.rebble.libpebblecommon.services.appmessage.AppMessageResult
import io.rebble.pebblekit2.common.model.PebbleDictionary
import io.rebble.pebblekit2.common.model.PebbleDictionaryItem
import io.rebble.pebblekit2.common.model.ReceiveResult
import io.rebble.pebblekit2.common.model.TransmissionResult
import io.rebble.pebblekit2.common.model.WatchIdentifier
import io.rebble.pebblekit2.server.DefaultPebbleListenerConnector
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid

class PebbleKit2(
    private val device: CompanionAppDevice,
    private val appInfo: PbwAppInfo,
    private val pkjsRunning: Boolean,
    private val libpebbleScope: LibPebbleCoroutineScope,
    private val connectionScope: ConnectionCoroutineScope,
) : LibPebbleKoinComponent, CompanionApp {
    private val nextTransactionId = atomic(0)
    private val targetPackages = appInfo.companionApp?.android?.apps.orEmpty().mapNotNull { it.pkg }
    private val connector = DefaultPebbleListenerConnector(getKoin().get(), targetPackages)
    private val errorTracker: ErrorTracker = getKoin().get<ErrorTracker>()

    val uuid: Uuid by lazy { Uuid.Companion.parse(appInfo.uuid) }
    private var runningScope: CoroutineScope? = null
    private var incomingConsumer: Job? = null

    override suspend fun start(incomingAppMessages: Flow<AppMessageData>) {
        val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
            logger.e(throwable) { "Unhandled exception in PebbleKit2 $uuid: ${throwable.message}" }
        }
        val scope = connectionScope + Job() + CoroutineName("PebbleKit2-$uuid") + exceptionHandler
        runningScope = scope

        scope.launch {
            val connectSuccess = connector.sendOnAppOpened(
                uuid.toJavaUuid(),
                WatchIdentifier(device.watchInfo.serial)
            )

            if (connectSuccess) {
                launchIncomingAppMessageHandler(scope, incomingAppMessages)
            } else {
                val appName = appInfo.shortName
                val downloadUrl = appInfo.companionApp?.android?.url
                errorTracker.reportError(
                    UserFacingError.MissingCompanionApp(
                        "$appName needs a companion app to function properly",
                        appName,
                        uuid,
                        downloadUrl
                    )
                )
                if (!pkjsRunning) {
                    // Don't auto-NACK if PKJS is running
                    launchNackAllIncomingMessages(scope, incomingAppMessages)
                }
            }
        }
    }

    override suspend fun stop() {
        incomingConsumer?.cancel()

        val scope = runningScope
        runningScope = null

        // Cancel the scope immediately to release the AppMessage channel
        scope?.cancel()

        // Give the companion app a couple of seconds to clean up before closing the connection
        libpebbleScope.launch {
            connector.sendOnAppClosed(uuid.toJavaUuid(), WatchIdentifier(device.watchInfo.serial))
            delay(5.seconds)
            connector.close()
        }
    }


    fun isAllowedToCommunicate(pkg: String): Boolean {
        return targetPackages.contains(pkg)
    }

    suspend fun sendMessage(pebbleDictionary: PebbleDictionary): TransmissionResult {
        val transactionId = (nextTransactionId.getAndIncrement() % UByte.MAX_VALUE.toInt()).toUByte()

        return try {
            val runningScope = runningScope ?: return TransmissionResult.FailedDifferentAppOpen
            // Send through async to ensure that cancelling of the running scope (presumably because app got closed)
            // immediately cancels the sending
            val res = runningScope.async {
                device.sendAppMessage(AppMessageData(transactionId, uuid, pebbleDictionary.toAppMessageDict()))
            }.await()

            when (res) {
                is AppMessageResult.ACK -> TransmissionResult.Success
                is AppMessageResult.NACK -> TransmissionResult.FailedWatchNacked
            }
        } catch (e: CancellationException) {
            currentCoroutineContext().ensureActive()

            TransmissionResult.FailedDifferentAppOpen
        }
    }

    private fun launchIncomingAppMessageHandler(scope: CoroutineScope, messages: Flow<AppMessageData>) {
        incomingConsumer = messages.onEach { appMessageData ->
            try {
                logger.d { "Got inbound AppMessage" }

                val pebbleDictionary = appMessageData.data.toPebbleDictionary()
                val result = connector.sendOnMessageReceived(
                    uuid.toJavaUuid(),
                    pebbleDictionary,
                    WatchIdentifier(device.watchInfo.serial)
                )

                if (result == ReceiveResult.Ack) {
                    replyACK(appMessageData.transactionId)
                } else {
                    replyNACK(appMessageData.transactionId)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.e(e) { "Error receiving app message from $uuid: ${e.message}" }
            }
        }.launchIn(scope)
    }

    private fun launchNackAllIncomingMessages(scope: CoroutineScope, messages: Flow<AppMessageData>) {
        incomingConsumer = messages.onEach { appMessageData ->
            try {
                logger.d { "Got inbound AppMessage, but valid companion app is not running. Nacking..." }

                replyNACK(appMessageData.transactionId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.e(e) { "Error receiving app message from $uuid: ${e.message}" }
            }
        }.launchIn(scope)
    }

    private suspend fun replyNACK(id: UByte) {
        withTimeoutOrNull(3.seconds) {
            device.sendAppMessageResult(AppMessageResult.NACK(id))
        }
    }

    private suspend fun replyACK(id: UByte) {
        withTimeoutOrNull(1000) {
            device.sendAppMessageResult(AppMessageResult.ACK(id))
        }
    }


    companion object {
        private val logger = Logger.Companion.withTag(PebbleKit2::class.simpleName!!)
    }
}

private fun PebbleDictionary.toAppMessageDict(): AppMessageDictionary {
    return map { it.key.toInt() to it.value.value }.toMap()
}

private fun AppMessageDictionary.toPebbleDictionary(): PebbleDictionary {
    return map {
        val key = it.key.toUInt()
        val value = when (val rawValue = it.value) {
            is String -> PebbleDictionaryItem.String(rawValue)
            is UByteArray -> PebbleDictionaryItem.ByteArray(rawValue.toByteArray())
            is ByteArray -> PebbleDictionaryItem.ByteArray(rawValue)
            is Int -> PebbleDictionaryItem.Int32(rawValue)
            is Long -> PebbleDictionaryItem.Int32(rawValue.toInt())
            is ULong -> PebbleDictionaryItem.UInt32(rawValue.toUInt())
            is UInt -> PebbleDictionaryItem.UInt32(rawValue)
            is Short -> PebbleDictionaryItem.Int16(rawValue)
            is UShort -> PebbleDictionaryItem.UInt16(rawValue)
            is Byte -> PebbleDictionaryItem.Int8(rawValue)
            is UByte -> PebbleDictionaryItem.UInt8(rawValue)
            else -> throw IllegalArgumentException("Unsupported type: ${rawValue::class.simpleName}")
        }

        key to value
    }.toMap()

}
