package coredevices.coreapp.plugins

import androidx.compose.runtime.Composable
import coredevices.database.AppstoreSourceDao
import coredevices.pebble.services.AppstoreService
import coredevices.pebble.services.isPebbleFeed
import coredevices.pebble.services.pluginMetadata
import coredevices.pebble.ui.CommonAppType
import coredevices.pebble.ui.InstallReviewDialog
import coredevices.pebble.ui.NativeLockerAddUtil
import coredevices.ring.agent.pebble_plugin.PluginStore
import coredevices.ring.agent.pebble_plugin.StorePlugin
import io.rebble.libpebblecommon.connection.KnownPebbleDevice
import io.rebble.libpebblecommon.connection.LibPebble
import io.rebble.libpebblecommon.locker.AppType
import io.rebble.libpebblecommon.metadata.WatchType
import io.rebble.libpebblecommon.permissions.InstallReview
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.parameter.parametersOf
import kotlin.uuid.Uuid

/** Serves the ring module's [PluginStore] from the Pebble feed's Index collection. */
class AppstorePluginStore(
    private val appstoreSourceDao: AppstoreSourceDao,
    private val libPebble: LibPebble,
    private val nativeLockerAddUtil: NativeLockerAddUtil,
) : PluginStore, KoinComponent {
    /** Listings from the last fetch, so an install has the store entry to add. */
    private val listed = mutableMapOf<Uuid, CommonAppType.Store>()

    override suspend fun indexPlugins(): List<StorePlugin>? {
        val source = appstoreSourceDao.getAllEnabledSources().firstOrNull { it.isPebbleFeed() } ?: return null
        val service = get<AppstoreService> { parametersOf(source) }
        val apps = service.fetchCollectionApps(
            path = "collection/$INDEX_COLLECTION_SLUG",
            appType = AppType.Plugin,
            hardwarePlatform = watchType(),
            limit = COLLECTION_LIMIT,
        ) ?: return null
        return apps.mapNotNull { app ->
            val store = app.commonAppType as? CommonAppType.Store ?: return@mapNotNull null
            val storeApp = store.storeApp ?: return@mapNotNull null
            val metadata = storeApp.pluginMetadata() ?: return@mapNotNull null
            listed[app.uuid] = store
            StorePlugin(metadata, configurable = storeApp.plugin?.configPage != null)
        }
    }

    override suspend fun reviewInstall(pluginUuid: Uuid): InstallReview? =
        listed[pluginUuid]?.let { nativeLockerAddUtil.review(it) }

    override suspend fun install(pluginUuid: Uuid, granted: Map<String, Boolean?>): Boolean {
        val app = listed[pluginUuid] ?: return false
        return nativeLockerAddUtil.addAppToLocker(app, app.storeSource, granted)
    }

    @Composable
    override fun ReviewInstallDialog(review: InstallReview, onAnswer: (Map<String, Boolean?>?) -> Unit) {
        InstallReviewDialog(review, onAnswer)
    }

    // Plugins run on the phone, but the store wants a hardware to list for.
    private fun watchType(): WatchType =
        libPebble.watches.value.filterIsInstance<KnownPebbleDevice>().firstOrNull()?.watchType?.watchType
            ?: WatchType.EMERY

    private companion object {
        const val INDEX_COLLECTION_SLUG = "index-default"
        const val COLLECTION_LIMIT = 100
    }
}
