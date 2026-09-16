package io.rebble.libpebblecommon.plugin

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** JVM target stub — no phone radios to surface. */
actual class PhoneNetworkMonitor {
    actual val cellGeneration: Flow<String?> = flowOf(null)
    actual val connection: Flow<String?> = flowOf(null)
}
