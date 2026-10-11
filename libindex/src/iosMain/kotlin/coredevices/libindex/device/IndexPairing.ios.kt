package coredevices.libindex.device

import co.touchlab.kermit.Logger
import com.juul.kable.Peripheral
import com.juul.kable.WriteType
import io.rebble.libpebblecommon.connection.AppContext
import io.rebble.libpebblecommon.connection.bt.ble.pebble.LEConstants.BOND_BONDED
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

private val haversineUuid = Uuid.parse("607B5C9B-3700-4E94-F44A-2DF900BCB0C3")
private val telestoDataChannel = Uuid.parse("DAAD3D52-237C-90A7-B54B-8854A134D801")
private val logger = Logger.withTag("IndexPairing")

private const val BOND_ATTEMPTS = 3
// Must stay below RealIndexPairing's overall pairing timeout so a hung connect surfaces as
// CreateBondFailed (which drives the recovery dialog) rather than a bare timeout.
private val BOND_TIMEOUT = 25.seconds

actual suspend fun createBond(
    context: AppContext,
    identifier: IndexIdentifier
): Boolean {
    val bonded = withTimeoutOrNull(BOND_TIMEOUT) {
        repeat(BOND_ATTEMPTS) { attempt ->
            if (bondAttempt(identifier)) return@withTimeoutOrNull true
            logger.e { "createBond() attempt ${attempt + 1}/$BOND_ATTEMPTS failed" }
            delay(500.milliseconds)
        }
        false
    }
    when (bonded) {
        null -> logger.e { "createBond() timed out after $BOND_TIMEOUT" }
        false -> logger.e { "createBond() failed after $BOND_ATTEMPTS attempts" }
        true -> {}
    }
    return bonded == true
}

/** Writing to the Telesto channel requires encryption, which is what makes iOS create the bond. */
private suspend fun bondAttempt(identifier: IndexIdentifier): Boolean {
    val peripheral = Peripheral(Uuid.parse(identifier.asPlatformAddress))
    try {
        peripheral.connect()
        val channel = peripheral.services.first()
            ?.firstOrNull { it.serviceUuid == haversineUuid }
            ?.characteristics
            ?.firstOrNull { it.characteristicUuid == telestoDataChannel }
            ?: return false
        peripheral.write(channel, byteArrayOf(0x00), writeType = WriteType.WithResponse)
        return true
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        logger.e(e) { "Bond attempt failed: ${e.message}" }
        return false
    } finally {
        withContext(NonCancellable) {
            peripheral.disconnect()
            peripheral.close()
        }
    }
}

actual fun getBluetoothDevicePairEvents(
    context: AppContext,
    identifier: IndexIdentifier
): Flow<BluetoothDevicePairEvent> {
    return flowOf(BluetoothDevicePairEvent(identifier, bondState = BOND_BONDED, unbondReason = null))
}
