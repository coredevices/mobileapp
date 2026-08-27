package coredevices


import androidx.room.Room
import androidx.room.RoomDatabase
import coredevices.haversine.KMPHaversineSatelliteManager
import coredevices.ring.RingDelegate
import coredevices.ring.agent.integrations.obsidian.IosObsidianVault
import coredevices.ring.agent.integrations.obsidian.ObsidianVault
import coredevices.ring.database.IntegrationTokenStorageImpl
import coredevices.ring.external.indexwebhook.IosNetworkMonitor
import coredevices.ring.external.indexwebhook.NetworkMonitor
import coredevices.ring.database.Preferences
import coredevices.ring.database.room.RingDatabase
import coredevices.ring.encryption.EncryptionKeyManager
import coredevices.ring.model.CactusModelProvider
import coredevices.ring.service.BackgroundRingService
import coredevices.ring.service.PlatformIndexNotificationManager
import coredevices.ring.service.RingOta
import coredevices.ring.service.RingSync
import coredevices.ring.util.AudioPlayer
import coredevices.ring.util.AudioRecorder
import coredevices.util.integrations.IntegrationTokenStorage
import coredevices.util.transcription.CactusModelPathProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

actual val platformRingModule = module {
    singleOf(::IosNetworkMonitor) bind NetworkMonitor::class
    single<CactusModelPathProvider> { CactusModelProvider() }
    singleOf(::RingDelegate)
    factoryOf(::AudioRecorder)
    factoryOf(::AudioPlayer)
    factory {
        val documentDirectory = NSFileManager.defaultManager.URLForDirectory(
            directory = NSDocumentDirectory,
            inDomain = NSUserDomainMask,
            appropriateForURL = null,
            create = false,
            error = null
        )
        Room.databaseBuilder<RingDatabase>(
            name = requireNotNull(documentDirectory?.path)+"/coreapp_room.db"
        )
    } bind RoomDatabase.Builder::class
    single {
        val prefs = get<Preferences>()
        val ringOTA = get<RingOta>()
        KMPHaversineSatelliteManager(
            pairedSatelliteIdProvider = { prefs.ringPaired.value },
            debugDelegate = get(),
            hacksDelegate = get(),
            collectionIndexStorage = get(),
            hwVersion = RingSync.SATELLITE_HW_VER,
            scope = CoroutineScope(Dispatchers.Default),
            updateJsonProvider = { it?.let { ringOTA.getLatestFirmware(it) } }
        )
    }
    single {
        RingOta(get(), get(), get())
    }
    singleOf(::PlatformIndexNotificationManager)
    singleOf(::BackgroundRingService)
    singleOf(::IntegrationTokenStorageImpl) bind IntegrationTokenStorage::class
    single { EncryptionKeyManager() }
    single<ObsidianVault> { IosObsidianVault() }
}
