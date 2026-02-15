package coredevices.pebble

import coredevices.pebble.actions.IosPebbleAppActions
import coredevices.pebble.actions.IosPebbleNotificationActions
import coredevices.pebble.actions.IosPebbleQuietTimeActions
import coredevices.pebble.actions.PebbleAppActions
import coredevices.pebble.actions.PebbleNotificationActions
import coredevices.pebble.actions.PebbleQuietTimeActions
import org.koin.dsl.bind
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

actual val platformWatchModule: Module = module {
    single<Platform> { Platform.IOS }
    singleOf(::PebbleIosDelegate)
    singleOf(::IosPebbleAppActions) bind PebbleAppActions::class
    singleOf(::IosPebbleNotificationActions) bind PebbleNotificationActions::class
    singleOf(::IosPebbleQuietTimeActions) bind PebbleQuietTimeActions::class
}
