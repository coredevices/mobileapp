package io.rebble.libpebblecommon.connection

import io.rebble.libpebblecommon.database.dao.KnownWatchDao
import io.rebble.libpebblecommon.database.entity.KnownWatchItem

/**
 * One-shot seeder that looks for Pebble watches already paired at the OS level and inserts
 * stub [KnownWatchItem] rows for any that aren't already in the DB, so users who reinstall
 * the app can see and reconnect to previously-paired watches.
 *
 * Returns the newly-inserted items (may be empty), or `null` if seeding couldn't run (e.g.
 * BT permission missing) and the caller should retry next launch. Implemented on Android via
 * `BluetoothAdapter.getBondedDevices()`; iOS/JVM return empty (CoreBluetooth provides no
 * equivalent enumeration API, and a reinstall would invalidate the per-app peripheral UUIDs
 * anyway).
 */
internal expect suspend fun seedBondedWatches(
    appContext: AppContext,
    knownWatchDao: KnownWatchDao,
): List<KnownWatchItem>?

const val UNKNOWN_WATCH_SERIAL_OR_VERSION = "unknown"