package coredevices.pebble

import coredevices.pebble.actions.IosPebbleAppActions
import coredevices.pebble.actions.IosPebbleNotificationActions
import coredevices.pebble.actions.IosPebbleQuietTimeActions
import coredevices.pebble.actions.IosPebbleTimelineActions
import coredevices.pebble.actions.IosPebbleHealthActions
import coredevices.pebble.actions.IosPebbleWatchInfoActions
import coredevices.pebble.actions.PebbleAppActions
import coredevices.pebble.actions.PebbleNotificationActions
import coredevices.pebble.actions.PebbleQuietTimeActions
import coredevices.pebble.actions.PebbleTimelineActions
import coredevices.pebble.actions.PebbleHealthActions
import coredevices.pebble.actions.PebbleWatchInfoActions
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
    singleOf(::IosPebbleTimelineActions) bind PebbleTimelineActions::class
    singleOf(::IosPebbleWatchInfoActions) bind PebbleWatchInfoActions::class
    singleOf(::IosPebbleHealthActions) bind PebbleHealthActions::class
}
