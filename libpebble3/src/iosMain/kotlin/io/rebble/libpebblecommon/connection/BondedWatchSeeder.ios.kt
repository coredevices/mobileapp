package io.rebble.libpebblecommon.connection

import io.rebble.libpebblecommon.database.dao.KnownWatchDao
import io.rebble.libpebblecommon.database.entity.KnownWatchItem

// iOS/CoreBluetooth has no equivalent of Android's BluetoothAdapter.getBondedDevices(), and
// a reinstall invalidates per-app CBPeripheral UUIDs, so we can't recover prior pairings here.
internal actual suspend fun seedBondedWatches(
    appContext: AppContext,
    knownWatchDao: KnownWatchDao,
): List<KnownWatchItem>? = emptyList()
