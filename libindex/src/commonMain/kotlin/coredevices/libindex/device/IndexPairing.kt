package coredevices.libindex.device

import co.touchlab.kermit.Logger
import coredevices.libindex.database.BasePreferences
import coredevices.libindex.database.PrefsCollectionIndexStorage
import coredevices.libindex.database.repository.RingTransferRepository
import io.rebble.libpebblecommon.connection.AppContext
import io.rebble.libpebblecommon.connection.bt.ble.pebble.LEConstants.BOND_BONDED
import io.rebble.libpebblecommon.connection.bt.ble.pebble.LEConstants.BOND_NONE
import io.rebble.libpebblecommon.connection.bt.ble.pebble.LEConstants.UNBOND_REASON_AUTH_CANCELLED
import io.rebble.libpebblecommon.connection.bt.ble.pebble.LEConstants.UNBOND_REASON_AUTH_FAILED
import io.rebble.libpebblecommon.connection.bt.ble.pebble.LEConstants.UNBOND_REASON_AUTH_REJECTED
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.seconds

interface IndexPairing {
    suspend fun pairDevice(device: DiscoveredIndexDevice): IndexPairingResult
}

class BluetoothDevicePairEvent(val device: IndexIdentifier, val bondState: Int, val unbondReason: Int?)

expect suspend fun createBond(context: AppContext, identifier: IndexIdentifier): Boolean

expect fun getBluetoothDevicePairEvents(
    context: AppContext,
    identifier: IndexIdentifier
): Flow<BluetoothDevicePairEvent>


class RealIndexPairing(
    private val context: AppContext,
    private val indexStorage: PrefsCollectionIndexStorage,
    private val transferRepo: RingTransferRepository,
    private val prefs: BasePreferences,
    private val deviceRepo: IndexDeviceManager,
    private val deviceFactory: IndexDeviceFactory
): IndexPairing {
    companion object {
        private val logger = Logger.withTag("RealIndexPairing")
    }
    private suspend fun requestPairing(device: DiscoveredIndexDevice): PairingRequestResult {
        try {
            return withTimeout(30.seconds) {
                if (!createBond(context, device.identifier)) {
                    return@withTimeout PairingRequestResult.CreateBondFailed
                }

                val result = getBluetoothDevicePairEvents(context, device.identifier).first {
                    it.bondState == BOND_BONDED || it.bondState == BOND_NONE
                }
                if (result.bondState == BOND_NONE) {
                    logger.e { "Pairing failed for device ${device.identifier.asString}, bondState=${result.bondState}, unbondReason=${result.unbondReason}" }
                    return@withTimeout when (result.unbondReason) {
                        UNBOND_REASON_AUTH_FAILED -> {
                            logger.i { "Authentication failed when pairing with device ${device.identifier.asString}, assuming already paired" }
                            PairingRequestResult.RingAlreadyPaired
                        }
                        UNBOND_REASON_AUTH_REJECTED -> {
                            logger.i { "Device ${device.identifier.asString} rejected pairing request, assuming already paired" }
                            PairingRequestResult.RingAlreadyPaired
                        }
                        UNBOND_REASON_AUTH_CANCELLED -> {
                            logger.i { "User cancelled pairing request for device ${device.identifier.asString}" }
                            PairingRequestResult.UserRejected
                        }
                        else -> {
                            logger.w { "Unknown pairing failure for device ${device.identifier.asString}, unbondReason=${result.unbondReason}" }
                            PairingRequestResult.Error(Exception("Pairing failed with unbondReason ${result.unbondReason}"))
                        }
                    }
                } else {
                    logger.d { "Pairing succeeded for device ${device.identifier.asString}" }
                    return@withTimeout PairingRequestResult.Paired
                }
            }
        } catch (e: TimeoutCancellationException) {
            logger.e { "Pairing timed out for device ${device.identifier.asString}" }
            return PairingRequestResult.Error(e)
        }

    }
    override suspend fun pairDevice(device: DiscoveredIndexDevice): IndexPairingResult {
        deviceRepo.update(
            deviceFactory.create(
                identifier = device.identifier,
                name = device.name,
                scanResult = IndexScanResult(
                    identifier = device.identifier,
                    name = device.name,
                    rssi = device.rssi
                ),
                isPaired = false,
                pairingState = IndexPairingState.Pairing
            )
        )
        return when (val result = requestPairing(device)) {
            // Already paired = the phone is paired, so the UI should tell the user to unpair from Bluetooth settings / reset ring.
            is PairingRequestResult.UserRejected, is PairingRequestResult.Error, is PairingRequestResult.RingAlreadyPaired, is PairingRequestResult.CreateBondFailed -> {
                deviceRepo.update(
                    deviceFactory.create(
                        identifier = device.identifier,
                        name = device.name,
                        scanResult = IndexScanResult(
                            identifier = device.identifier,
                            name = device.name,
                            rssi = device.rssi
                        ),
                        isPaired = false,
                        pairingState = IndexPairingState.Error(IndexPairingResult.PairingFailure(result))
                    )
                )
                IndexPairingResult.PairingFailure(result)
            }

            is PairingRequestResult.Paired -> {
                withContext(Dispatchers.IO) {
                    indexStorage.setLastSuccessfulCollectionIndex(null)
                    transferRepo.markTransfersAsPreviousIndexIteration()
                    prefs.setRingPaired(device.identifier.asString)
                    withTimeout(3.seconds) {
                        prefs.ringPaired.first { it == device.identifier.asString }
                    }
                }
                IndexPairingResult.Success
            }
        }
    }
}

sealed interface PairingRequestResult {
    object UserRejected: PairingRequestResult
    class Error(val cause: Throwable): PairingRequestResult
    object CreateBondFailed: PairingRequestResult
    object RingAlreadyPaired: PairingRequestResult
    object Paired: PairingRequestResult
}

sealed interface IndexPairingResult {
    object Success: IndexPairingResult
    class PairingFailure(val cause: PairingRequestResult): IndexPairingResult
}