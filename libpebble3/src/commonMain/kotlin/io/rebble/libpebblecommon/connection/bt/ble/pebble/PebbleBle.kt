package io.rebble.libpebblecommon.connection.bt.ble.pebble

import co.touchlab.kermit.Logger
import io.rebble.libpebblecommon.BleConfigFlow
import io.rebble.libpebblecommon.connection.ConnectionFailureReason
import io.rebble.libpebblecommon.connection.PebbleBleIdentifier
import io.rebble.libpebblecommon.connection.PebbleConnectionResult
import io.rebble.libpebblecommon.connection.TransportConnector
import io.rebble.libpebblecommon.connection.bt.ble.pebble.LEConstants.TARGET_MTU
import io.rebble.libpebblecommon.connection.bt.ble.ppog.PPoG
import io.rebble.libpebblecommon.connection.bt.ble.ppog.PPoGPacketSender
import io.rebble.libpebblecommon.connection.bt.ble.ppog.PPoGStream
import io.rebble.libpebblecommon.connection.bt.ble.transport.BleScanner
import io.rebble.libpebblecommon.connection.bt.ble.transport.GattConnectionResult
import io.rebble.libpebblecommon.connection.bt.ble.transport.GattConnector
import io.rebble.libpebblecommon.connection.bt.ble.transport.GattServerManager
import io.rebble.libpebblecommon.di.ConnectionCoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

class PebbleBle(
    private val config: BleConfigFlow,
    private val identifier: PebbleBleIdentifier,
    private val scope: ConnectionCoroutineScope,
    private val gattConnector: GattConnector,
    private val ppog: PPoG,
    private val ppogPacketSender: PPoGPacketSender,
    private val pPoGStream: PPoGStream,
    private val connectionParams: ConnectionParams,
    private val mtuParam: Mtu,
    private val connectivity: ConnectivityWatcher,
    private val pairing: PebblePairing,
    private val gattServerManager: GattServerManager,
    private val batteryWatcher: BatteryWatcher,
    private val preConnectScanner: PreConnectScanner,
    private val pPoGReset: PPoGReset,
) : TransportConnector {
    private val logger = Logger.withTag("PebbleBle/${identifier.asString}")

    override suspend fun connect(lastError: ConnectionFailureReason?): PebbleConnectionResult {
        logger.d("connect() reversedPPoG = ${config.value.reversedPPoG}")
        if (!config.value.reversedPPoG) {
            if (!gattServerManager.registerDevice(identifier, pPoGStream.inboundPPoGBytesChannel)) {
                return PebbleConnectionResult.Failed(ConnectionFailureReason.RegisterGattServer)
            }
        }

        if (lastError == ConnectionFailureReason.GattErrorUnknown147) {
            // Try scanning before connecting (this seems to magically allow android to connect,
            // when otherwise it can't).
            preConnectScanner.scanBeforeConnect(identifier)
        }

        val result = gattConnector.connect()
        val device = when (result) {
            is GattConnectionResult.Failure -> {
                return PebbleConnectionResult.Failed(result.reason)
            }

            is GattConnectionResult.Success -> result.client
        }
        val services = device.discoverServices()
        logger.d("services = $services")

        if (!connectionParams.subscribeAndConfigure(device)) {
            // this can happen on some older firmwares (PRF?)
            logger.i("error setting up connection params")
        }
        logger.d("done connectionParams")

        scope.launch {
            mtuParam.mtu.collect { newMtu ->
                logger.d("newMtu = $newMtu")
                ppog.updateMtu(newMtu)
            }
        }
        val mtuResult = mtuParam.update(device, TARGET_MTU)
        if (mtuResult != PebbleConnectionResult.Success) {
            return mtuResult
        }
        logger.d("done mtu update")

        if (!connectivity.subscribe(device)) {
            logger.d("failed to subscribe to connectivity")
            return PebbleConnectionResult.Failed(ConnectionFailureReason.SubscribeConnectivity)
        }
        logger.d("subscribed connectivity d")
        val connectionStatus = withTimeoutOrNull(CONNECTIVITY_UPDATE_TIMEOUT) {
            connectivity.status.first()
        }
        if (connectionStatus == null) {
            logger.d("failed to get connection status")
            return PebbleConnectionResult.Failed(ConnectionFailureReason.ConnectionStatus)
        }
        logger.d("connectionStatus = $connectionStatus")
        batteryWatcher.subscribe(device)

        val needToPair = if (connectionStatus.paired) {
            if (device.isBonded()) {
                logger.d("already paired")
                false
            } else {
                logger.d("watch thinks it is paired, phone does not")
                true
            }
        } else {
            if (device.isBonded()) {
                logger.d("phone thinks it is paired, watch does not")
                true
            } else {
                logger.d("needs pairing")
                true
            }
        }

        if (needToPair) {
            val pairingResult =
                pairing.requestBlePairing(device, connectionStatus, connectivity.status, identifier)
            if (pairingResult != null) {
                PebbleConnectionResult.Failed(pairingResult)
            }
        }

        if (ppogPacketSender is PpogClient) {
            // TODO do this better if it works
            // FIXME
            ppogPacketSender.init(device)
        }

        val requestedPpogResetViaCharacteristic = pPoGReset.triggerPpogResetIfNeeded(device)
        logger.d { "requestedPpogResetViaCharacteristic = $requestedPpogResetViaCharacteristic" }

        ppog.run(requestedPpogResetViaCharacteristic)
        return PebbleConnectionResult.Success
    }

    override suspend fun disconnect() {
        ppog.close()
        gattConnector.disconnect()
        gattServerManager.unregisterDevice(identifier)
    }

    override val disconnected = gattConnector.disconnected

    companion object {
        private val CONNECTIVITY_UPDATE_TIMEOUT = 10.seconds
    }
}

class PreConnectScanner(
    private val bleScanner: BleScanner,
    private val identifier: PebbleBleIdentifier,
) {
    private val logger = Logger.withTag("PreConnectScanner")

    suspend fun scanBeforeConnect(identifier: PebbleBleIdentifier) {
        logger.d { "scanBeforeConnect(): $identifier" }
        val scanResults = bleScanner.scan()
        val found = withTimeoutOrNull(SCAN_TIMEOUT_MS) {
            scanResults.first {
                it.identifier == identifier
            }
        }
        logger.d { "scanBeforeConnect: found = $found" }
    }

    companion object {
        private val SCAN_TIMEOUT_MS = 10.seconds
    }
}

expect val SERVER_META_RESPONSE: ByteArray