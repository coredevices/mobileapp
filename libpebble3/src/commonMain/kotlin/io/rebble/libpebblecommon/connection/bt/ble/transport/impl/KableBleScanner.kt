package io.rebble.libpebblecommon.connection.bt.ble.transport.impl

import com.juul.kable.Advertisement
import com.juul.kable.Identifier
import io.rebble.libpebblecommon.connection.BleScanResult
import io.rebble.libpebblecommon.connection.PebbleBleIdentifier
import io.rebble.libpebblecommon.connection.bt.ble.transport.BleScanner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull

expect fun kableBleScanner(): BleScanner

internal expect fun createKableAdvertisementsFlow(): Flow<Advertisement>

class KableBleScanner : BleScanner {
    override fun scan(): Flow<BleScanResult> {
        return createKableAdvertisementsFlow()
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
