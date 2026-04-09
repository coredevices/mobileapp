package io.rebble.libpebblecommon.connection.bt.ble.transport.impl

import com.juul.kable.Advertisement
import com.juul.kable.Identifier
import com.juul.kable.Scanner
import io.rebble.libpebblecommon.connection.PebbleBleIdentifier
import io.rebble.libpebblecommon.connection.bt.ble.transport.BleScanner
import kotlinx.coroutines.flow.Flow
import kotlin.uuid.Uuid

actual fun kableBleScanner(): BleScanner = KableBleScanner()

internal actual fun createKableAdvertisementsFlow(): Flow<Advertisement> = Scanner {
    filters {
        match { }
    }
}.advertisements

actual fun Identifier.asPebbleBleIdentifier(): PebbleBleIdentifier = PebbleBleIdentifier(uuid = Uuid.parse(toString()))