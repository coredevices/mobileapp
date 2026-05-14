package io.rebble.libpebblecommon.connection

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import co.touchlab.kermit.Logger
import io.rebble.libpebblecommon.connection.bt.classic.PEBBLE_NAME_REGEX
import io.rebble.libpebblecommon.database.dao.KnownWatchDao
import io.rebble.libpebblecommon.database.entity.KnownWatchItem
import io.rebble.libpebblecommon.database.entity.TransportType

private val logger = Logger.withTag("BondedWatchSeeder")

@SuppressLint("MissingPermission")
internal actual suspend fun seedBondedWatches(
    appContext: AppContext,
    knownWatchDao: KnownWatchDao,
): List<KnownWatchItem>? {
    val btManager = appContext.context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    val adapter = btManager?.adapter
    if (adapter == null) {
        logger.d { "No BluetoothAdapter; nothing to seed" }
        return emptyList()
    }

    val bonded = try {
        adapter.bondedDevices ?: emptySet()
    } catch (e: SecurityException) {
        logger.w(e) { "Missing BLUETOOTH_CONNECT; will retry next launch" }
        return null
    }

    val existing = knownWatchDao.knownWatches().map { it.transportIdentifier }.toHashSet()
    val inserted = mutableListOf<KnownWatchItem>()

    for (device in bonded) {
        val name = device.name ?: continue

        if (!PEBBLE_NAME_REGEX.matches(name)) continue

        val transportType = when (device.type) {
            BluetoothDevice.DEVICE_TYPE_LE -> TransportType.BluetoothLe
            BluetoothDevice.DEVICE_TYPE_DUAL, BluetoothDevice.DEVICE_TYPE_CLASSIC ->
                TransportType.BluetoothClassic
            else -> {
                logger.d { "Skipping bonded Pebble $name (${device.address}) with unknown type=${device.type}" }
                continue
            }
        }

        val address = device.address.uppercase()
        if (address in existing) {
            logger.d { "Bonded Pebble $name ($address) already in DB; skipping" }
            continue
        }

        val item = KnownWatchItem(
            transportIdentifier = address,
            transportType = transportType,
            name = name,
            runningFwVersion = UNKNOWN_WATCH_SERIAL_OR_VERSION,
            serial = UNKNOWN_WATCH_SERIAL_OR_VERSION,
            connectGoal = false,
        )
        try {
            knownWatchDao.insertOrUpdate(item)
            inserted += item
            logger.i { "Seeded bonded Pebble $name ($address) as $transportType" }
        } catch (e: Exception) {
            logger.w(e) { "Failed to insert seeded bonded Pebble $name ($address)" }
        }
    }

    return inserted
}
