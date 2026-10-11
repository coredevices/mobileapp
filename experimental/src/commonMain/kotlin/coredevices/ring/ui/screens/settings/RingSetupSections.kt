package coredevices.ring.ui.screens.settings

import CommonRoutes
import CoreNav
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import coredevices.ring.external.indexwebhook.IndexWebhookConfig
import coredevices.ring.external.indexwebhook.IndexWebhookPreferences
import coredevices.ring.external.indexwebhook.IndexWebhookSettingsViewModel
import coredevices.ring.external.indexwebhook.IndexWebhookSheet
import coredevices.ring.ui.navigation.RingRoutes
import coredevices.ring.ui.viewmodel.SettingsViewModel
import coredevices.util.Platform
import coredevices.util.isAndroid
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * The Ring Button switchboard with its settings state.
 *
 * Extracted so the button controls exist in one place wherever they are shown. Every host must
 * also render [IndexWebhookSheetHost], or the webhook rows here open nothing.
 */
@Composable
fun RingButtonSection(viewModel: SettingsViewModel) {
    val platform = koinInject<Platform>()
    val webhookViewModel = koinViewModel<IndexWebhookSettingsViewModel>()
    val webhookPreferences = koinInject<IndexWebhookPreferences>()
    val gestureRoutes by viewModel.gestureRoutes.collectAsState()
    val webhookConfigs by webhookPreferences.configs.collectAsState()

    ButtonSwitchboard(
        routes = gestureRoutes,
        musicEnabled = platform.isAndroid,
        webhookConfigFor = { webhookConfigs[it] ?: IndexWebhookConfig() },
        onSetRoute = viewModel::setGestureRoute,
        onOpenWebhookSettings = { webhookViewModel.openDialog(it) },
        onDisableWebhook = { webhookPreferences.setEnabled(it, false) },
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
fun IndexAgentActionsSection(coreNav: CoreNav, viewModel: SettingsViewModel, showHeader: Boolean = true) {
    val actions by viewModel.indexActions.collectAsState()
    val httpMcpDisabledReason by viewModel.httpMcpDisabledReason.collectAsState()
    val plugins by viewModel.plugins.collectAsState()
    val httpMcpServers by viewModel.httpMcpServers.collectAsState()
    val availableNoteProviders by viewModel.availableNoteProviders.collectAsState()
    val availableReminderProviders by viewModel.availableReminderProviders.collectAsState()
    val configurablePlugins by viewModel.configurablePlugins.collectAsState()

    LaunchedEffect(Unit) { viewModel.refreshAvailableProviders() }
    IndexActionsSection(
        showHeader = showHeader,
        actions = actions,
        plugins = plugins,
        httpMcpServers = httpMcpServers,
        httpMcpDisabledReason = httpMcpDisabledReason,
        availableNoteProviders = availableNoteProviders,
        availableReminderProviders = availableReminderProviders,
        configurablePlugins = configurablePlugins,
        onSetActionEnabled = viewModel::setActionEnabled,
        onSetPluginGrantEnabled = viewModel::setPluginGrantEnabled,
        onSetHttpServerEnabled = viewModel::setHttpServerEnabled,
        onSaveHttpServer = viewModel::saveHttpServer,
        onDeleteHttpServer = viewModel::deleteHttpServer,
        onAddIntegration = { coreNav.navigateTo(RingRoutes.AddIntegration) },
        onConfigurePlugin = { uuid, title ->
            coreNav.navigateTo(CommonRoutes.WatchappSettingsRoute(uuid = uuid.toString(), title = title))
        },
        onOpenBeeperContacts = viewModel::showContactsDialog,
        onGetMoreActions = {},
        onProvidersChanged = viewModel::refreshAvailableProviders,
    )
}
