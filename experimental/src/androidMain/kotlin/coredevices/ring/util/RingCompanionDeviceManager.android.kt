package coredevices.ring.util

import android.annotation.SuppressLint
import android.bluetooth.le.ScanFilter
import android.companion.AssociationInfo
import android.companion.AssociationRequest
import android.companion.BluetoothLeDeviceFilter
import android.companion.CompanionDeviceManager
import android.content.Context
import android.content.IntentSender
import android.os.Build
import android.os.ParcelUuid
import co.touchlab.kermit.Logger
import coredevices.haversine.KMPHaversineSatellite
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.UUID
import kotlin.coroutines.resume

actual class RingCompanionDeviceManager actual constructor(scope: CoroutineScope): KoinComponent {
    companion object {
        private val logger = Logger.withTag(RingCompanionDeviceManager::class.simpleName!!)
    }
    private val context: Context by inject()
    private val companionDeviceManager = context.getSystemService(Context.COMPANION_DEVICE_SERVICE) as CompanionDeviceManager

    private val _associations = MutableStateFlow(getAssociations())
    actual val associations = _associations.asStateFlow()

    private fun getAssociations(): List<DeviceAssociation> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            companionDeviceManager.myAssociations.mapNotNull {
                if (it.deviceProfile == AssociationRequest.DEVICE_PROFILE_WATCH) {
                    return@mapNotNull null // Ignore watches
                }
                it.deviceMacAddress?.let { mac ->
                    DeviceAssociation(
                        id = mac.toString().uppercase().replace(":", ""),
                        name = it.displayName?.toString() ?: mac.toOuiString()
                    )
                }

            }
        } else {
            companionDeviceManager.associations.map {
                DeviceAssociation(
                    id = it.toString().uppercase().replace(":", ""),
                    name = it
                )
            }
        }
    }

    private fun refreshAssociations() {
        _associations.value = getAssociations()
    }

    private fun associationExists(satellite: KMPHaversineSatellite): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            companionDeviceManager.myAssociations.any {
                it.deviceMacAddress?.toString()?.replace(":", "")?.uppercase() == satellite.id
            }
        } else {
            companionDeviceManager.associations.any {
                it.replace(":", "").uppercase() == satellite.id
            }
        }
    }

    actual suspend fun openPairingPicker(): CompanionRegisterResult {
        val uuid16 = UUID.fromString(ringServiceUuid16)
        val uuid128 = UUID.fromString(ringServiceUuid128)
        val deviceFilter = BluetoothLeDeviceFilter.Builder()
            .setScanFilter(
                ScanFilter.Builder()
                    .setServiceUuid(ParcelUuid(uuid16))
                    .build()
            )
            .build()
        val deviceFilter128 = BluetoothLeDeviceFilter.Builder()
            .setScanFilter(
                ScanFilter.Builder()
                    .setServiceUuid(ParcelUuid(uuid128))
                    .build()
            )
            .build()
        val associationRequest = AssociationRequest.Builder()
            .addDeviceFilter(deviceFilter)
            .addDeviceFilter(deviceFilter128)
            .build()
        return suspendCancellableCoroutine { completion ->
            logger.d { "Opening pairing picker" }
            companionDeviceManager.associate(associationRequest, object : CompanionDeviceManager.Callback() {
                override fun onFailure(error: CharSequence?) {
                    logger.e { "Association failed: $error" }
                    refreshAssociations()
                    completion.resume(CompanionRegisterResult.Failure(error.toString()))
                }

                @SuppressLint("MissingPermission")
                override fun onAssociationCreated(associationInfo: AssociationInfo) {
                    logger.d { "Association created: $associationInfo" }
                    var id: String? = null
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        associationInfo.associatedDevice?.bluetoothDevice?.createBond()
                            ?: associationInfo.associatedDevice?.bleDevice?.device?.createBond()
                        val address = associationInfo.associatedDevice?.bleDevice?.device?.address
                        id = address?.replace(":", "")?.uppercase()
                    }
                    refreshAssociations()
                    completion.resume(CompanionRegisterResult.Success(id))
                }

                override fun onAssociationPending(intentSender: IntentSender) {
                    logger.d { "Association pending" }
                    intentSender.sendIntent(context, 0, null, null, null)
                }
            }, null)
        }
    }

    actual suspend fun unregister(satellite: KMPHaversineSatellite) {
        if (!associationExists(satellite)) {
            return
        }
        val mac = idToMac(satellite.id)
        companionDeviceManager.disassociate(mac)
        logger.d { "Disassociated: $mac" }
        refreshAssociations()
    }

    actual suspend fun unregisterAll() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            companionDeviceManager.myAssociations.forEach {
                if (it.displayName?.toString()?.startsWith("CoreRing") == false) {
                    return@forEach // Ignore other associations
                }
                it.deviceMacAddress?.let { mac ->
                    companionDeviceManager.disassociate(mac.toString())
                    logger.d { "Disassociated: $mac" }
                }
            }
        } else {
            companionDeviceManager.associations.forEach {
                companionDeviceManager.disassociate(it)
                logger.d { "Disassociated: $it" }
            }
        }
        refreshAssociations()
    }

    private fun idToMac(id: String) = buildString {
        for (i in id.indices step 2) {
            append(id.substring(i, i + 2))
            if (i < id.length - 2) append(":")
        }
    }
}