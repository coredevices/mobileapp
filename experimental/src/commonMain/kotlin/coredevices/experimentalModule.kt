package coredevices

import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import coredevices.haversine.CollectionIndexStorage
import coredevices.haversine.KMPHaversineDebugDelegate
import coredevices.indexai.agent.ServletRepository
import coredevices.ring.BuildKonfig
import coredevices.ring.agent.AgentCactus
import coredevices.ring.model.CactusModelProvider
import coredevices.ring.transcription.InferenceBoostProvider
import coredevices.ring.transcription.NoOpInferenceBoostProvider
import coredevices.util.transcription.CactusModelPathProvider
import coredevices.util.transcription.InferenceBoost
import coredevices.ring.agent.AgentFactory
import coredevices.ring.agent.AgentNenya
import coredevices.ring.agent.BuiltinServletRepository
import coredevices.ring.agent.ContextualActionPredictor
import coredevices.ring.agent.ShortcutActionHandler
import coredevices.ring.agent.builtin_servlets.reminders.ReminderFactory
import coredevices.ring.agent.integrations.GTasksIntegration
import coredevices.ring.agent.integrations.UIEmailIntegration
import coredevices.ring.api.ApiConfig
import coredevices.ring.api.GoogleTasksApi
import coredevices.ring.api.NenyaClient
import coredevices.ring.api.NenyaClientImpl
import coredevices.ring.api.NotionApi
import coredevices.ring.audio.M4aEncoder
import coredevices.ring.database.Preferences
import coredevices.ring.database.PreferencesImpl
import coredevices.ring.database.room.RingDatabase
import coredevices.ring.database.room.repository.McpSandboxRepository
import coredevices.ring.database.room.repository.RecordingProcessingTaskRepository
import coredevices.ring.database.room.repository.RecordingRepository
import coredevices.ring.database.room.repository.RingTransferRepository
import coredevices.ring.external.vermillion.VermillionApi
import coredevices.ring.external.vermillion.VermillionApiImpl
import coredevices.ring.external.vermillion.VermillionPreferences
import coredevices.ring.firestoreModule
import coredevices.ring.mcpModule
import coredevices.ring.service.FirestoreRingDebugDelegate
import coredevices.ring.service.IndexButtonActionHandler
import coredevices.ring.service.IndexButtonSequenceRecorder
import coredevices.ring.service.IndexNotificationManager
import coredevices.ring.service.PrefsCollectionIndexStorage
import coredevices.ring.service.RecordingBackgroundScope
import coredevices.ring.service.RingBackgroundManager
import coredevices.ring.service.RingPairing
import coredevices.ring.service.RingSync
import coredevices.ring.service.recordings.RecordingPreprocessor
import coredevices.ring.service.recordings.RecordingProcessingQueue
import coredevices.ring.service.recordings.RecordingProcessor
import coredevices.ring.service.recordings.button.RecordingOperationFactory
import coredevices.ring.storage.RecordingStorage
import coredevices.ring.util.RingCompanionDeviceManager
import coredevices.ring.util.trace.RingTraceSession
import coredevices.ring.viewmodelModule
import coredevices.util.CommonBuildKonfig
import coredevices.util.PermissionRequester
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.Module
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

class HackyPermissionRequesterProvider(val getter: () -> PermissionRequester) {
    fun get(): PermissionRequester = getter()
}

