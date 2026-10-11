package io.rebble.libpebblecommon.connection.endpointmanager

import io.rebble.libpebblecommon.connection.CompanionApp
import io.rebble.libpebblecommon.di.ConnectionCoroutineScope
import io.rebble.libpebblecommon.di.LibPebbleCoroutineScope
import io.rebble.libpebblecommon.js.CompanionAppDevice
import io.rebble.libpebblecommon.metadata.pbw.appinfo.PbwAppInfo
import io.rebble.libpebblecommon.metadata.pbw.appinfo.hasPebbleKit2Companion
import io.rebble.libpebblecommon.pebblekit.classic.PebbleKitClassic
import io.rebble.libpebblecommon.pebblekit.two.PebbleKit2

actual fun createPlatformSpecificCompanionAppControl(
    device: CompanionAppDevice,
    appInfo: PbwAppInfo,
    pkjsRunning: Boolean,
    legacyPebbleKitEnabled: Boolean,
    libPebbleCoroutineScope: LibPebbleCoroutineScope,
    connectionCoroutineScope: ConnectionCoroutineScope,
): CompanionApp? {
    return when {
        appInfo.hasPebbleKit2Companion() ->
            PebbleKit2(device, appInfo, pkjsRunning, libPebbleCoroutineScope, connectionCoroutineScope)
        legacyPebbleKitEnabled -> PebbleKitClassic(device, appInfo, pkjsRunning, connectionCoroutineScope)
        else -> null
    }
}
