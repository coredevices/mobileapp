package coredevices.libindex

import coredevices.libindex.device.IndexDevice
import coredevices.libindex.device.IndexDeviceManager
import coredevices.libindex.device.IndexPlatformBluetoothAssociations
import coredevices.libindex.device.RealScanning
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface LibIndex : Scanning, Rings {
    fun init(bluetoothPermissionChanged: Flow<Boolean>)
}

typealias IndexDevices = StateFlow<List<IndexDevice>>

interface Scanning {
    val isScanning: StateFlow<Boolean>
    fun startScan()
    fun stopScan()
}

interface Rings {
    val rings: IndexDevices
}

class RealLibIndex(
    private val scanning: RealScanning,
    private val deviceRepo: IndexDeviceManager,
    private val associations: IndexPlatformBluetoothAssociations?
): LibIndex, Scanning by scanning, Rings by deviceRepo {
    override fun init(bluetoothPermissionChanged: Flow<Boolean>) {
        if (IndexPlatformBluetoothAssociations.isEnabled) {
            associations?.init(bluetoothPermissionChanged)
        }
        deviceRepo.init()
    }
}