val experimentalModule = module {
    includes(platformRingModule)
    includes(mcpModule)
    includes(firestoreModule)
    includes(viewmodelModule)

    single {
        val builder: RoomDatabase.Builder<RingDatabase> = get()
        builder
            .fallbackToDestructiveMigrationOnDowngrade(true)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
    }
    single {
        get<RingDatabase>().localReminderDao()
    }
    single {
        get<RingDatabase>().cachedRecordingMetadataDao()
    }
    single {
        get<RingDatabase>().ringDebugTransferDao()
    }
    single {
        get<RingDatabase>().localRecordingDao()
    }
    single {
        get<RingDatabase>().recordingEntryDao()
    }
    single {
        get<RingDatabase>().conversationMessageDao()
    }
    single {
        get<RingDatabase>().ringTransferDao()
    }
    single {
        get<RingDatabase>().builtinMcpGroupAssociationDao()
    }
    single {
        get<RingDatabase>().httpMcpGroupAssociationDao()
    }
    single {
        get<RingDatabase>().httpMcpServerDao()
    }
    single {
        get<RingDatabase>().mcpSandboxGroupDao()
    }
    single {
        get<RingDatabase>().recordingProcessingTaskDao()
    }
    single {
        get<RingDatabase>().traceSessionDao()
    }
    single {
        get<RingDatabase>().traceEntryDao()
    }
    singleOf(::RecordingRepository)
    singleOf(::RingTransferRepository)
    singleOf(::RecordingProcessingTaskRepository)
    singleOf(::PreferencesImpl) bind Preferences::class
    singleOf(::RingTraceSession)

    single {
        ApiConfig(
            nenyaUrl = BuildKonfig.NENYA_URL,
            notionOAuthBackendUrl = BuildKonfig.NOTION_OAUTH_BACKEND_URL,
            notionApiUrl = "https://api.notion.com/v1",
            bugUrl = CommonBuildKonfig.BUG_URL,
            version = CommonBuildKonfig.USER_AGENT_VERSION,
            tokenUrl = CommonBuildKonfig.TOKEN_URL,
        )
    }

    singleOf(::NenyaClientImpl) bind NenyaClient::class
    singleOf(::NotionApi)
    singleOf(::GoogleTasksApi)
    singleOf(::M4aEncoder)
    singleOf(::VermillionPreferences)
    single {
        VermillionApiImpl(
            get(),
            get(),
            get(),
            get<RecordingBackgroundScope>()
        )
    } bind VermillionApi::class

    single { RecordingBackgroundScope(CoroutineScope(Dispatchers.IO + SupervisorJob())) }
    single { RecordingProcessingQueue(get(), get(), get(), get(), get(), get(), get(), get()) }
    singleOf(::RecordingOperationFactory)
    singleOf(::RecordingStorage)
    singleOf(::RecordingPreprocessor)
    singleOf(::RingSync)
    singleOf(::RingBackgroundManager)
    singleOf(::IndexNotificationManager)
    singleOf(::RingPairing)
    singleOf(::ExperimentalDevices)
    singleOf(::PrefsCollectionIndexStorage) bind CollectionIndexStorage::class
    factory { params ->
        RingCompanionDeviceManager(params.get())
    }
    factory { HackyPermissionRequesterProvider { get<PermissionRequester>() } }
    factory { p -> AgentNenya(get(), p.getOrNull() ?: emptyList(), p.getOrNull() ?: false) }
    single { CactusModelProvider() }
    single<CactusModelPathProvider> { get<CactusModelProvider>() }
    factory { p -> AgentCactus(get<CactusModelProvider>(), p.getOrNull() ?: emptyList(), getOrNull<InferenceBoostProvider>() ?: NoOpInferenceBoostProvider()) }
    singleOf(::AgentFactory)
    singleOf(::RecordingProcessor)
    singleOf(::IndexButtonActionHandler)
    singleOf(::IndexButtonSequenceRecorder)
    singleOf(::FirestoreRingDebugDelegate) bind KMPHaversineDebugDelegate::class
    singleOf(::McpSandboxRepository)
    singleOf(::BuiltinServletRepository) bind ServletRepository::class

    factoryOf(::GTasksIntegration)
    factoryOf(::UIEmailIntegration)
    singleOf(::ReminderFactory)
    singleOf(::ContextualActionPredictor)
    singleOf(::ShortcutActionHandler)
}

expect val platformRingModule: Module
