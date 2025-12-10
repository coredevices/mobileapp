package coredevices.pebble

import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

actual val platformWatchModule: Module = module {
    single<Platform> { Platform.IOS }
    singleOf(::PebbleIosDelegate)
}
