package coredevices

import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import coredevices.haversine.CollectionIndexStorage
import coredevices.haversine.KMPHaversineDebugDelegate
import coredevices.haversine.KMPHaversineHacksDelegate
import coredevices.indexai.agent.ServletRepository
import coredevices.libindex.database.BasePreferences
import coredevices.libindex.di.libIndexModule
import coredevices.ring.BuildKonfig
import coredevices.ring.agent.IndexAgentCactus
import coredevices.ring.model.CactusModelProvider
import coredevices.ring.transcription.InferenceBoostProvider
import coredevices.ring.transcription.NoOpInferenceBoostProvider
import coredevices.util.transcription.CactusModelPathProvider
import coredevices.ring.agent.AgentFactory
import coredevices.ring.agent.IndexAgentNenya
import coredevices.ring.agent.McpSandboxAgentNenya
import coredevices.ring.agent.SearchAgentNenya
import coredevices.ring.agent.BuiltinServletRepository
import coredevices.ring.agent.ContextualActionPredictor
import coredevices.ring.agent.ShareActionHandler
import coredevices.ring.agent.ShortcutActionHandler
import coredevices.ring.agent.builtin_servlets.reminders.BuiltInReminderFeedItems
import coredevices.ring.agent.builtin_servlets.reminders.BuiltInReminderIntegration
import coredevices.ring.agent.builtin_servlets.reminders.ReminderIntegrationFactory
import coredevices.ring.agent.builtin_servlets.reminders.createBuiltInReminderIntegration
import coredevices.ring.agent.integrations.DelegatedIntegrationItems
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
import coredevices.ring.database.room.repository.ItemRepository
import coredevices.ring.database.room.repository.ListRepository
import coredevices.ring.database.room.repository.RecordingRepository
import coredevices.ring.reminders.ReminderDeepLinkResolver
import coredevices.ring.service.indexfeed.DefaultListsBootstrap
import coredevices.ring.service.indexfeed.IndexFeedSyncService
import coredevices.ring.service.indexfeed.ItemFactory
import coredevices.libindex.database.repository.RingTransferRepository
import coredevices.ring.external.indexwebhook.IndexWebhookApi
import coredevices.ring.external.indexwebhook.IndexWebhookApiImpl
import coredevices.ring.external.indexwebhook.IndexWebhookPreferences
import coredevices.ring.agent.integrations.obsidian.ObsidianPreferences
import coredevices.ring.firestoreModule
import coredevices.ring.mcpModule
import coredevices.ring.service.FirestoreRingDebugDelegate
import coredevices.ring.service.IndexButtonActionHandler
import coredevices.ring.service.IndexButtonSequenceRecorder
import coredevices.ring.service.IndexNotificationManager
import coredevices.libindex.database.PrefsCollectionIndexStorage
import coredevices.ring.agent.AgentNenya
import coredevices.ring.api.NenyaModel
import coredevices.ring.service.RecordingBackgroundScope
import coredevices.ring.service.RingPairing
import coredevices.ring.service.RingSync
import coredevices.ring.service.recordings.RecordingPreprocessor
import coredevices.ring.service.recordings.RecordingProcessingQueue
import coredevices.ring.service.recordings.RecordingProcessor
import coredevices.ring.service.recordings.button.RecordingOperationFactory
import coredevices.ring.encryption.DocumentEncryptor
import coredevices.ring.encryption.EncryptionManager
import coredevices.ring.service.RingHacksDelegate
import coredevices.ring.storage.RealRecordingStorage
import coredevices.ring.storage.RecordingStorage
import coredevices.ring.util.trace.RingTraceSession
import coredevices.ring.util.trace.TraceSessionExporter
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
import org.koin.dsl.binds
import org.koin.dsl.module

class HackyPermissionRequesterProvider(val getter: () -> PermissionRequester) {
    fun get(): PermissionRequester = getter()
}

