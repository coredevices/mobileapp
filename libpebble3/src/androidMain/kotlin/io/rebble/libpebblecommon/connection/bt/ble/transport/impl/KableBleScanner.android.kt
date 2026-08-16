package io.rebble.libpebblecommon.connection.bt.ble.transport.impl

import com.juul.kable.Advertisement
import com.juul.kable.Identifier
import com.juul.kable.Scanner
import io.rebble.libpebblecommon.BleConfig
import io.rebble.libpebblecommon.BleConfigFlow
import io.rebble.libpebblecommon.connection.PebbleBleIdentifier
import io.rebble.libpebblecommon.connection.bt.ble.transport.BleScanner
import kotlinx.coroutines.flow.Flow

actual fun kableBleScanner(bleConfigFlow: BleConfigFlow): BleScanner = KableBleScanner(bleConfigFlow)

internal actual fun createKableAdvertisementsFlow(bleConfig: BleConfig): Flow<Advertisement> = Scanner {
    preConflate = true
    filters {
        match {
            applyServiceFilter(bleConfig)
        }
    }
}.advertisements

actual fun Identifier.asPebbleBleIdentifier(): PebbleBleIdentifier =
    PebbleBleIdentifier(macAddress = toString())

actual fun configureKableCentral(stateRestoration: Boolean) {
    // No-op: CoreBluetooth state restoration is iOS-only.
}
