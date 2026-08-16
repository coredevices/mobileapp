package coredevices.libindex.device

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

expect class IndexPlatformBluetoothAssociations {
    companion object {
        val isEnabled: Boolean
    }

    /** Null until the bond list has actually been read; an empty list means nothing is bonded. */
    val associations: StateFlow<List<IndexAssociation>?>
    val bondStateChanges: Flow<IndexBondStateUpdate>
    fun init(bluetoothPermissionChanged: Flow<Boolean>)

    /**
     * Warns when there are no CDM associations for Android, since we lose privileges to e.g.
     * send alarm create intent in the background.
     */
    fun warnIfNoCompanionAssociations(): Unit
}

val IndexPlatformBluetoothAssociations.Companion.REQUEST_URI_HOST: String
    get() = "register-index-companion"

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