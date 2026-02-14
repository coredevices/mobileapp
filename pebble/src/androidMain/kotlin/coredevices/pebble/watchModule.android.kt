package coredevices.pebble

import android.content.Context
import coredevices.pebble.actions.AndroidPebbleAppActions
import coredevices.pebble.actions.AndroidPebbleNotificationActions
import coredevices.pebble.actions.AndroidPebbleQuietTimeActions
import coredevices.pebble.actions.AndroidPebbleTimelineActions
import coredevices.pebble.actions.AndroidPebbleHealthActions
import coredevices.pebble.actions.AndroidPebbleWatchInfoActions
import coredevices.pebble.actions.PebbleAppActions
import coredevices.pebble.actions.PebbleNotificationActions
import coredevices.pebble.actions.PebbleQuietTimeActions
import coredevices.pebble.actions.PebbleTimelineActions
import coredevices.pebble.actions.PebbleHealthActions
import coredevices.pebble.actions.PebbleWatchInfoActions
import io.rebble.libpebblecommon.connection.AppContext
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

actual val platformWatchModule: Module = module {
    single {
        val context = get<Context>()
        AppContext(context.applicationContext)
    }
    single<Platform> { Platform.Android }
    singleOf(::PebbleAndroidDelegate)
    // Android implementations of generic Pebble actions (placeholders for now)
    singleOf(::AndroidPebbleAppActions) bind PebbleAppActions::class
    singleOf(::AndroidPebbleNotificationActions) bind PebbleNotificationActions::class
    singleOf(::AndroidPebbleQuietTimeActions) bind PebbleQuietTimeActions::class
    singleOf(::AndroidPebbleTimelineActions) bind PebbleTimelineActions::class
    singleOf(::AndroidPebbleWatchInfoActions) bind PebbleWatchInfoActions::class
    singleOf(::AndroidPebbleHealthActions) bind PebbleHealthActions::class
}