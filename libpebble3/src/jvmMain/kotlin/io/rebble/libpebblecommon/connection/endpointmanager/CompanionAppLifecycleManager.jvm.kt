package io.rebble.libpebblecommon.connection.endpointmanager

import io.rebble.libpebblecommon.connection.CompanionApp
import io.rebble.libpebblecommon.di.ConnectionCoroutineScope
import io.rebble.libpebblecommon.di.LibPebbleCoroutineScope
import io.rebble.libpebblecommon.js.CompanionAppDevice
import io.rebble.libpebblecommon.metadata.pbw.appinfo.PbwAppInfo

actual fun createPlatformSpecificCompanionAppControl(
    device: CompanionAppDevice,
    appInfo: PbwAppInfo,
    pkjsRunning: Boolean,
    libPebbleCoroutineScope: LibPebbleCoroutineScope,
    connectionCoroutineScope: ConnectionCoroutineScope,
): CompanionApp? {
    // No JVM specific PebbleKit exists (yet?)
    return null
}
