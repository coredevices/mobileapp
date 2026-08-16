package io.rebble.libpebblecommon.connection.bt.ble.transport.impl

import co.touchlab.kermit.Logger
import com.juul.kable.Advertisement
import com.juul.kable.CentralManager
import com.juul.kable.Identifier
import com.juul.kable.Scanner
import io.rebble.libpebblecommon.BleConfig
import io.rebble.libpebblecommon.BleConfigFlow
import io.rebble.libpebblecommon.connection.PebbleBleIdentifier
import io.rebble.libpebblecommon.connection.bt.ble.transport.BleScanner
import kotlinx.coroutines.flow.Flow
import kotlin.uuid.Uuid

actual fun kableBleScanner(bleConfigFlow: BleConfigFlow): BleScanner = KableBleScanner(bleConfigFlow)

actual fun configureKableCentral(stateRestoration: Boolean) {
    // Opts the watch connection into CoreBluetooth state preservation/restoration
    // (CBCentralManagerOptionRestoreIdentifierKey). Without it iOS never relaunches us for
    // central-side events, so a jetsammed app stays disconnected until the user opens it.
    // The GATT server side has had this since it was written (GattServer.ios.kt, "ppog-server").
    // Leaving it off means passing no options at all, which is what Kable did before this existed.
    if (!stateRestoration) return
    try {
        CentralManager.configure { this.stateRestoration = true }
    } catch (e: IllegalStateException) {
        Logger.w("Kable central already initialised; state restoration not enabled", e)
    }
}

internal actual fun createKableAdvertisementsFlow(bleConfig: BleConfig): Flow<Advertisement> = Scanner {
    filters {
        match {
            applyServiceFilter(bleConfig)
        }
    }
}.advertisements

actual fun Identifier.asPebbleBleIdentifier(): PebbleBleIdentifier = PebbleBleIdentifier(uuid = Uuid.parse(toString()))