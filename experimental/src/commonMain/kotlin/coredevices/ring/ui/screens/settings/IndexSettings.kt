package coredevices.ring.ui.screens.settings

import BugReportButton
import CommonRoutes
import CoreNav
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.touchlab.kermit.Logger
import coreapp.util.generated.resources.back
import coreapp.util.generated.resources.settings
import coredevices.ring.agent.integrations.providers.IntegrationProvider
import coredevices.ring.agent.integrations.providers.IntegrationProviderRepository
import coredevices.ring.agent.integrations.providers.LegacyDestination
import coredevices.ring.data.NoteShortcutType
import coredevices.ring.external.indexwebhook.IndexWebhookSettingsViewModel
import coredevices.ring.ui.PreviewWrapper
import coredevices.ring.ui.components.SectionHeader
import coredevices.ring.ui.navigation.RingRoutes
import coredevices.ring.ui.theme.IndexTheme
import coredevices.ring.ui.viewmodel.SettingsViewModel
import coredevices.ui.M3Dialog
import coredevices.ui.SignInDialog
import coredevices.util.Permission
import coredevices.util.PermissionRequester
import coredevices.util.Platform
import coredevices.util.isAndroid
import coredevices.util.rememberUiContext
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import androidx.compose.runtime.produceState
import com.cactus.isCactusSupported
import coredevices.ring.ui.viewmodel.DiagnosticsState
import coredevices.util.CoreConfigHolder
import coredevices.util.models.ModelManager
import coredevices.util.transcription.PlatformSpeechRecognizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import coredevices.util.models.CactusSTTMode
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import coreapp.util.generated.resources.Res as UtilRes

