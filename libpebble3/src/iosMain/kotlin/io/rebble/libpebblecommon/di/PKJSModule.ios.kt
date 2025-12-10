package io.rebble.libpebblecommon.di

import io.rebble.libpebblecommon.js.JavascriptCoreJsRunner
import io.rebble.libpebblecommon.js.JsRunner
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module

actual val pkjsPlatformModule: Module = module {
    factory { params ->
        JavascriptCoreJsRunner(
            appContext = get(),
            libPebble = get(),
            jsTokenUtil = get(),

            device = params.get(),
            scope = params.get(),
            appInfo = params.get(),
            lockerEntry = params.get(),
            jsPath = params.get(),
            urlOpenRequests = params.get(),
            logMessages = params.get(),
            remoteTimelineEmulator = get(),
        )
    } bind JsRunner::class
}