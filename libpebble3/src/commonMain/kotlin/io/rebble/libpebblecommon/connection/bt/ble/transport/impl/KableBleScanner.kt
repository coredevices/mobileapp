package io.rebble.libpebblecommon.connection.bt.ble.transport.impl

import com.juul.kable.Advertisement
import com.juul.kable.FilterPredicateBuilder
import com.juul.kable.Identifier
import io.rebble.libpebblecommon.BleConfig
import io.rebble.libpebblecommon.BleConfigFlow
import io.rebble.libpebblecommon.connection.BleScanResult
import io.rebble.libpebblecommon.connection.PebbleBleIdentifier
import io.rebble.libpebblecommon.connection.bt.ble.pebble.LEConstants.UUIDs.PAIRING_SERVICE_UUID
import io.rebble.libpebblecommon.connection.bt.ble.transport.BleScanner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull

expect fun kableBleScanner(bleConfigFlow: BleConfigFlow): BleScanner

/**
 * Must run before anything touches Kable's shared central manager (any scan,
 * [com.juul.kable.Peripheral] creation, or Bluetooth-state collection) — Kable
 * throws if its central has already been initialised.
 */
expect fun configureKableCentral(stateRestoration: Boolean)

internal expect fun createKableAdvertisementsFlow(bleConfig: BleConfig): Flow<Advertisement>

fun FilterPredicateBuilder.applyServiceFilter(bleConfig: BleConfig) {
    if (bleConfig.filterScanResultsByUuid) {
        services = listOf(PAIRING_SERVICE_UUID)
    }
}

class KableBleScanner(
    private val bleConfigFlow: BleConfigFlow,
) : BleScanner {
    override fun scan(): Flow<BleScanResult> {
        return createKableAdvertisementsFlow(bleConfigFlow.value)
            .mapNotNull {
                val name = it.name ?: return@mapNotNull null
                val manufacturerData = it.manufacturerData ?: return@mapNotNull null
                BleScanResult(
                    identifier = it.identifier.asPebbleBleIdentifier(),
                    name = name,
                    rssi = it.rssi,
                    manufacturerData = manufacturerData
                )
            }
    }
}

expect fun Identifier.asPebbleBleIdentifier(): PebbleBleIdentifier
