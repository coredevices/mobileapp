package io.rebble.libpebblecommon.connection.bt.ble.transport.impl

import com.juul.kable.Advertisement
import com.juul.kable.Identifier
import io.rebble.libpebblecommon.connection.PebbleBleIdentifier
import io.rebble.libpebblecommon.connection.bt.ble.transport.BleScanner
import kotlinx.coroutines.flow.Flow

actual fun kableBleScanner(): BleScanner = TODO("Not yet implemented")

internal actual fun createKableAdvertisementsFlow(): Flow<Advertisement> = TODO("Not yet implemented")

actual fun Identifier.asPebbleBleIdentifier(): PebbleBleIdentifier {
    TODO("Not yet implemented")
}