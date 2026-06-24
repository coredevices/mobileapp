package io.rebble.libpebblecommon.js

import io.rebble.libpebblecommon.connection.AppContext
import io.rebble.libpebblecommon.connection.LibPebble
import io.rebble.libpebblecommon.connection.TokenProvider
import io.rebble.libpebblecommon.database.dao.FakeLockerEntryDao
import io.rebble.libpebblecommon.database.entity.LockerEntry
import io.rebble.libpebblecommon.metadata.pbw.appinfo.PbwAppInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.io.files.Path

fun createJsRunner(
    libPebble: LibPebble,
    scope: CoroutineScope,
    appInfo: PbwAppInfo,
    lockerEntry: LockerEntry,
    jsPath: Path,
    device: CompanionAppDevice,
    urlOpenRequests: Channel<String>,
    logMessages: Channel<String>
): JsRunner {
    val watchConfigFlow = testWatchConfigFlow()
    return JavascriptCoreJsRunner(
        appContext = AppContext(),
        libPebble = libPebble,
        jsTokenUtil = JsTokenUtil(
            object : TokenProvider {
                override suspend fun getDevToken(): String? {
                    return null
                }
            },
            lockerEntryDao = FakeLockerEntryDao(),
            watchConfigFlow = watchConfigFlow,
        ),
        device = device,
        scope = scope,
        appInfo = appInfo,
        lockerEntry = lockerEntry,
        jsPath = jsPath,
        urlOpenRequests = urlOpenRequests,
        logMessages = logMessages,
        remoteTimelineEmulator = testRemoteTimelineEmulator(),
        httpInterceptorManager = testHttpInterceptorManager(),
        notificationConfigFlow = testNotificationConfigFlow(),
    )
}
