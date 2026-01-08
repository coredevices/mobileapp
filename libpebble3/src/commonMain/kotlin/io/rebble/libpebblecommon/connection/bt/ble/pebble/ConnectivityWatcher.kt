package io.rebble.libpebblecommon.connection.bt.ble.pebble

import co.touchlab.kermit.Logger
import com.juul.kable.GattStatusException
import io.rebble.libpebblecommon.connection.bt.ble.pebble.LEConstants.UUIDs.CONNECTIVITY_CHARACTERISTIC
import io.rebble.libpebblecommon.connection.bt.ble.pebble.LEConstants.UUIDs.PAIRING_SERVICE_UUID
import io.rebble.libpebblecommon.connection.bt.ble.transport.ConnectedGattClient
import io.rebble.libpebblecommon.di.ConnectionCoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.io.IOException
import kotlin.experimental.and
import kotlin.time.Duration.Companion.seconds

/**
 * Talks to watch connectivity characteristic describing pair status, connection, and other parameters
 */
class ConnectivityWatcher(private val scope: ConnectionCoroutineScope) {
    private val logger = Logger.withTag("ConnectivityWatcher")
    private val _status = MutableStateFlow<ConnectivityStatus?>(null)
    val status = _status.asStateFlow().filterNotNull()

    suspend fun subscribe(gattClient: ConnectedGattClient): Boolean = withTimeoutOrNull(10.seconds) {
        val connectivitySub = gattClient.subscribeToCharacteristic(PAIRING_SERVICE_UUID, CONNECTIVITY_CHARACTERISTIC)
        if (connectivitySub == null) {
            logger.e("connectivitySub is null")
            return@withTimeoutOrNull false
        }
        scope.launch {
            // There is some weird timing thing where this throws a GATT error "Authorization is
            // insufficient", if done too early.
            delay(2.seconds)
            if (!connectivitySub.collectConnectivityChanges()) {
                delay(5.seconds)
                logger.i { "retrying connectivitySub.collectConnectivityChanges()" }
                if (!connectivitySub.collectConnectivityChanges()) {
                    delay(10.seconds)
                    logger.i { "retrying connectivitySub.collectConnectivityChanges() (last attempt)" }
                    if (!connectivitySub.collectConnectivityChanges()) {
                        logger.w { "failed to subscribe to connectivity changes after retries" }
                    }
                }
            }
        }
        // Asterix doesn't notify on subscription right now - so we need to do an explicit read
        val connectivity =
            gattClient.readCharacteristic(PAIRING_SERVICE_UUID, CONNECTIVITY_CHARACTERISTIC)
        if (connectivity == null) {
            logger.d("connectivity == null")
            return@withTimeoutOrNull true
        }
        _status.value = ConnectivityStatus(connectivity)
        true
    } ?: false

    private suspend fun Flow<ByteArray>.collectConnectivityChanges(): Boolean {
        try {
            collect {
                _status.value = ConnectivityStatus(it).also {
                    logger.d("connectivity: $it")
                }
            }
            return true
        } catch (e: GattStatusException) {
            // Android
            logger.e(e) { "connectivitySub.collect ${e.message}" }
            return false
        } catch (e: IOException) {
            // iOS
            logger.e(e) { "connectivitySub.collect ${e.message}" }
            return false
        }
    }
}

class ConnectivityStatus(characteristicValue: ByteArray) {
    val connected: Boolean
    val paired: Boolean
    val encrypted: Boolean
    val hasBondedGateway: Boolean
    val supportsPinningWithoutSlaveSecurity: Boolean
    val hasRemoteAttemptedToUseStalePairing: Boolean
    val pairingErrorCode: PairingErrorCode

    init {
        // Handle empty array (can happen with recovery firmware)
        if (characteristicValue.isEmpty()) {
            connected = false
            paired = false
            encrypted = false
            hasBondedGateway = false
            supportsPinningWithoutSlaveSecurity = false
            hasRemoteAttemptedToUseStalePairing = false
            pairingErrorCode = PairingErrorCode.NO_ERROR
        } else {
            val flags = characteristicValue[0]
            connected = flags and 0b1 > 0
            paired = flags and 0b10 > 0
            encrypted = flags and 0b100 > 0
            hasBondedGateway = flags and 0b1000 > 0
            supportsPinningWithoutSlaveSecurity = flags and 0b10000 > 0
            hasRemoteAttemptedToUseStalePairing = flags and 0b100000 > 0
            pairingErrorCode = if (characteristicValue.size > 3) {
                PairingErrorCode.getByValue(characteristicValue[3])
            } else {
                PairingErrorCode.NO_ERROR
            }
        }
    }

    override fun toString(): String =
        "< ConnectivityStatus connected = ${connected} paired = ${paired} encrypted = ${encrypted} hasBondedGateway = ${hasBondedGateway} supportsPinningWithoutSlaveSecurity = ${supportsPinningWithoutSlaveSecurity} hasRemoteAttemptedToUseStalePairing = ${hasRemoteAttemptedToUseStalePairing} pairingErrorCode = ${pairingErrorCode}>"
}

enum class PairingErrorCode(val value: Byte) {
    NO_ERROR(0),
    PASSKEY_ENTRY_FAILED(1),
    OOB_NOT_AVAILABLE(2),
    AUTHENTICATION_REQUIREMENTS(3),
    CONFIRM_VALUE_FAILED(4),
    PAIRING_NOT_SUPPORTED(5),
    ENCRYPTION_KEY_SIZE(6),
    COMMAND_NOT_SUPPORTED(7),
    UNSPECIFIED_REASON(8),
    REPEATED_ATTEMPTS(9),
    INVALID_PARAMETERS(10),
    DHKEY_CHECK_FAILED(11),
    NUMERIC_COMPARISON_FAILED(12),
    BR_EDR_PAIRING_IN_PROGRESS(13),
    CROSS_TRANSPORT_KEY_DERIVATION_NOT_ALLOWED(14),
    UNKNOWN_ERROR(255u.toByte());

    companion object {
        fun getByValue(value: Byte): PairingErrorCode {
            val v = values().firstOrNull { it.value == value }
            return v ?: UNKNOWN_ERROR
        }
    }
}
