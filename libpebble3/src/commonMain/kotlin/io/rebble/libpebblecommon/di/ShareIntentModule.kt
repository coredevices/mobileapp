package io.rebble.libpebblecommon.di

import io.rebble.libpebblecommon.shareintent.ShareIntentDispatcher
import io.rebble.libpebblecommon.shareintent.ShareTargetsProducer
import io.rebble.libpebblecommon.shareintent.ShareUrlResolver
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

/**
 * Singletons for the share-intent platform API (PR 1).
 *
 * Platform-specific glue (e.g. Android's `ShareTargetSync`) is registered in
 * the host application's Android/iOS Koin module, since it needs the host's
 * activity [android.content.ComponentName] and resource ids.
 */
val shareIntentModule = module {
    singleOf(::ShareIntentDispatcher)
    singleOf(::ShareTargetsProducer)
    // ShareUrlResolver gets its own HttpClient instance rather than the
    // libpebble3 singleton — see class kdoc for reasoning. The no-arg
    // primary constructor builds an OkHttp client with engine-level
    // timeouts; the secondary internal constructor is for tests only.
    single { ShareUrlResolver() }
}
