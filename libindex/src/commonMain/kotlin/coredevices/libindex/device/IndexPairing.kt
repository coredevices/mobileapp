package coredevices.libindex.device

import co.touchlab.kermit.Logger
import coredevices.haversine.KMPHaversineHacksDelegate
import coredevices.haversine.KMPHaversineSatelliteManager
import coredevices.libindex.database.BasePreferences
import coredevices.libindex.database.PrefsCollectionIndexStorage
import coredevices.libindex.database.repository.RingTransferRepository
import io.rebble.libpebblecommon.connection.AppContext
import io.rebble.libpebblecommon.connection.bt.ble.pebble.LEConstants.BOND_BONDED
import io.rebble.libpebblecommon.connection.bt.ble.pebble.LEConstants.BOND_NONE
import io.rebble.libpebblecommon.connection.bt.ble.pebble.LEConstants.UNBOND_REASON_AUTH_CANCELLED
import io.rebble.libpebblecommon.connection.bt.ble.pebble.LEConstants.UNBOND_REASON_AUTH_FAILED
import io.rebble.libpebblecommon.connection.bt.ble.pebble.LEConstants.UNBOND_REASON_AUTH_REJECTED
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.seconds

interface IndexPairing {
    suspend fun pairDevice(device: PairableIndexDevice): IndexPairingResult
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
    private val haversineSatelliteManager: KMPHaversineSatelliteManager,
    private val ringHacksDelegate: KMPHaversineHacksDelegate
): IndexPairing {
    companion object {
        private val logger = Logger.withTag("RealIndexPairing")
    }

    /** The image the ring is currently advertising, null once it has no scanned entry left. */
    private fun List<IndexDevice>.imageOf(device: DiscoveredIndexDevice): IndexImage? =
        (getByIDNamePair(device.identifier, device.name) as? DiscoveredIndexDevice)?.currentImage

    /**
     * Check the live state of device image
     */
    private fun isPrimaryImage(device: PairableIndexDevice): Boolean {
        val image = deviceRepo.rings.value.imageOf(device) ?: device.currentImage
        return image == IndexImage.Primary
    }

    /** Runs the bond attempt, aborting it if the ring leaves its primary image while it is open. */
    private suspend fun requestPairing(device: PairableIndexDevice): PairingRequestResult =
        coroutineScope {
            val bonding = async { bond(device) }
            val abort = launch {
                deviceRepo.rings
                    .map { it.imageOf(device) }
                    .first { it != null && it != IndexImage.Primary }
                logger.w { "Device ${device.identifier.asString} left its primary image while pairing, aborting" }
                bonding.cancel()
            }
            try {
                bonding.await()
            } catch (e: CancellationException) {
                ensureActive()
                PairingRequestResult.NotPrimaryImage
            } finally {
                abort.cancel()
            }
        }

    private suspend fun bond(device: PairableIndexDevice): PairingRequestResult {
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
                } else if (!isPrimaryImage(device)) {
                    logger.e { "Device ${device.identifier.asString} took the bonding write on a non-primary image, no bond exists" }
                    return@withTimeout PairingRequestResult.NotPrimaryImage
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
    override suspend fun pairDevice(device: PairableIndexDevice): IndexPairingResult {
        if (!isPrimaryImage(device)) {
            logger.w { "Refusing to pair ${device.identifier.asString}, not on the primary image" }
            return IndexPairingResult.PairingFailure(PairingRequestResult.NotPrimaryImage)
        }
        deviceRepo.setPairingState(device.identifier, device.name, IndexPairingState.Pairing)
        return when (val result = requestPairing(device)) {
            // The scan already rebuilt the row on the image the ring moved to, which carries no
            // pairing state to report a failure against.
            is PairingRequestResult.NotPrimaryImage -> IndexPairingResult.PairingFailure(result)

            // Already paired = the phone is paired, so the UI should tell the user to unpair from Bluetooth settings / reset ring.
            is PairingRequestResult.UserRejected, is PairingRequestResult.Error, is PairingRequestResult.RingAlreadyPaired, is PairingRequestResult.CreateBondFailed -> {
                deviceRepo.setPairingState(
                    device.identifier,
                    device.name,
                    IndexPairingState.Error(IndexPairingResult.PairingFailure(result)),
                )
                IndexPairingResult.PairingFailure(result)
            }

            is PairingRequestResult.Paired -> {
                withContext(Dispatchers.IO) {
                    indexStorage.setLastSuccessfulCollectionIndex(null)
                    transferRepo.markTransfersAsPreviousIndexIteration()
                    try {
                        // Wipe collections in case factory recordings survive, and then tell the delegate we don't need a wipe next time we transfer.
                        haversineSatelliteManager.getSatelliteById(device.identifier.asString)?.let { satellite ->
                            satellite.eraseCollections()
                            logger.d { "Erased collections on satellite ${device.identifier.asString}" }
                            ringHacksDelegate.wipedCollectionsBeforeTransfer(satellite)
                        } ?: logger.w { "Could not find satellite with id ${device.identifier.asString} to erase collections" }
                    } catch (e: Exception) {
                        logger.e(e) { "Failed to erase collections on satellite ${device.identifier.asString}" }
                        return@withContext IndexPairingResult.EraseFailed(e)
                    }
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

    /** The ring was not on (or did not stay on) its primary image, so it cannot bond. */
    object NotPrimaryImage: PairingRequestResult
}

sealed interface IndexPairingResult {
    object Success: IndexPairingResult
    class PairingFailure(val cause: PairingRequestResult): IndexPairingResult
    class EraseFailed(val cause: Throwable): IndexPairingResult
}