@Composable
private fun RelinkDestinationsCard(destinations: List<LegacyDestination>) {
    val colors = IndexTheme.colors
    Card(
        colors = CardDefaults.cardColors(
            containerColor = colors.errorContainer,
            contentColor = colors.onErrorContainer,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Link, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    destinations.map { it.provider.title }.distinct().joinToString() + " needs re-linking",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Built-in integrations are being replaced by plugins and will be removed in an upcoming version. " +
                    "Relink each one as a plugin to keep sending to it.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(8.dp))
            destinations.forEach { destination ->
                Text(
                    "${destination.provider.title} · ${legacyDestinationLabel(destination.setting)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

private fun legacyDestinationLabel(setting: LegacyDestination.Setting): String = when (setting) {
    LegacyDestination.Setting.Notes -> "Notes"
    LegacyDestination.Setting.Reminders -> "Reminders"
    LegacyDestination.Setting.Shortcut -> "Notification shortcut"
}

// openUri throws if nothing can handle the link (no browser, DPC policy);
// don't let a help link tap crash the settings screen.
internal fun UriHandler.openUrlSafely(url: String) {
    try {
        openUri(url)
    } catch (e: Exception) {
        Logger.w(e) { "Failed to open URL: $url" }
    }
}

@Composable
fun IndexSettings(coreNav: CoreNav) {
    val viewModel = koinViewModel<SettingsViewModel>()
    val snackbarHostState = remember { SnackbarHostState() }
    val webhookViewModel = koinViewModel<IndexWebhookSettingsViewModel>()
    val debugDetailsEnabled by viewModel.debugDetailsEnabled.collectAsState()
    val showNoteShortcutDialog by viewModel.showNoteShortcutDialog.collectAsState()
    val autoDismissActionNotifications by viewModel.autoDismissActionNotifications.collectAsState()
    val pendingIntentScanEnabled by viewModel.pendingIntentScanEnabled.collectAsState()
    val scanByPairedAddress by viewModel.scanByPairedAddress.collectAsState()
    val diagnosticsState by viewModel.diagnosticsState.collectAsState()
    val platform = koinInject<Platform>()
    val providers = koinInject<IntegrationProviderRepository>()
    val coreConfigHolder = koinInject<CoreConfigHolder>()
    val coreConfig by coreConfigHolder.config.collectAsState()
    val modelManager = koinInject<ModelManager>()
    val platformSpeechRecognizer = koinInject<PlatformSpeechRecognizer>()
    val speechPermissionRequester = koinInject<PermissionRequester>()
    val speechUiContext = rememberUiContext()
    val speechScope = rememberCoroutineScope()
    val onDeviceSpeechSupported = remember { isCactusSupported() }
    val modelDownloadStatus by modelManager.modelDownloadStatus.collectAsState()
    val selectedSttModel = coreConfig.sttConfig.modelName
    val hasOfflineSpeechModels by produceState(false, modelDownloadStatus, selectedSttModel) {
        value = withContext(Dispatchers.Default) {
            val slug = selectedSttModel ?: modelManager.getRecommendedSTTModel().modelSlug
            slug in modelManager.getDownloadedSTTModelSlugs()
        }
    }
    val platformSttAvailable by produceState(false) {
        value = withContext(Dispatchers.Default) { platformSpeechRecognizer.isAvailable() }
    }
    val webhookUrl by webhookViewModel.webhookUrl.collectAsState()
    val configuredWebhookGesture by webhookViewModel.configuredGesture.collectAsState()
    val webhookIsLinked = !webhookUrl.isNullOrBlank()
    val currentRingFirmware by viewModel.currentRingFirmware.collectAsStateWithLifecycle()
    val currentRing by viewModel.currentRingName.collectAsStateWithLifecycle()
    val currentRingPaired = viewModel.ringPaired.collectAsStateWithLifecycle()
    val ringPaired by remember { derivedStateOf { currentRingPaired.value != null } }
    val accountUsername by viewModel.username.collectAsStateWithLifecycle()
    val noteShortcut by viewModel.noteShortcut.collectAsState()
    var showSignInDialog by remember { mutableStateOf(false) }
    var showBackupDialog by remember { mutableStateOf(false) }
    var showDiagnosticsDialog by remember { mutableStateOf(false) }
    val availableNoteProviders by viewModel.availableNoteProviders.collectAsState()
    val availableReminderProviders by viewModel.availableReminderProviders.collectAsState()
    val legacyDestinations by viewModel.legacyDestinations.collectAsState()

    val colors = IndexTheme.colors

    if (showSignInDialog) {
        SignInDialog(onDismiss = { showSignInDialog = false })
    }
    IndexWebhookSheetHost()
    if (showNoteShortcutDialog) {
        val shortcut = viewModel.noteShortcut.collectAsState()
        NoteShortcutDialog(
            availableNoteProviders = availableNoteProviders,
            availableReminderProviders = availableReminderProviders,
            currentShortcut = shortcut.value,
            onShortcutSelected = {
                viewModel.setNoteShortcut(it)
                viewModel.closeNoteShortcutDialog()
            },
            onDismissRequest = {
                viewModel.closeNoteShortcutDialog()
            }
        )
    }
    if (showBackupDialog) {
        BackupDialog(
            debugDetailsEnabled = debugDetailsEnabled,
            onDismiss = { showBackupDialog = false }
        )
    }
    if (showDiagnosticsDialog) {
        DiagnosticsDialog(
            coreNav = coreNav,
            viewModel = viewModel,
            onDismiss = { showDiagnosticsDialog = false }
        )
    }
    val scope = rememberCoroutineScope()

    Scaffold(
        containerColor = colors.surface,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = colors.primary,
                    navigationIconContentColor = colors.onSurface,
                    actionIconContentColor = colors.onSurface,
                ),
                navigationIcon = {
                    IconButton(onClick = coreNav::goBack) {
                        Icon(
                            Icons.AutoMirrored.Default.ArrowBack,
                            contentDescription = stringResource(coreapp.util.generated.resources.Res.string.back)
                        )
                    }
                },
                title = { Text(stringResource(UtilRes.string.settings)) },
                actions = {
                    BugReportButton(
                        coreNav,
                        pebble = false,
                        screenContext = mapOf(
                            "screen" to "Settings",
                            "username" to (accountUsername ?: "null")
                        )
                    )
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxHeight()
        ) {
            if (legacyDestinations.isNotEmpty()) {
                item { RelinkDestinationsCard(legacyDestinations) }
            }

            // Getting Started guide + FAQ — Index 01 is a new kind of device,
            // so steer everyone to the guide. Opens in the system browser.
            item {
                val uriHandler = LocalUriHandler.current
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = colors.primaryContainer,
                        contentColor = colors.onPrimaryContainer,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.AutoMirrored.Filled.MenuBook,
                                contentDescription = null
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "New to Index 01?",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "It's a new kind of gadget! A quick read goes a long way.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Inverted colors so the button stands out on the container card
                            Button(
                                onClick = { uriHandler.openUrlSafely("https://pbl.zip/index-guide") },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colors.onPrimaryContainer,
                                    contentColor = colors.primaryContainer,
                                ),
                                modifier = Modifier.weight(1f).height(40.dp)
                            ) {
                                Text("Getting Started Guide")
                                Spacer(Modifier.width(6.dp))
                                Icon(
                                    Icons.AutoMirrored.Filled.OpenInNew,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            OutlinedButton(
                                onClick = { uriHandler.openUrlSafely("https://pbl.zip/index-faq") },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = colors.onPrimaryContainer,
                                ),
                                border = BorderStroke(
                                    1.dp,
                                    colors.onPrimaryContainer.copy(alpha = 0.5f)
                                ),
                                modifier = Modifier.height(40.dp),
                            ) {
                                Text("FAQ")
                            }
                        }
                    }
                }
            }

            // Device card
            item {
                IndexDeviceListItem(
                    headline = when {
                        ringPaired && currentRing != null -> (currentRing ?: "")
                        ringPaired -> "Paired to Index 01"
                        else -> "No Ring Paired"
                    },
                    supporting = if (ringPaired) "Paired" else "Not paired",
                )
            }

            // --- Button Actions section ---
            item {
                SectionHeader(
                    title = "Ring Button",
                    modifier = Modifier.padding(top = 18.dp, bottom = 2.dp, start = 16.dp, end = 16.dp)
                )
            }
            item { RingButtonSection(coreNav) }

            // --- Actions section ---
            item { IndexAgentActionsSection(coreNav) }
            item {
                SectionHeader(
                    modifier = Modifier.padding(top = 22.dp, bottom = 2.dp, start = 16.dp, end = 16.dp),
                    title = "Speech Recognition",
                )
            }
            item {
                SpeechSection(
                    mode = coreConfig.sttConfig.mode,
                    spokenLanguage = coreConfig.sttConfig.spokenLanguage,
                    selectedModel = selectedSttModel,
                    onDeviceSupported = onDeviceSpeechSupported,
                    platformSttAvailable = platformSttAvailable,
                    hasOfflineModels = hasOfflineSpeechModels,
                    signedIn = accountUsername != null,
                    onSelectMode = { mode ->
                        coreConfigHolder.update(
                            coreConfig.copy(sttConfig = coreConfig.sttConfig.copy(mode = mode))
                        )
                        if (mode == CactusSTTMode.PlatformOnly) {
                            speechUiContext?.let { ctx ->
                                speechScope.launch {
                                    speechPermissionRequester.requestPermission(
                                        Permission.SpeechRecognizer,
                                        ctx,
                                    )
                                }
                            }
                        }
                    },
                    onSelectModeWithModel = { mode, modelSlug ->
                        coreConfigHolder.update(
                            coreConfig.copy(
                                sttConfig = coreConfig.sttConfig.copy(
                                    mode = mode,
                                    modelName = modelSlug,
                                )
                            )
                        )
                    },
                    onSelectModel = { modelSlug ->
                        coreConfigHolder.update(
                            coreConfig.copy(
                                sttConfig = coreConfig.sttConfig.copy(modelName = modelSlug)
                            )
                        )
                    },
                    onSelectLanguage = { language ->
                        coreConfigHolder.update(
                            coreConfig.copy(
                                sttConfig = coreConfig.sttConfig.copy(spokenLanguage = language)
                            )
                        )
                        platformSpeechRecognizer.releaseUnusedModels(language)
                    },
                    onRequireSignIn = { showSignInDialog = true },
                    onShowModelDownload = { coreNav.navigateTo(CommonRoutes.SpeechModelDownloadDialog) },
                )
            }
            item {
                SectionHeader(
                    modifier = Modifier.padding(top = 22.dp, bottom = 2.dp, start = 16.dp, end = 16.dp),
                    leadingContent = {
                        Text(
                            "Advanced",
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.primary
                        )
                    },
                    trailingContent = {}
                )
            }
            item {
                SettingsRow(
                    title = "Backup",
                    subtitle = "Sync, manage, or delete cloud backup",
                    onClick = { showBackupDialog = true },
                )
            }
            item {
                SettingsRow(
                    title = "Notification Shortcut",
                    subtitle = providers.shortcutDestinationTitle(noteShortcut) ?: "Email me",
                    onClick = viewModel::showNoteShortcutDialog,
                )
            }
            item {
                SettingsRow(
                    title = "Auto-dismiss Notifications",
                    subtitle = "Dismiss recording action notifications after 5 minutes",
                    onClick = viewModel::toggleAutoDismissActionNotifications,
                ) {
                    Switch(
                        checked = autoDismissActionNotifications,
                        onCheckedChange = { viewModel.toggleAutoDismissActionNotifications() }
                    )
                }
            }
            item {
                SettingsRow(
                    title = "Webhook",
                    subtitle = if (webhookIsLinked) "Configured, tap to modify" else "Not Linked",
                    onClick = {
                        configuredWebhookGesture
                            ?.let { webhookViewModel.openDialog(it) }
                            ?: webhookViewModel.openDialog()
                    },
                )
            }
            item {
                SettingsRow(
                    title = "Ring Firmware Version",
                    subtitle = currentRingFirmware ?: "Not yet seen device",
                )
            }
            item {
                SettingsRow(
                    title = "Diagnostics",
                    subtitle = "Record diagnostic information from your Index 01, only needed if support asks.",
                    enabled = ringPaired && diagnosticsState != DiagnosticsState.Running,
                    onClick = {
                        if (diagnosticsState != DiagnosticsState.Idle) viewModel.resetDiagnostics()
                        showDiagnosticsDialog = true
                    },
                )
            }

            if (debugDetailsEnabled || platform.isSecondaryProfile || coreConfig.disableRingBluetoothSync) {
                item {
                    SettingsRow(
                        title = "Disable Bluetooth Sync",
                        subtitle = "Turn on if Pebble is also installed on another profile of this phone. Index can't sync when two profiles connect to the ring at once.",
                        onClick = {
                            coreConfigHolder.update(coreConfig.copy(disableRingBluetoothSync = !coreConfig.disableRingBluetoothSync))
                        },
                    ) {
                        Switch(
                            checked = coreConfig.disableRingBluetoothSync,
                            onCheckedChange = { coreConfigHolder.update(coreConfig.copy(disableRingBluetoothSync = it)) }
                        )
                    }
                }
            }

            // --- Debug section ---
            item {
                HorizontalDivider(
                    color = colors.outlineVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            fun togglePendingIntentScanEnabled() {
                viewModel.togglePendingIntentScanEnabled()
                scope.launch { snackbarHostState.showSnackbar("Please force close the app to apply") }
            }
            item {
                SettingsRow(
                    title = "Use PendingIntent scan",
                    subtitle = if (platform.isAndroid) {
                        "Improves background reliability and battery consumption"
                    } else {
                        "Available on Android only"
                    },
                    enabled = platform.isAndroid,
                    onClick = ::togglePendingIntentScanEnabled,
                    trailing = {
                        Switch(
                            checked = pendingIntentScanEnabled,
                            onCheckedChange = { togglePendingIntentScanEnabled() }
                        )
                    }
                )
            }
            fun toggleScanByPairedAddress() {
                viewModel.toggleScanByPairedAddress()
                scope.launch { snackbarHostState.showSnackbar("Please force close the app to apply") }
            }
            item {
                SettingsRow(
                    title = "Scan by paired address",
                    subtitle = if (platform.isAndroid) {
                        "Improves latency and battery"
                    } else {
                        "Available on Android only"
                    },
                    enabled = platform.isAndroid,
                    onClick = ::toggleScanByPairedAddress,
                    trailing = {
                        Switch(
                            checked = scanByPairedAddress,
                            onCheckedChange = { toggleScanByPairedAddress() }
                        )
                    }
                )
            }
            item {
                SettingsRow(
                    title = "Enable Debug Details",
                    subtitle = "Version: ${viewModel.version}",
                    onClick = viewModel::toggleDebugDetailsEnabled,
                ) {
                    Switch(
                        checked = debugDetailsEnabled,
                        onCheckedChange = { viewModel.toggleDebugDetailsEnabled() }
                    )
                }
            }
            if (debugDetailsEnabled) {
                item {
                    SettingsRow(
                        title = "Restart Pre-emptive Transfer",
                        subtitle = "Available on iOS only".takeIf { platform.isAndroid },
                        enabled = !platform.isAndroid,
                        onClick = viewModel::restartPreemptiveTransfer,
                    )
                }
                item {
                    SettingsRow(
                        title = "Ring Sync Inspector",
                        onClick = { coreNav.navigateTo(RingRoutes.RingSyncInspector) },
                    )
                }
            }
            item {
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

/** Advanced / Debug list row. M3 [ListItem] paints its own `colorScheme.surface`, which would
 *  punch white bars through the Index page background. */
@Composable
internal fun SettingsRow(
    title: String,
    subtitle: String? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = IndexTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick == null) Modifier
                else Modifier.clickable(enabled = enabled, onClick = onClick)
            )
            .alpha(if (enabled) 1f else 0.38f)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, color = colors.onSurface)
            if (subtitle != null) {
                Text(subtitle, fontSize = 14.sp, lineHeight = 20.sp, color = colors.onSurfaceVariant)
            }
        }
        trailing?.invoke()
    }
}

@Composable
fun NoteShortcutDialog(
    availableNoteProviders: List<IntegrationProvider>,
    availableReminderProviders: List<IntegrationProvider>,
    currentShortcut: NoteShortcutType,
    onShortcutSelected: (NoteShortcutType) -> Unit,
    onDismissRequest: () -> Unit
) {
    var targetShortcut by remember { mutableStateOf(currentShortcut) }
    val options = remember {
        buildList {
            add(NoteShortcutType.SendToMe to "Email me")
            availableNoteProviders.forEach {
                add(NoteShortcutType.SendToNoteProvider(it.id) to it.notesTitle)
            }
            availableReminderProviders.forEach {
                add(NoteShortcutType.SendToReminderProvider(it.id) to it.remindersTitle)
            }
        }
    }
    M3Dialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Notification Shortcut") },
        buttons = {
            TextButton(onClick = onDismissRequest) {
                Text("Cancel")
            }
            TextButton(onClick = { onShortcutSelected(targetShortcut) }) {
                Text("OK")
            }
        }
    ) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(options.size) { index ->
                val (option, label) = options[index]
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { targetShortcut = option }
                ) {
                    RadioButton(
                        selected = targetShortcut == option,
                        onClick = { targetShortcut = option }
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(label)
                }
            }
        }
    }
}

@Composable
fun IndexDeviceListItem(
    headline: String,
    supporting: String,
    modifier: Modifier = Modifier,
) {
    val colors = IndexTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier.padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(44.dp)
                .border(2.dp, colors.outlineVariant, CircleShape)
        ) {
            Icon(
                Icons.Default.RadioButtonUnchecked,
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
        }
        Column {
            Text(headline, fontSize = 16.sp, color = colors.onSurface)
            Text(supporting, fontSize = 14.sp, color = colors.onSurfaceVariant)
        }
    }
}

@Preview
@Composable
fun PreviewDeviceListItem() {
    PreviewWrapper {
        IndexDeviceListItem(headline = "Pebble Index 0A1", supporting = "Paired")
    }
}
