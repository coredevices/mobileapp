package coredevices.libindex.device

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

actual class IndexPlatformBluetoothAssociations {
    actual val associations: StateFlow<List<IndexAssociation>> get() = throw NotImplementedError()
    actual val bondStateChanges: Flow<IndexBondStateUpdate> get() = throw NotImplementedError()
    actual val associationsReady: Deferred<Unit> get() = throw NotImplementedError()

    actual fun init(bluetoothPermissionChanged: Flow<Boolean>) {
        throw NotImplementedError()
    }

    actual companion object {
        actual val isEnabled: Boolean = false
    }
}