package coredevices.ring.ui.screens.settings

import CommonRoutes
import CoreNav
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import coredevices.ring.external.indexwebhook.IndexWebhookConfig
import coredevices.ring.external.indexwebhook.IndexWebhookSettingsViewModel
import coredevices.ring.agent.pebble_plugin.PblPluginIntegrationProvider
import coredevices.ring.external.indexwebhook.IndexWebhookSheet
import coredevices.ring.ui.navigation.RingRoutes
import coredevices.ring.ui.viewmodel.IndexActionsViewModel
import coredevices.ring.ui.viewmodel.RingButtonViewModel
import io.rebble.libpebblecommon.locker.AppType
import org.koin.compose.viewmodel.koinViewModel
import kotlin.uuid.Uuid

/**
 * The Ring Button switchboard with its settings state.
 *
 * Extracted so the button controls exist in one place wherever they are shown. Every host must
 * also render [IndexWebhookSheetHost], or the webhook rows here open nothing.
 */
@Composable
fun RingButtonSection(coreNav: CoreNav, viewModel: RingButtonViewModel = koinViewModel()) {
    val webhookViewModel = koinViewModel<IndexWebhookSettingsViewModel>()
    val gestureRoutes by viewModel.gestureRoutes.collectAsState()
    val gestureCommands by viewModel.gestureCommands.collectAsState()
    val webhookConfigs by viewModel.webhookConfigs.collectAsState()
    val agentDestinations by viewModel.agentDestinations.collectAsState()
    val configurePlugin: (Uuid, String) -> Unit = { uuid, title ->
        coreNav.navigateTo(CommonRoutes.WatchappSettingsRoute(uuid = uuid.toString(), title = title))
    }
    val installer = rememberStorePluginInstaller(
        reviewInstall = viewModel::reviewInstall,
        installPlugin = viewModel::installStorePlugin,
        onConfigurePlugin = configurePlugin,
    )

    LaunchedEffect(Unit) { viewModel.refreshAgentDestinations() }
    installer.Dialogs()
    ButtonSwitchboard(
        routes = gestureRoutes,
        commands = gestureCommands,
        musicEnabled = viewModel.musicEnabled,
        webhookConfigFor = { webhookConfigs[it] ?: IndexWebhookConfig() },
        onSetRoute = viewModel::setGestureRoute,
        onSetCommand = viewModel::setGestureCommand,
        onOpenWebhookSettings = { webhookViewModel.openDialog(it) },
        onDisableWebhook = viewModel::disableWebhook,
        agentDestinations = agentDestinations,
        onConnectAgentPlugin = { provider ->
            if (provider is PblPluginIntegrationProvider) configurePlugin(provider.pluginUuid, provider.title)
        },
        onInstallAgentPlugin = installer::install,
        onConfigureAgentPlugin = { provider ->
            if (provider is PblPluginIntegrationProvider) {
                coreNav.navigateTo(CommonRoutes.LockerAppRoute(uuid = provider.pluginUuid.toString()))
            }
        },
    )
}

@Composable
fun IndexWebhookSheetHost() {
    val webhookViewModel = koinViewModel<IndexWebhookSettingsViewModel>()
    val open by webhookViewModel.dialogOpen.collectAsState()
    if (open) {
        IndexWebhookSheet(webhookViewModel)
    }
}

/** The Index Agent action list with its settings state, shared the same way as [RingButtonSection]. */
@Composable
fun IndexAgentActionsSection(
    coreNav: CoreNav,
    showHeader: Boolean = true,
    canBrowsePlugins: Boolean = true,
    viewModel: IndexActionsViewModel = koinViewModel(),
) {
    val agentReachable by viewModel.agentReachable.collectAsState()
    val actions by viewModel.indexActions.collectAsState()
    val httpMcpDisabledReason by viewModel.httpMcpDisabledReason.collectAsState()
    val plugins by viewModel.plugins.collectAsState()
    val httpMcpServers by viewModel.httpMcpServers.collectAsState()
    val captureType by viewModel.captureType.collectAsState()
    val noteProvider by viewModel.noteProvider.collectAsState()
    val reminderProvider by viewModel.reminderProvider.collectAsState()
    val approvedContactCount by viewModel.approvedContactCount.collectAsState()
    val noteDestinations by viewModel.noteDestinations.collectAsState()
    val reminderDestinations by viewModel.reminderDestinations.collectAsState()
    val lockerPlugins by viewModel.lockerPlugins.collectAsState()
    val pluginsEnabled by viewModel.pluginsEnabled.collectAsState()
    val showContactsDialog by viewModel.showContactsDialog.collectAsState()

    LaunchedEffect(Unit) { viewModel.refreshAvailableProviders() }
    if (showContactsDialog && viewModel.contactsDialogSupported) {
        SettingsBeeperContactsDialog(onDismissRequest = viewModel::closeContactsDialog)
    }
    IndexActionsSection(
        showHeader = showHeader,
        agentReachable = agentReachable,
        actions = actions,
        plugins = plugins,
        httpMcpServers = httpMcpServers,
        httpMcpDisabledReason = httpMcpDisabledReason,
        captureType = captureType,
        noteProvider = noteProvider,
        reminderProvider = reminderProvider,
        approvedContactCount = approvedContactCount,
        noteDestinations = noteDestinations,
        reminderDestinations = reminderDestinations,
        lockerPlugins = lockerPlugins,
        pluginsEnabled = pluginsEnabled,
        onSetActionEnabled = viewModel::setActionEnabled,
        onSetPluginGrantEnabled = viewModel::setPluginGrantEnabled,
        onSetHttpServerEnabled = viewModel::setHttpServerEnabled,
        onSaveHttpServer = viewModel::saveHttpServer,
        onDeleteHttpServer = viewModel::deleteHttpServer,
        onSetCaptureType = viewModel::setCaptureType,
        onSelectNoteProvider = viewModel::selectNoteProvider,
        onSelectReminderProvider = viewModel::selectReminderProvider,
        onNoteConnectStarted = viewModel::noteConnectStarted,
        onReminderConnectStarted = viewModel::reminderConnectStarted,
        onReviewInstall = viewModel::reviewInstall,
        onInstallStorePlugin = viewModel::installStorePlugin,
        onAddIntegration = { coreNav.navigateTo(RingRoutes.AddIntegration) },
        onConfigurePlugin = { uuid, title ->
            coreNav.navigateTo(CommonRoutes.WatchappSettingsRoute(uuid = uuid.toString(), title = title))
        },
        onOpenPluginPage = { uuid -> coreNav.navigateTo(CommonRoutes.LockerAppRoute(uuid = uuid.toString())) },
        onOpenBeeperContacts = viewModel::showContactsDialog,
        onGetMoreActions = {},
        onGetMorePlugins = { coreNav.navigateTo(CommonRoutes.LockerTabRoute(AppType.Plugin)) }
            .takeIf { canBrowsePlugins },
        onProvidersChanged = viewModel::refreshAvailableProviders,
    )
}
