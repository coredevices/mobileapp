package coredevices.ring.util

import coredevices.haversine.KMPHaversineSatellite
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

internal val ringServiceUuid16 = "0000" +  /* 16-bit id: */"FCC9" +  /* 16-bit base suffix: */"-0000-1000-8000-00805F9B34FB"
internal val ringServiceUuid128 = "607B5C9B-3700-4E94-F44A-2DF900BCB0C3"

expect class RingCompanionDeviceManager(scope: CoroutineScope) {
    val associations: StateFlow<List<DeviceAssociation>>
    suspend fun openPairingPicker(): CompanionRegisterResult
    suspend fun unregister(satellite: KMPHaversineSatellite)
    suspend fun unregisterAll()
}

data class DeviceAssociation(
    val id: String,
    val name: String
)

sealed class CompanionRegisterResult {
    data class Success(val id: String?) : CompanionRegisterResult()
    data class Failure(val error: String) : CompanionRegisterResult()
}