val experimentalModule = module {
    //TODO: remove and init LibIndex as library when its decoupled from global koin
    includes(libIndexModule)
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
    single {
        get<RingDatabase>().cachedItemDao()
    }
    single {
        get<RingDatabase>().cachedListDao()
    }
    singleOf(::RecordingRepository)
    single {
        RingTransferRepository(get(), get<RingDatabase>())
    }
    singleOf(::RecordingProcessingTaskRepository)
    single {
        val builtInReminders = get<BuiltInReminderIntegration>()
        ItemRepository(get()) { builtInReminders.cancelReminder(it) }
    }
    singleOf(::ListRepository)
    singleOf(::DefaultListsBootstrap)
    singleOf(::IndexFeedSyncService)
    singleOf(::ItemFactory)
    singleOf(::ReminderDeepLinkResolver)
    singleOf(::PreferencesImpl) binds arrayOf(Preferences::class, BasePreferences::class)
    singleOf(::RingTraceSession)
    singleOf(::TraceSessionExporter)

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
    singleOf(::IndexWebhookPreferences)
    singleOf(::ObsidianPreferences)
    single {
        IndexWebhookApiImpl(
            get(),
            get(),
            get(),
            get<RecordingBackgroundScope>()
        )
    } bind IndexWebhookApi::class

    single { RecordingBackgroundScope(CoroutineScope(Dispatchers.IO + SupervisorJob())) }
    single { RecordingProcessingQueue(get(), get(), get(), get(), get(), get(), get(), get()) }
    singleOf(::RecordingOperationFactory)
    singleOf(::RealRecordingStorage) bind RecordingStorage::class
    singleOf(::DocumentEncryptor)
    singleOf(::EncryptionManager)
    singleOf(::RecordingPreprocessor)
    singleOf(::RingSync)
    singleOf(::IndexNotificationManager)
    singleOf(::RingPairing)
    singleOf(::ExperimentalDevices)
    singleOf(::PrefsCollectionIndexStorage) bind CollectionIndexStorage::class
    factory { HackyPermissionRequesterProvider { get<PermissionRequester>() } }
    factory { p -> AgentNenya(get(), p.getOrNull() ?: "", p.getOrNull() ?: NenyaModel.Default, p.getOrNull() ?: emptyList()) }
    factory { p -> IndexAgentNenya(get(), p.getOrNull() ?: emptyList()) }
    factory { p -> McpSandboxAgentNenya(get(), p.getOrNull() ?: NenyaModel.Default, p.getOrNull() ?: emptyList()) }
    factory { p -> SearchAgentNenya(get(), get(), get(), p.getOrNull() ?: emptyList()) }
    single { CactusModelProvider() }
    single<CactusModelPathProvider> { get<CactusModelProvider>() }
    factory { p -> IndexAgentCactus(get<CactusModelProvider>(), p.getOrNull() ?: emptyList(), getOrNull<InferenceBoostProvider>() ?: NoOpInferenceBoostProvider()) }
    singleOf(::AgentFactory)
    singleOf(::RecordingProcessor)
    singleOf(::IndexButtonActionHandler)
    singleOf(::IndexButtonSequenceRecorder)
    singleOf(::FirestoreRingDebugDelegate) bind KMPHaversineDebugDelegate::class
    singleOf(::RingHacksDelegate) bind KMPHaversineHacksDelegate::class
    singleOf(::McpSandboxRepository)
    singleOf(::BuiltinServletRepository) bind ServletRepository::class

    factoryOf(::GTasksIntegration)
    factoryOf(::UIEmailIntegration)
    single { createBuiltInReminderIntegration() }
    singleOf(::BuiltInReminderFeedItems)
    singleOf(::DelegatedIntegrationItems)
    singleOf(::ReminderIntegrationFactory)
    singleOf(::ContextualActionPredictor)
    singleOf(::ShortcutActionHandler)
    singleOf(::ShareActionHandler)
}

expect val platformRingModule: Module
