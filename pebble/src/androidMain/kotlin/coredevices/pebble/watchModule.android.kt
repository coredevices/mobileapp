package coredevices.pebble

import android.content.Context
import io.rebble.libpebblecommon.connection.AppContext
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

actual val platformWatchModule: Module = module {
    single {
        val context = get<Context>()
        AppContext(context.applicationContext)
    }
    single<Platform> { Platform.Android }
    singleOf(::PebbleAndroidDelegate)
}