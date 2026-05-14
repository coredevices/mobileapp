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
        scope.launch {
            // collect{} below can throw "Authorization is insufficient" if started too early,
            // and can also fire mid-flow during pairing renegotiation on iOS (MOB-6632). On
            // either, the underlying iOS subscription is dead and re-collecting on the same
            // Flow does nothing. Loop indefinitely: each iteration (re-)subscribes for a fresh
            // Flow and re-reads (Asterix doesn't notify on subscribe, so the read is also how
            // we capture state changes that landed between subscribes). The loop exits when
            // the connection scope is cancelled.
            var attempt = 0
            while (true) {
                val sub = gattClient.subscribeToCharacteristic(PAIRING_SERVICE_UUID, CONNECTIVITY_CHARACTERISTIC)
                if (sub == null) {
                    logger.w { "sub is null" }
                }
                val connectivity = gattClient.readCharacteristic(PAIRING_SERVICE_UUID, CONNECTIVITY_CHARACTERISTIC)
                if (connectivity != null) {
                    _status.value = ConnectivityStatus(connectivity).also {
                        logger.d("connectivity (read): $it")
                    }
                }
                if (attempt == 0) delay(INITIAL_SUBSCRIBE_DELAY)
                sub?.collectConnectivityChanges()
                attempt++
                val backoff = if (attempt == 1) FIRST_RETRY_DELAY else SUBSEQUENT_RETRY_DELAY
                delay(backoff)
                logger.i { "retrying connectivity subscribe + read (attempt $attempt)" }
            }
        }
        true
    } ?: false

    companion object {
        private val INITIAL_SUBSCRIBE_DELAY = 2.seconds
        private val FIRST_RETRY_DELAY = 5.seconds
        private val SUBSEQUENT_RETRY_DELAY = 10.seconds
    }

    private suspend fun Flow<ByteArray>.collectConnectivityChanges() {
        // Returns when the subscription throws (caught) or via cancellation (propagates).
        // collect { } never completes normally for a hot GATT notification subscription.
        try {
            collect {
                _status.value = ConnectivityStatus(it).also {
                    logger.d("connectivity: $it")
                }
            }
        } catch (e: GattStatusException) {
            // Android
            logger.e(e) { "connectivitySub.collect ${e.message}" }
        } catch (e: IOException) {
            // iOS
            logger.e(e) { "connectivitySub.collect ${e.message}" }
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
        val flags = characteristicValue[0]
        connected = flags and 0b1 > 0
        paired = flags and 0b10 > 0
        encrypted = flags and 0b100 > 0
        hasBondedGateway = flags and 0b1000 > 0
        supportsPinningWithoutSlaveSecurity = flags and 0b10000 > 0
        hasRemoteAttemptedToUseStalePairing = flags and 0b100000 > 0
        pairingErrorCode = PairingErrorCode.getByValue(characteristicValue[3])
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
