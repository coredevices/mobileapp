package io.rebble.libpebblecommon.connection.bt.ble.transport.impl

import android.bluetooth.BluetoothGatt
import co.touchlab.kermit.Logger
import com.juul.kable.AndroidPeripheral
import com.juul.kable.Peripheral
import io.rebble.libpebblecommon.connection.PebbleBleIdentifier
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

actual fun peripheralFromIdentifier(identifier: PebbleBleIdentifier, name: String): Peripheral?
 = Peripheral(identifier.macAddress)

actual suspend fun Peripheral.requestMtuNative(mtu: Int): Int {
    if (this is AndroidPeripheral) {
        return this.requestMtu(mtu)
    }
    throw IllegalStateException("Not an AndroidPeripheral")
}

private val LOG_TAG = "Peripheral.refreshServicesNative"
private val REDISCOVER_TIMEOUT = 10.seconds
private const val KABLE_ON_SERVICE_CHANGED_CLASS = "com.juul.kable.gatt.OnServiceChanged"

actual suspend fun Peripheral.refreshServicesNative(): Boolean {
    val logger = Logger.withTag(LOG_TAG)
    if (this !is AndroidPeripheral) {
        logger.w("Not an AndroidPeripheral, skipping refresh")
        return false
    }
    val connection = extractConnection(this) ?: run {
        logger.w("Could not extract Kable Connection")
        return false
    }
    val gatt = extractField<BluetoothGatt>(connection, "gatt") ?: run {
        logger.w("Could not extract BluetoothGatt")
        return false
    }
    val callback = extractField<Any>(connection, "callback") ?: run {
        logger.w("Could not extract Kable Callback")
        return false
    }
    @Suppress("UNCHECKED_CAST")
    val onServiceChangedChannel = extractField<Any>(callback, "onServiceChanged") as? Channel<Any>
    if (onServiceChangedChannel == null) {
        logger.w("Could not extract Kable Callback.onServiceChanged channel")
        return false
    }
    val onServiceChangedInstance = kableOnServiceChangedInstance() ?: run {
        logger.w("Could not locate Kable OnServiceChanged singleton")
        return false
    }

    // Snapshot the current services list so we can detect the re-emission by
    // referential inequality — Kable allocates a new list every time
    // Connection.discoverServices() finishes.
//    val before = services.value

    // (1) Blow away Android's per-app service cache for this device.
    if (!invokeGattRefresh(gatt)) {
        logger.w("BluetoothGatt.refresh() returned false")
        return false
    }

    // (2) Nudge Kable's onServiceChanged channel; its Connection has a
    // coroutine listening on it that re-runs discoverServices() and updates
    // the services StateFlow.
    if (onServiceChangedChannel.trySend(onServiceChangedInstance).isFailure) {
        logger.w("Failed to send OnServiceChanged into Kable channel")
        return false
    }

    // (3) Wait for Kable to re-emit the services flow. StateFlow dedupes by
    // structural equality, so if the rediscovered services are identical (no
    // change) there will be no emission — that's the *no-change* case, and
    // still counts as success for the caller (the current services list is
    // already accurate). Only reflection/refresh/channel-send failures above
    // return false.
//    val fresh = withTimeoutOrNull(REDISCOVER_TIMEOUT) {
//        services.first { it !== before }
//    }
//    if (fresh == null) {
//        logger.d("no re-emission within ${REDISCOVER_TIMEOUT} — services unchanged")
//    } else {
//        logger.d("services rediscovered: ${fresh.size}")
//    }
    return true
}

private fun extractConnection(peripheral: AndroidPeripheral): Any? = try {
    val connectionField = peripheral.javaClass
        .getDeclaredField("connection")
        .apply { isAccessible = true }
    val connectionFlow = connectionField.get(peripheral) as? StateFlow<*>
    connectionFlow?.value
} catch (e: Throwable) {
    Logger.w(LOG_TAG, e) { "extractConnection failed" }
    null
}

private inline fun <reified T : Any> extractField(owner: Any, name: String): T? = try {
    val field = owner.javaClass.getDeclaredField(name).apply { isAccessible = true }
    field.get(owner) as? T
} catch (e: Throwable) {
    Logger.w(LOG_TAG, e) { "extractField($name) failed" }
    null
}

private fun kableOnServiceChangedInstance(): Any? = try {
    Class.forName(KABLE_ON_SERVICE_CHANGED_CLASS)
        .getField("INSTANCE")
        .get(null)
} catch (e: Throwable) {
    Logger.w(LOG_TAG, e) { "kableOnServiceChangedInstance failed" }
    null
}

private fun invokeGattRefresh(gatt: BluetoothGatt): Boolean = try {
    val method = gatt.javaClass.getMethod("refresh")
    method.invoke(gatt) as? Boolean == true
} catch (e: Throwable) {
    Logger.w(LOG_TAG, e) { "BluetoothGatt.refresh() failed" }
    false
}
