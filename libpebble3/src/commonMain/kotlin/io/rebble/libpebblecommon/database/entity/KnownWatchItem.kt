package io.rebble.libpebblecommon.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import io.rebble.libpebblecommon.connection.PebbleBleIdentifier
import io.rebble.libpebblecommon.connection.PebbleBtClassicIdentifier
import io.rebble.libpebblecommon.connection.PebbleIdentifier
import io.rebble.libpebblecommon.connection.PebbleSocketIdentifier
import io.rebble.libpebblecommon.connection.asPebbleBleIdentifier
import io.rebble.libpebblecommon.connection.asPebbleBtClassicIdentifier
import io.rebble.libpebblecommon.database.MillisecondInstant
import io.rebble.libpebblecommon.database.entity.TransportType.BluetoothClassic
import io.rebble.libpebblecommon.database.entity.TransportType.BluetoothLe
import io.rebble.libpebblecommon.database.entity.TransportType.Socket
import io.rebble.libpebblecommon.metadata.WatchColor
import io.rebble.libpebblecommon.packets.ProtocolCapsFlag

@Entity
data class KnownWatchItem(
    @PrimaryKey val transportIdentifier: String,
    val transportType: TransportType,
    val name: String,
    val runningFwVersion: String,
    val serial: String,
    val connectGoal: Boolean,
    val lastConnected: MillisecondInstant? = null,
    val watchType: String? = null,
    val color: WatchColor? = null,
    val nickname: String? = null,
    val btClassicMacAddress: String? = null,
    val capabilities: Set<ProtocolCapsFlag>? = null,
)

enum class TransportType {
    BluetoothLe,
    BluetoothClassic,
    Socket,
}

fun KnownWatchItem.identifier(): PebbleIdentifier = when (transportType) {
    BluetoothLe -> transportIdentifier.asPebbleBleIdentifier()
    BluetoothClassic -> transportIdentifier.asPebbleBtClassicIdentifier()
    Socket -> PebbleSocketIdentifier(transportIdentifier)
}

fun PebbleIdentifier.type(): TransportType = when (this) {
    is PebbleBleIdentifier -> BluetoothLe
    is PebbleBtClassicIdentifier -> BluetoothClassic
    is PebbleSocketIdentifier -> Socket
    // Can't use a sealed interface because expect/actual
    else -> error("unknown identifier type: $this")
}