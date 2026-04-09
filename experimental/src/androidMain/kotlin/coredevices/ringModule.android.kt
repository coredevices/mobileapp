package coredevices

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import coredevices.haversine.KMPHaversineSatelliteManager
import coredevices.ring.RingDelegate
import coredevices.ring.agent.builtin_servlets.js.AndroidWebviewJsEngine
import coredevices.ring.agent.builtin_servlets.js.JsEngine
import coredevices.util.integrations.IntegrationTokenStorage
import coredevices.ring.database.IntegrationTokenStorageImpl
import coredevices.ring.database.Preferences
import coredevices.ring.database.room.RingDatabase
import coredevices.ring.service.PlatformIndexNotificationManager
import coredevices.ring.service.RingSync
import coredevices.ring.ui.screens.settings.SettingsBeeperContactsDialogViewModel
import coredevices.ring.ui.viewmodel.RingPairingViewModel
import coredevices.ring.util.AudioPlayer
import coredevices.ring.util.AudioRecorder
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.bind
import coredevices.ring.model.CactusModelProvider
import coredevices.ring.transcription.AndroidInferenceBoostProvider
import coredevices.ring.transcription.InferenceBoostProvider
import coredevices.util.transcription.CactusModelPathProvider
import coredevices.util.transcription.InferenceBoost
import org.koin.dsl.module

actual val platformRingModule = module {
    single<InferenceBoostProvider> { AndroidInferenceBoostProvider(get()) } bind InferenceBoost::class
    single<CactusModelPathProvider> { CactusModelProvider() }
    singleOf(::RingDelegate)
    single {
        val prefs = get<Preferences>()
        KMPHaversineSatelliteManager(
            pairedSatelliteIdProvider = { prefs.ringPaired.value?.replace(":", "") },
            debugDelegate = get(),
            collectionIndexStorage = get(),
            context = get(),
            hwVersion = RingSync.SATELLITE_HW_VER,
            CoroutineScope(Dispatchers.Default)
        )
    }
    singleOf(::PlatformIndexNotificationManager)
    singleOf(::IntegrationTokenStorageImpl) bind IntegrationTokenStorage::class
    factoryOf(::AndroidWebviewJsEngine) bind JsEngine::class
    factoryOf(::AudioRecorder)
    factoryOf(::AudioPlayer)
    factory {
        val context = get<Context>()
        val dbFile = context.applicationContext.getDatabasePath("coreapp_room.db")
        Room.databaseBuilder<RingDatabase>(context = context.applicationContext, name = dbFile.absolutePath)
    } bind RoomDatabase.Builder::class
    viewModelOf(::RingPairingViewModel)
    viewModelOf(::SettingsBeeperContactsDialogViewModel)
}