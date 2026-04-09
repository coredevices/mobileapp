package io.rebble.libpebblecommon.connection.bt.ble.transport.impl

import com.juul.kable.Advertisement
import com.juul.kable.Identifier
import com.juul.kable.Scanner
import io.rebble.libpebblecommon.connection.PebbleBleIdentifier
import io.rebble.libpebblecommon.connection.bt.ble.transport.BleScanner
import kotlinx.coroutines.flow.Flow

actual fun kableBleScanner(): BleScanner = KableBleScanner()

internal actual fun createKableAdvertisementsFlow(): Flow<Advertisement> = Scanner {
    preConflate = true
    filters {
        match {
//            if (namePrefix != null) {
//                name = Filter.Name.Prefix(namePrefix)
//            }
        }
    }
}.advertisements

actual fun Identifier.asPebbleBleIdentifier(): PebbleBleIdentifier =
    PebbleBleIdentifier(macAddress = toString())