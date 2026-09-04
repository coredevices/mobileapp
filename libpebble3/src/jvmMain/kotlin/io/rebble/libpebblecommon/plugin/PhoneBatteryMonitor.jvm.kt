package io.rebble.libpebblecommon.plugin

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** JVM target stub — no phone battery to surface. */
actual class PhoneBatteryMonitor {
    actual val batteryLevel: Flow<Int?> = flowOf(null)
}
