package coredevices.ring.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coredevices.indexai.data.entity.mcp.HttpMcpServerEntity
import coredevices.ring.agent.DefaultCaptureType
import coredevices.ring.agent.IndexActionsRepository
import coredevices.ring.agent.integrations.providers.IntegrationProvider
import coredevices.ring.agent.integrations.providers.IntegrationProviderRepository
import coredevices.ring.agent.integrations.providers.IntegrationSupport
import coredevices.ring.agent.integrations.providers.availableDestinations
import coredevices.ring.agent.integrations.providers.visibleDestinations
import coredevices.ring.agent.pebble_plugin.PluginGrant
import coredevices.ring.agent.pebble_plugin.PluginStore
import coredevices.ring.agent.pebble_plugin.StorePlugin
import coredevices.ring.database.Preferences
import coredevices.ring.database.room.repository.McpServerRepository
import coredevices.ring.service.button.GestureRoutingPreferences
import coredevices.ring.ui.screens.settings.nothingRoutesToIndexAgent
import coredevices.util.Platform
import coredevices.util.flow.mapState
import coredevices.util.isAndroid
import io.rebble.libpebblecommon.permissions.InstallReview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.uuid.Uuid

/**
 * State for the Index Agent actions section: built-in actions, plugins, MCP servers and where
 * notes and reminders are saved. The locker is reached through [lockerUuids] so the
 * view model does not need the whole of LibPebble.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class IndexActionsViewModel(
    private val indexActionsRepository: IndexActionsRepository,
    private val mcpServerRepository: McpServerRepository,
    private val pluginStore: PluginStore,
    private val providers: IntegrationProviderRepository,
    private val preferences: Preferences,
    gestureRouting: GestureRoutingPreferences,
    platform: Platform,
    val pluginsEnabled: StateFlow<Boolean>,
    lockerUuids: Flow<List<Uuid>>,
) : ViewModel() {
    val agentReachable: StateFlow<Boolean> = gestureRouting.routes.mapState { !nothingRoutesToIndexAgent(it) }
    val contactsDialogSupported: Boolean = platform.isAndroid

    private val _showContactsDialog = MutableStateFlow(false)
    val showContactsDialog = _showContactsDialog.asStateFlow()

    // Eager so the list is loaded before the section is lazily scrolled into view.
    val indexActions = indexActionsRepository.actions.stateIn(
        viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = emptyList()
    )
    val httpMcpDisabledReason = indexActionsRepository.httpMcpDisabledReason.stateIn(
        viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = null
    )
    val plugins = indexActionsRepository.plugins.stateIn(
        viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = emptyList()
    )
    val httpMcpServers = mcpServerRepository.httpServersFlow().stateIn(
        viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = emptyList()
    )

    val captureType = preferences.defaultCaptureType
    val noteProvider = providers.selectedNoteProvider
    val reminderProvider = providers.selectedReminderProvider
    val approvedContactCount: StateFlow<Int> = preferences.approvedBeeperContacts.mapState { it.size }

    private val providerRefresh = MutableStateFlow(0)
    // Connecting from a destination sheet is a pick as well as a connect, but the connect flows
    // only report dismissal, so the choice is applied once the provider shows up as available.
    // Held by id: a store plugin has no provider until it is installed.
    private val pendingNoteConnect = MutableStateFlow<String?>(null)
    private val pendingReminderConnect = MutableStateFlow<String?>(null)

    val availableNoteProviders = providers
        .availableDestinations(IntegrationSupport.Note, pluginsEnabled, providerRefresh)
        .applyingPendingConnect(pendingNoteConnect, providers::selectNoteProvider)
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val availableReminderProviders = providers
        .availableDestinations(IntegrationSupport.Reminder, pluginsEnabled, providerRefresh)
        .applyingPendingConnect(pendingReminderConnect, providers::selectReminderProvider)
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    /** Every destination on this platform for the kind, connected or not. */
    val noteDestinationProviders = destinationProviders(IntegrationSupport.Note)
    val reminderDestinationProviders = destinationProviders(IntegrationSupport.Reminder)

    // Fetched once per screen: the list only changes when the store does.
    private val indexStorePlugins = flow { emit(pluginStore.indexPlugins().orEmpty()) }
        .shareIn(viewModelScope, SharingStarted.Lazily, replay = 1)

    /** Store plugins not in the locker yet, offered as destinations while plugins are on. */
    val storePlugins: StateFlow<List<StorePlugin>> = pluginsEnabled
        .flatMapLatest { pluginsOn ->
            if (!pluginsOn) {
                flowOf(emptyList())
            } else {
                combine(indexStorePlugins, lockerUuids) { listed, installed ->
                    listed.filter { it.pluginUuid !in installed }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    /** Plugins with a locker page; built-in plugins have none to configure from. */
    val lockerPlugins: StateFlow<Set<Uuid>> = lockerUuids
        .map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptySet())

    fun setActionEnabled(name: String, enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) { indexActionsRepository.setActionEnabled(name, enabled) }
    }

    fun setHttpServerEnabled(server: HttpMcpServerEntity, enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) { mcpServerRepository.setHttpServerEnabled(server, enabled) }
    }

    fun saveHttpServer(server: HttpMcpServerEntity) {
        viewModelScope.launch(Dispatchers.IO) { mcpServerRepository.addOrUpdateHttpServer(server) }
    }

    fun deleteHttpServer(server: HttpMcpServerEntity) {
        viewModelScope.launch(Dispatchers.IO) { mcpServerRepository.deleteHttpServer(server) }
    }

    fun setPluginGrantEnabled(pluginUuid: Uuid, grant: PluginGrant, enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            indexActionsRepository.setPluginGrantEnabled(pluginUuid, grant, enabled)
        }
    }

    fun setCaptureType(type: DefaultCaptureType) {
        preferences.setDefaultCaptureType(type)
    }

    fun selectNoteProvider(provider: IntegrationProvider) {
        pendingNoteConnect.value = null
        providers.selectNoteProvider(provider)
    }

    fun selectReminderProvider(provider: IntegrationProvider) {
        pendingReminderConnect.value = null
        providers.selectReminderProvider(provider)
    }

    fun noteConnectStarted(providerId: String) {
        pendingNoteConnect.value = providerId
    }

    fun reminderConnectStarted(providerId: String) {
        pendingReminderConnect.value = providerId
    }

    suspend fun reviewInstall(plugin: StorePlugin): InstallReview? = pluginStore.reviewInstall(plugin.pluginUuid)

    suspend fun installStorePlugin(plugin: StorePlugin, granted: Map<String, Boolean?>): Boolean {
        val installed = pluginStore.install(plugin.pluginUuid, granted)
        refreshAvailableProviders()
        return installed
    }

    fun refreshAvailableProviders() {
        indexActionsRepository.refreshPlugins()
        providerRefresh.value++
    }

    fun showContactsDialog() {
        _showContactsDialog.value = true
    }

    fun closeContactsDialog() {
        _showContactsDialog.value = false
    }

    private fun destinationProviders(support: IntegrationSupport): StateFlow<List<IntegrationProvider>> =
        combine(providerRefresh, pluginsEnabled) { _, pluginsOn ->
            providers.providers(support).visibleDestinations(pluginsOn)
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private fun Flow<List<IntegrationProvider>>.applyingPendingConnect(
        pending: MutableStateFlow<String?>,
        select: (IntegrationProvider) -> Unit,
    ): Flow<List<IntegrationProvider>> = onEach { available ->
        available.firstOrNull { it.id == pending.value }?.let {
            select(it)
            pending.value = null
        }
    }
}
