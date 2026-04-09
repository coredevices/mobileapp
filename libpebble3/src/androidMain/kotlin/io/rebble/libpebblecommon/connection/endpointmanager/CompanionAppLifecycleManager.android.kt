package io.rebble.libpebblecommon.connection.endpointmanager

import io.rebble.libpebblecommon.connection.CompanionApp
import io.rebble.libpebblecommon.di.ConnectionCoroutineScope
import io.rebble.libpebblecommon.di.LibPebbleCoroutineScope
import io.rebble.libpebblecommon.js.CompanionAppDevice
import io.rebble.libpebblecommon.metadata.pbw.appinfo.PbwAppInfo
import io.rebble.libpebblecommon.pebblekit.classic.PebbleKitClassic
import io.rebble.libpebblecommon.pebblekit.two.PebbleKit2

actual fun createPlatformSpecificCompanionAppControl(
    device: CompanionAppDevice,
    appInfo: PbwAppInfo,
    pkjsRunning: Boolean,
    libPebbleCoroutineScope: LibPebbleCoroutineScope,
    connectionCoroutineScope: ConnectionCoroutineScope,
): CompanionApp? {
    val hasAnyPebbleKit2CompanionApps =
        appInfo.companionApp
            ?.android
            ?.apps
            ?.any { it.pkg != null } == true

    return if (hasAnyPebbleKit2CompanionApps) {
        PebbleKit2(device, appInfo, pkjsRunning, libPebbleCoroutineScope, connectionCoroutineScope)
    } else {
        PebbleKitClassic(device, appInfo, connectionCoroutineScope)
    }
}
