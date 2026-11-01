package coredevices.libindex.database

import kotlinx.coroutines.flow.StateFlow

interface BasePreferences {
    fun setRingPaired(id: String?)
    val ringPaired: StateFlow<String?>

    fun setRingPairedName(name: String?)
    val ringPairedName: StateFlow<String?>

    fun setRingPairingKeyHash(hash: Int?)
    val ringPairingKeyHash: StateFlow<Int?>

    suspend fun setLastSyncIndex(index: Int?)
    val lastSyncIndex: StateFlow<Int?>

    fun setScanByPairedAddress(enabled: Boolean)
    val scanByPairedAddress: StateFlow<Boolean>
}