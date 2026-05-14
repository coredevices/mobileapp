package coredevices.libindex.device

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

expect class IndexPlatformBluetoothAssociations {
    companion object {
        val isEnabled: Boolean
    }

    val associations: StateFlow<List<IndexAssociation>>
    val bondStateChanges: Flow<IndexBondStateUpdate>
    val associationsReady: Deferred<Unit>
    fun init(bluetoothPermissionChanged: Flow<Boolean>)
}

data class IndexAssociation(
    val deviceName: String,
    val identifier: IndexIdentifier
)

data class IndexBondStateUpdate(
    val name: String?,
    val state: IndexBondState,
    val identifier: IndexIdentifier
)

enum class IndexBondState {
    Bonded,
    Bonding,
    NotBonded
}