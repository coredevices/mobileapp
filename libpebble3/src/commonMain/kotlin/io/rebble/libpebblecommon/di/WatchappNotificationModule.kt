package io.rebble.libpebblecommon.di

import io.rebble.libpebblecommon.notification.WatchappNotificationDispatcher
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

/**
 * Singleton for the watchapp notification subscription API (PR 2).
 *
 * The Android-side `LibPebbleNotificationListener` injects this and calls
 * `dispatch()` on every posted/removed notification. The dispatcher is a
 * lightweight router that consults `LibPebble.watches` for currently-running
 * PKJS apps and delivers to those whose `notificationFilter` matches.
 *
 * No platform-specific glue — works the same for any platform that wires a
 * notification source to call `dispatch(packageName, notificationJson)`.
 */
val watchappNotificationModule = module {
    singleOf(::WatchappNotificationDispatcher)
}
