package coredevices.ring.ui.viewmodel

import coredevices.ring.agent.integrations.providers.IntegrationProvider
import coredevices.ring.agent.integrations.providers.IntegrationProviderRepository
import coredevices.ring.agent.integrations.providers.IntegrationSupport
import coredevices.ring.agent.integrations.providers.titleFor
import coredevices.ring.agent.integrations.providers.visibleDestinations
import coredevices.ring.agent.pebble_plugin.PblPluginIntegrationProvider
import coredevices.ring.agent.pebble_plugin.PluginStore
import coredevices.ring.agent.pebble_plugin.StorePlugin
import io.rebble.libpebblecommon.permissions.InstallReview
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.uuid.Uuid

/** One row of a destination picker: a provider on this platform, or a store plugin that would become one. */
sealed interface DestinationItem {
    val id: String
    val title: String

    /** Only a [connected] provider can be picked; one with a locker page is reconfigured from there. */
    data class Installed(
        val provider: IntegrationProvider,
        override val title: String,
        val connected: Boolean,
        val hasLockerPage: Boolean,
    ) : DestinationItem {
        override val id: String get() = provider.id
    }

    /** Not in the locker yet; connecting installs it. */
    data class InStore(val plugin: StorePlugin) : DestinationItem {
        override val id: String get() = PblPluginIntegrationProvider.idFor(plugin.pluginUuid)
        override val title: String get() = plugin.title
    }
}

val DestinationItem.isConnected: Boolean get() = this is DestinationItem.Installed && connected

val DestinationItem.isPlugin: Boolean
    get() = when (this) {
        is DestinationItem.Installed -> provider.isExternalPlugin
        is DestinationItem.InStore -> true
    }

/**
 * The one source for what the settings screens offer as a note, reminder or agent destination:
 * every provider on this platform, whether it is connected and has a locker page, then the store's
 * Index plugins that are not installed yet. Shared by the screens so the store is fetched once and
 * a [refresh] from one screen is seen by the others.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DestinationCatalog(
    private val providers: IntegrationProviderRepository,
    private val pluginStore: PluginStore,
    private val pluginsEnabled: StateFlow<Boolean>,
    private val lockerUuids: Flow<List<Uuid>>,
) {
    private val refresh = MutableStateFlow(0)
    private val storeFetch = Mutex()
    private val storeCache = MutableStateFlow<List<StorePlugin>?>(null)

    /** Plugins with a locker page; built-in plugins have none to configure from. */
    val lockerPlugins: Flow<Set<Uuid>> = lockerUuids.map { it.toSet() }

    // A failed fetch is not cached, so the next screen tries again.
    private val listedStorePlugins: Flow<List<StorePlugin>> = flow {
        val listed = storeCache.value ?: storeFetch.withLock {
            storeCache.value ?: pluginStore.indexPlugins()?.also { storeCache.value = it }
        }
        emit(listed.orEmpty())
    }

    private val storePlugins: Flow<List<StorePlugin>> = pluginsEnabled.flatMapLatest { pluginsOn ->
        if (!pluginsOn) {
            flowOf(emptyList())
        } else {
            combine(listedStorePlugins, lockerUuids) { listed, installed ->
                listed.filter { it.pluginUuid !in installed }
            }
        }
    }

    fun items(support: IntegrationSupport): Flow<List<DestinationItem>> =
        combine(installed(support), storePlugins) { installed, store ->
            installed + store.filter { support in it.supports }.map { DestinationItem.InStore(it) }
        }

    private fun installed(support: IntegrationSupport): Flow<List<DestinationItem.Installed>> =
        combine(refresh, pluginsEnabled, lockerPlugins) { _, pluginsOn, locker ->
            val connected = providers.availableProviders(support)
            providers.providers(support).visibleDestinations(pluginsOn).map {
                DestinationItem.Installed(
                    provider = it,
                    title = it.titleFor(support),
                    connected = it in connected,
                    hasLockerPage = it is PblPluginIntegrationProvider && it.pluginUuid in locker,
                )
            }
        }

    /** Providers connect from their own pages, so screens call this when they show and after a connect flow. */
    fun refresh() {
        refresh.value++
    }

    suspend fun reviewInstall(plugin: StorePlugin): InstallReview? = pluginStore.reviewInstall(plugin.pluginUuid)

    suspend fun install(plugin: StorePlugin, granted: Map<String, Boolean?>): Boolean {
        val installed = pluginStore.install(plugin.pluginUuid, granted)
        refresh()
        return installed
    }
}
