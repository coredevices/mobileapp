package coredevices.ring.ui.screens.settings

import BugReportButton
import CoreNav
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coreapp.ring.generated.resources.Res
import coreapp.ring.generated.resources.ring_wireframe
import coreapp.util.generated.resources.back
import coreapp.util.generated.resources.settings
import coredevices.EnableExperimentalDevices
import coredevices.ring.agent.builtin_servlets.notes.NoteProvider
import coredevices.ring.agent.builtin_servlets.reminders.ReminderProvider
import coredevices.ring.data.NoteShortcutType
import coredevices.ring.agent.integrations.GTasksIntegration
import coredevices.ring.agent.integrations.NotionIntegration
import coredevices.ring.database.MusicControlMode
import coredevices.ring.database.Preferences
import coredevices.ring.database.PrimaryMode
import coredevices.ring.database.SecondaryMode
import coredevices.ring.external.indexwebhook.IndexWebhookSettingsDialog
import coredevices.ring.external.indexwebhook.IndexWebhookSettingsViewModel
import coredevices.ring.ui.PreviewWrapper
import coredevices.ring.ui.components.SectionHeader
import coredevices.ring.ui.navigation.RingRoutes
import kotlinx.coroutines.launch
import coredevices.ring.ui.components.QrCodeImage
import coredevices.ring.ui.viewmodel.pickZipFile
import coredevices.ring.ui.viewmodel.SettingsViewModel
import coredevices.ui.M3Dialog
import coredevices.util.rememberUiContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import coredevices.ui.ModelDownloadDialog
import coredevices.ui.SignInDialog
import coredevices.util.Platform
import coredevices.util.isAndroid
import coredevices.util.isIOS
import kotlinx.coroutines.flow.flow
import org.jetbrains.compose.resources.imageResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import coreapp.util.generated.resources.Res as UtilRes

@Composable
fun IndexSettings(coreNav: CoreNav) {
    val viewModel = koinViewModel<SettingsViewModel>()
    val webhookViewModel = koinViewModel<IndexWebhookSettingsViewModel>()
    val useCactusAgent by viewModel.useCactusAgent.collectAsState()
    val showModelDialog by viewModel.showModelDownloadDialog.collectAsState()
    val showMusicControlDialog by viewModel.showMusicControlDialog.collectAsState()
    val debugDetailsEnabled by viewModel.debugDetailsEnabled.collectAsState()
    val showContactsDialog by viewModel.showContactsDialog.collectAsState()
    val showSecondaryModeDialog by viewModel.showSecondaryModeDialog.collectAsState()
    val showNoteShortcutDialog by viewModel.showNoteShortcutDialog.collectAsState()
    val platform = koinInject<Platform>()
    val webhookUrl by webhookViewModel.webhookUrl.collectAsState()
    val webhookToken by webhookViewModel.authToken.collectAsState()
    val webhookIsLinked = !webhookUrl.isNullOrBlank() && !webhookToken.isNullOrBlank()
    val webhookDialogOpen by webhookViewModel.dialogOpen.collectAsState()
    val experimentalDevicesEnabled by koinInject<EnableExperimentalDevices>().enabled.collectAsState()
    val currentRingFirmware by viewModel.currentRingFirmware.collectAsStateWithLifecycle()
    val currentRing by viewModel.currentRingName.collectAsStateWithLifecycle()
    val currentRingPaired = viewModel.ringPaired.collectAsStateWithLifecycle()
    val ringPaired by remember { derivedStateOf { currentRingPaired.value != null } }
    val accountUsername by viewModel.username.collectAsStateWithLifecycle()
    val preferences = koinInject<Preferences>()
    val musicControlMode by viewModel.musicControlMode.collectAsState()
    val secondaryMode by viewModel.secondaryMode.collectAsState()
    val showPrimaryModeDialog by viewModel.showPrimaryModeDialog.collectAsState()
    val primaryMode by viewModel.primaryMode.collectAsState()
    val shareWithIndexAgent by viewModel.shareWithIndexAgent.collectAsState()
    val noteShortcut by viewModel.noteShortcut.collectAsState()
    var showSignInDialog by remember { mutableStateOf(false) }
    var showBackupDialog by remember { mutableStateOf(false) }

    if (showSignInDialog) {
        SignInDialog(onDismiss = { showSignInDialog = false })
    }
    if (showModelDialog != null) {
        ModelDownloadDialog(
            onDismissRequest = { success ->
                viewModel.onModelDownloadDialogDismissed(success)
            },
            models = setOf(showModelDialog!!)
        )
    }
    if (showContactsDialog && platform.isAndroid) {
        SettingsBeeperContactsDialog(
            onDismissRequest = viewModel::closeContactsDialog
        )
    }
    if (showMusicControlDialog) {
        val mode = viewModel.musicControlMode.collectAsState()
        MusicControlDialog(
            currentMode = mode.value,
            onModeSelected = { mode ->
                viewModel.setMusicControlMode(mode)
                viewModel.closeMusicControlDialog()
            },
            onDismissRequest = {
                viewModel.closeMusicControlDialog()
            }
        )
    }
    if (showSecondaryModeDialog) {
        val mode = viewModel.secondaryMode.collectAsState()
        SecondaryModeDialog(
            currentMode = mode.value,
            onModeSelected = {
                viewModel.setSecondaryMode(it)
                viewModel.closeSecondaryModeDialog()
            },
            onDismissRequest = {
                viewModel.closeSecondaryModeDialog()
            },
            webhookEnabled = webhookIsLinked,
            webhookShown = experimentalDevicesEnabled
        )
    }
    if (showPrimaryModeDialog) {
        PrimaryModeDialog(
            currentMode = primaryMode,
            webhookLinked = webhookIsLinked,
            shareWithIndexAgent = shareWithIndexAgent,
            onModeSelected = { newMode ->
                viewModel.setPrimaryMode(newMode)
                viewModel.closePrimaryModeDialog()
            },
            onShareToggle = { newValue ->
                viewModel.setShareWithIndexAgent(newValue)
            },
            onConfigureWebhook = {
                viewModel.closePrimaryModeDialog()
                webhookViewModel.openDialog()
            },
            onDismissRequest = {
                viewModel.closePrimaryModeDialog()
            },
        )
    }
    if (showNoteShortcutDialog) {
        val shortcut = viewModel.noteShortcut.collectAsState()
        NoteShortcutDialog(
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
    if (webhookDialogOpen) {
        IndexWebhookSettingsDialog(webhookViewModel)
    }
    if (showBackupDialog) {
        BackupDialog(
            viewModel = viewModel,
            onDismiss = { showBackupDialog = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = coreNav::goBack) {
                        Icon(
                            Icons.AutoMirrored.Default.ArrowBack,
                            contentDescription = stringResource(coreapp.util.generated.resources.Res.string.back)
                        )
                    }
                },
                title = {
                    Text(
                        stringResource(UtilRes.string.settings),
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                actions = {
                    BugReportButton(
                        coreNav,
                        pebble = false,
                        screenContext = mapOf(
                            "screen" to "Settings",
                            "useCactusAgent" to useCactusAgent.toString(),
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
            // Device card
            item {
                IndexDeviceListItem(
                    headline = when {
                        ringPaired && currentRing != null -> (currentRing ?: "")
                        ringPaired -> "Paired to Index 01"
                        else -> "No Ring Paired"
                    },
                    buttons = {
                        val text = when {
                            accountUsername == null -> "Log in before pairing"
                            ringPaired -> "Pair different device"
                            else -> "Pair"
                        }
                        Button(
                            onClick = {
                                if (accountUsername == null) {
                                    showSignInDialog = true
                                } else {
                                    coreNav.navigateTo(RingRoutes.RingPairing)
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(text)
                        }
                    },
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            // --- Button Actions section ---
            item {
                SectionHeader(
                    leadingContent = {
                        Text(
                            "Button Actions",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    },
                    trailingContent = {}
                )
            }
            item {
                val isAndroid = platform.isAndroid
                ListItem(
                    modifier = Modifier.clickable(enabled = isAndroid) {
                        viewModel.showMusicControlDialog()
                    },
                    headlineContent = {
                        Text(
                            if (isAndroid) "Music Play/Pause" else "Music Play/Pause (Only on Android)",
                            color = if (isAndroid) MaterialTheme.colorScheme.onSurface
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                    },
                    supportingContent = {
                        Text(
                            when {
                                !isAndroid -> "Not available on this platform"
                                else -> when (musicControlMode) {
                                    MusicControlMode.Disabled -> "Disabled"
                                    MusicControlMode.SingleClick -> "Single click: Play/Pause · Double click: Next track"
                                    MusicControlMode.DoubleClick -> "Double click: Play/Pause · Triple click: Next track"
                                }
                            },
                            color = if (isAndroid) MaterialTheme.colorScheme.onSurfaceVariant
                                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                        )
                    }
                )
            }
            if (platform.isIOS && experimentalDevicesEnabled) {
                item {
                    ListItem(
                        modifier = Modifier.clickable {
                            viewModel.showPrimaryModeDialog()
                        },
                        headlineContent = { Text("Single click and hold") },
                        supportingContent = {
                            Text(
                                when (primaryMode) {
                                    PrimaryMode.IndexAgent -> "Index Agent"
                                    PrimaryMode.Webhook -> if (webhookIsLinked) "Webhook"
                                                          else "Webhook (not configured)"
                                }
                            )
                        }
                    )
                }
            }
            item {
                ListItem(
                    modifier = Modifier.clickable {
                        viewModel.showSecondaryModeDialog()
                    },
                    headlineContent = { Text("Double click and hold") },
                    supportingContent = {
                        Text(
                            when (secondaryMode) {
                                SecondaryMode.Disabled -> "Disabled"
                                SecondaryMode.Search -> "Search"
                                SecondaryMode.IndexWebhook -> "Webhook"
                            }
                        )
                    }
                )
            }

            // --- Accounts section ---
            item {
                SectionHeader(
                    leadingContent = {
                        Text(
                            "Accounts",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    },
                    trailingContent = {
                        IconButton(
                            onClick = { coreNav.navigateTo(RingRoutes.AddIntegration) },
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add Account")
                        }
                    }
                )
            }
            item {
                AuthorizedIntegrations(preferences)
            }
            item {
                ListItem(
                    modifier = Modifier.clickable {
                        viewModel.showNoteShortcutDialog()
                    },
                    headlineContent = { Text("Notification Shortcut") },
                    supportingContent = {
                        Text(
                            when (noteShortcut) {
                                is NoteShortcutType.SendToMe -> "Email me"
                                is NoteShortcutType.SendToNoteProvider -> (noteShortcut as NoteShortcutType.SendToNoteProvider).provider.title
                                is NoteShortcutType.SendToReminderProvider -> (noteShortcut as NoteShortcutType.SendToReminderProvider).provider.title
                            }
                        )
                    }
                )
            }

            // --- Advanced section ---
            item {
                SectionHeader(
                    leadingContent = {
                        Text(
                            "Advanced",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    },
                    trailingContent = {}
                )
            }
            item {
                ListItem(
                    modifier = Modifier.clickable { showBackupDialog = true },
                    headlineContent = { Text("Backup") },
                    supportingContent = { Text("Sync, manage, or delete cloud backup") }
                )
            }
            item {
                ListItem(
                    modifier = Modifier.clickable {
                        coreNav.navigateTo(RingRoutes.McpSandboxSettings)
                    },
                    headlineContent = { Text("MCP & Tool Settings") },
                    supportingContent = {
                        Text("Configure MCP sandbox and built-in functionality")
                    }
                )
            }
            item {
                ListItem(
                    modifier = Modifier.clickable(onClick = viewModel::showContactsDialog, enabled = platform.isAndroid),
                    headlineContent = { Text("Beeper Contacts") },
                    supportingContent = {
                        if (platform.isAndroid) {
                            Text("Approve Index to send messages to specific contacts")
                        } else {
                            Text("Available on Android only")
                        }
                    }
                )
            }
            item {
                ListItem(
                    modifier = Modifier.clickable(onClick = viewModel::toggleCactusAgent),
                    headlineContent = { Text("Use Cactus Agent") },
                    supportingContent = {
                        Text("Experimental agent with enhanced capabilities")
                    },
                    trailingContent = {
                        Switch(
                            checked = useCactusAgent,
                            onCheckedChange = { viewModel.toggleCactusAgent() }
                        )
                    }
                )
            }
            if (experimentalDevicesEnabled) {
                item {
                    ListItem(
                        modifier = Modifier.clickable(onClick = webhookViewModel::openDialog),
                        headlineContent = { Text("Webhook") },
                        supportingContent = {
                            Text(
                                if (webhookIsLinked) "Configured, tap to modify"
                                else "Not Linked"
                            )
                        }
                    )
                }
            }
            item {
                ListItem(
                    headlineContent = { Text("Ring Firmware Version") },
                    supportingContent = {
                        Text(currentRingFirmware ?: "Not yet seen device")
                    }
                )
            }

            // --- Debug section ---
            item { HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp)) }
            item {
                ListItem(
                    modifier = Modifier.clickable { viewModel.toggleDebugDetailsEnabled() },
                    headlineContent = { Text("Enable Debug Details") },
                    supportingContent = {
                        Text("Version: ${viewModel.version}")
                    },
                    trailingContent = {
                        Switch(
                            checked = debugDetailsEnabled,
                            onCheckedChange = { viewModel.toggleDebugDetailsEnabled() }
                        )
                    }
                )
            }
            if (debugDetailsEnabled) {
                item {
                    ListItem(
                        modifier = Modifier.clickable {
                            coreNav.navigateTo(RingRoutes.RingSyncInspector)
                        },
                        headlineContent = { Text("Ring Sync Inspector") }
                    )
                }
                item {
                    ListItem(
                        modifier = Modifier.clickable {
                            coreNav.navigateTo(RingRoutes.RingDebug)
                        },
                        headlineContent = { Text("Ring Debug") }
                    )
                }
            }
            item {
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
fun MusicControlDialog(
    currentMode: MusicControlMode,
    onModeSelected: (MusicControlMode) -> Unit,
    onDismissRequest: () -> Unit
) {
    var targetMode by remember { mutableStateOf(currentMode) }
    M3Dialog(
        onDismissRequest = onDismissRequest,
        icon = { Icon(Icons.Default.Headphones, contentDescription = null) },
        title = { Text("Music Control Mode") },
        buttons = {
            TextButton(
                onClick = onDismissRequest
            ) {
                Text("Cancel")
            }
            TextButton(
                onClick = {
                    onModeSelected(targetMode)
                }
            ) {
                Text("OK")
            }
        }
    ) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item(MusicControlMode.Disabled) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            targetMode = MusicControlMode.Disabled
                        }
                ) {
                    RadioButton(
                        selected = targetMode == MusicControlMode.Disabled,
                        onClick = {
                            targetMode = MusicControlMode.Disabled
                        }
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Disabled")
                }
            }
            item(MusicControlMode.SingleClick) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            targetMode = MusicControlMode.SingleClick
                        }
                ) {
                    RadioButton(
                        selected = targetMode == MusicControlMode.SingleClick,
                        onClick = {
                            targetMode = MusicControlMode.SingleClick
                        }
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("Single click")
                        Text(
                            "Single click: Play/Pause. Double click: Next track.",
                            fontSize = 12.sp,
                        )
                    }
                }
            }
            item(MusicControlMode.DoubleClick) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            targetMode = MusicControlMode.DoubleClick
                        }
                ) {
                    RadioButton(
                        selected = targetMode == MusicControlMode.DoubleClick,
                        onClick = {
                            targetMode = MusicControlMode.DoubleClick
                        }
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("Double click")
                        Text(
                            "Double click: Play/Pause. Triple click: Next track.",
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SecondaryModeDialog(
    currentMode: SecondaryMode,
    onModeSelected: (SecondaryMode) -> Unit,
    onDismissRequest: () -> Unit,
    webhookEnabled: Boolean,
    webhookShown: Boolean,
) {
    var targetMode by remember { mutableStateOf(currentMode) }
    M3Dialog(
        onDismissRequest = onDismissRequest,
        icon = { Icon(Icons.Default.Search, contentDescription = null) },
        title = { Text("Secondary Mode") },
        buttons = {
            TextButton(
                onClick = onDismissRequest
            ) {
                Text("Cancel")
            }
            TextButton(
                onClick = {
                    onModeSelected(targetMode)
                }
            ) {
                Text("OK")
            }
        }
    ) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item(SecondaryMode.Disabled) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            targetMode = SecondaryMode.Disabled
                        }
                ) {
                    RadioButton(
                        selected = targetMode == SecondaryMode.Disabled,
                        onClick = {
                            targetMode = SecondaryMode.Disabled
                        }
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("Disabled")
                        Text(
                            "Double click & hold will be the same as a normal click.",
                            fontSize = 12.sp,
                        )
                    }
                }
            }
            item(SecondaryMode.Search) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            targetMode = SecondaryMode.Search
                        }
                ) {
                    RadioButton(
                        selected = targetMode == SecondaryMode.Search,
                        onClick = {
                            targetMode = SecondaryMode.Search
                        }
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("Search")
                        Text(
                            "Get web search results on a double click & hold.",
                            fontSize = 12.sp,
                        )
                    }
                }
            }
            if (webhookShown) {
                item(SecondaryMode.IndexWebhook) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = webhookEnabled) {
                                targetMode = SecondaryMode.IndexWebhook
                            }
                    ) {
                        RadioButton(
                            selected = targetMode == SecondaryMode.IndexWebhook,
                            onClick = {
                                targetMode = SecondaryMode.IndexWebhook
                            },
                            enabled = webhookEnabled
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("Webhook")
                            Text(
                                "Send recording data to your webhook on a double click & hold.",
                                fontSize = 12.sp,
                            )
                            if (!webhookEnabled) {
                                Text(
                                    "Configure webhook first.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PrimaryModeDialog(
    currentMode: PrimaryMode,
    webhookLinked: Boolean,
    shareWithIndexAgent: Boolean,
    onModeSelected: (PrimaryMode) -> Unit,
    onShareToggle: (Boolean) -> Unit,
    onConfigureWebhook: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    var targetMode by remember { mutableStateOf(currentMode) }
    var showInfoDialog by remember { mutableStateOf(false) }

    M3Dialog(
        onDismissRequest = onDismissRequest,
        icon = { Icon(Icons.Default.Settings, contentDescription = null) },
        title = { Text("Single Click and Hold") },
        buttons = {
            TextButton(onClick = onDismissRequest) { Text("Cancel") }
            TextButton(onClick = { onModeSelected(targetMode) }) { Text("OK") }
        }
    ) {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item(PrimaryMode.IndexAgent) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            targetMode = PrimaryMode.IndexAgent
                        }
                ) {
                    RadioButton(
                        selected = targetMode == PrimaryMode.IndexAgent,
                        onClick = { targetMode = PrimaryMode.IndexAgent },
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("Index Agent")
                        Text(
                            "Transcribe and send to the Index agent. This is the original behavior.",
                            fontSize = 12.sp,
                        )
                    }
                }
            }
            item(PrimaryMode.Webhook) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = webhookLinked) {
                            targetMode = PrimaryMode.Webhook
                        }
                ) {
                    RadioButton(
                        selected = targetMode == PrimaryMode.Webhook,
                        onClick = { targetMode = PrimaryMode.Webhook },
                        enabled = webhookLinked,
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("Webhook")
                        Text(
                            "Transcribe and send to your webhook.",
                            fontSize = 12.sp,
                        )
                        if (!webhookLinked) {
                            TextButton(onClick = onConfigureWebhook) {
                                Text("Configure Webhook")
                            }
                        }
                    }
                }
            }

            if (targetMode != PrimaryMode.IndexAgent) {
                item("share_toggle") {
                    Spacer(Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onShareToggle(!shareWithIndexAgent) }
                    ) {
                        Checkbox(
                            checked = shareWithIndexAgent,
                            onCheckedChange = onShareToggle,
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Share with Index agent")
                                Spacer(Modifier.width(4.dp))
                                IconButton(onClick = { showInfoDialog = true }) {
                                    Icon(Icons.Default.Info, contentDescription = "What does this do?")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showInfoDialog) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            confirmButton = {
                TextButton(onClick = { showInfoDialog = false }) { Text("Got it") }
            },
            title = { Text("Share with Index agent") },
            text = {
                Text(
                    "When on, your transcription also appears in the Index feed and the " +
                    "on-device agent processes it (creates notes, runs tools, and so on). " +
                    "When off, the transcription is sent only to your destination — it still " +
                    "shows in the feed as a raw transcript, but the agent does not run on it."
                )
            }
        )
    }
}

@Composable
fun NoteShortcutDialog(
    currentShortcut: NoteShortcutType,
    onShortcutSelected: (NoteShortcutType) -> Unit,
    onDismissRequest: () -> Unit
) {
    var targetShortcut by remember { mutableStateOf(currentShortcut) }
    val options = buildList {
        add(NoteShortcutType.SendToMe)
        NoteProvider.entries.forEach { add(NoteShortcutType.SendToNoteProvider(it)) }
        ReminderProvider.entries.forEach { add(NoteShortcutType.SendToReminderProvider(it)) }
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
                val option = options[index]
                val label = when (option) {
                    is NoteShortcutType.SendToMe -> "Email me"
                    is NoteShortcutType.SendToNoteProvider -> option.provider.title
                    is NoteShortcutType.SendToReminderProvider -> option.provider.title
                }
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
fun BackupDialog(
    viewModel: SettingsViewModel,
    onDismiss: () -> Unit
) {
    val userId by viewModel.userId.collectAsState()
    val syncing by viewModel.syncingFeedHistory.collectAsState()
    val syncStatus by viewModel.syncStatus.collectAsState()
    val backupDownloading by viewModel.backupDownloading.collectAsState()
    val backupDownloadStatus by viewModel.backupDownloadStatus.collectAsState()
    val importing by viewModel.importing.collectAsState()
    val importStatus by viewModel.importStatus.collectAsState()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val backupCount by viewModel.backupCount.collectAsState()
    val backupLoading by viewModel.backupLoading.collectAsState()
    val backupStatus by viewModel.backupStatus.collectAsState()
    val backupEnabled by viewModel.backupEnabled.collectAsState()
    val hasLocalKey by viewModel.hasLocalKey.collectAsState()
    val encryptionKeyStatus by viewModel.encryptionKeyStatus.collectAsState()
    val encryptionKeyLoading by viewModel.encryptionKeyLoading.collectAsState()
    val generatedKey by viewModel.generatedKey.collectAsState()
    val uiContext = rememberUiContext()
    val clipboardManager = LocalClipboardManager.current
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var deleteInput by remember { mutableStateOf("") }
    var showDeleteLocalConfirm by remember { mutableStateOf(false) }
    var deleteLocalInput by remember { mutableStateOf("") }
    var showOverwriteKeyConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.loadBackupCount()
        viewModel.checkLocalKey()
    }
    DisposableEffect(Unit) {
        onDispose {
            viewModel.clearBackupStatus()
            viewModel.clearEncryptionKeyStatus()
        }
    }

    if (showOverwriteKeyConfirm) {
        AlertDialog(
            onDismissRequest = { showOverwriteKeyConfirm = false },
            title = { Text("Overwrite Encryption Key?") },
            text = {
                Text("An encryption key already exists. Generating a new one will overwrite it. Previously encrypted backups will not be decryptable with the new key.")
            },
            confirmButton = {
                TextButton(onClick = {
                    showOverwriteKeyConfirm = false
                    uiContext?.let { viewModel.generateAndStoreKey(it) }
                }) {
                    Text("Overwrite", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showOverwriteKeyConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
    if (generatedKey != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearGeneratedKey() },
            title = { Text("Encryption Key Backup") },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Save this key securely. You will need it to decrypt your backups.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        QrCodeImage(
                            data = generatedKey!!,
                            size = 220.dp
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    clipboardManager.setText(AnnotatedString(generatedKey!!))
                }) {
                    Text("Copy Key")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.clearGeneratedKey() }) {
                    Text("Done")
                }
            }
        )
    }
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false; deleteInput = "" },
            title = { Text("Delete Backup") },
            text = {
                Column {
                    Text(
                        "This will permanently delete all your cloud backup data. This cannot be undone.",
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.height(16.dp))
                    Text("Type \"delete\" to confirm:")
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = deleteInput,
                        onValueChange = { deleteInput = it },
                        singleLine = true,
                        placeholder = { Text("delete") }
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = deleteInput.equals("delete", ignoreCase = true) && !backupLoading,
                    onClick = {
                        viewModel.deleteBackup()
                        showDeleteConfirm = false
                        deleteInput = ""
                    }
                ) {
                    Text("Delete", color = if (deleteInput.equals("delete", ignoreCase = true)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false; deleteInput = "" }) {
                    Text("Cancel")
                }
            }
        )
    }
    if (showDeleteLocalConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteLocalConfirm = false; deleteLocalInput = "" },
            title = { Text("Delete Local Feed") },
            text = {
                Column {
                    Text(
                        "This will delete all recordings from the local feed on this device. Cloud backup is not affected. You can restore from cloud with Sync or from a backup zip with Import.",
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.height(16.dp))
                    Text("Type \"delete\" to confirm:")
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = deleteLocalInput,
                        onValueChange = { deleteLocalInput = it },
                        singleLine = true,
                        placeholder = { Text("delete") }
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = deleteLocalInput.equals("delete", ignoreCase = true) && !backupLoading,
                    onClick = {
                        viewModel.deleteLocalFeed()
                        showDeleteLocalConfirm = false
                        deleteLocalInput = ""
                    }
                ) {
                    Text("Delete", color = if (deleteLocalInput.equals("delete", ignoreCase = true)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteLocalConfirm = false; deleteLocalInput = "" }) {
                    Text("Cancel")
                }
            }
        )
    }

    M3Dialog(
        onDismissRequest = onDismiss,
        title = { Text("Backup") },
        buttons = {
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.verticalScroll(rememberScrollState())
        ) {
            // Backup count
            ListItem(
                headlineContent = { Text("Items in backup") },
                supportingContent = {
                    if (backupLoading && backupCount == null) {
                        Text("Loading...")
                    } else {
                        Column {
                            Text("${backupCount ?: "—"} recordings across all devices")
                            if (userId != null) {
                                Text("ID: $userId", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                },
                trailingContent = {
                    if (backupLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                }
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            // Backup enabled toggle
            ListItem(
                modifier = Modifier.clickable { viewModel.setBackupEnabled(!backupEnabled) },
                headlineContent = { Text("Backup enabled") },
                supportingContent = { Text("Automatically sync recordings to cloud") },
                trailingContent = {
                    Switch(
                        checked = backupEnabled,
                        onCheckedChange = { viewModel.setBackupEnabled(it) }
                    )
                }
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            // Sync now
            ListItem(
                modifier = Modifier.clickable(enabled = !syncing) {
                    viewModel.downloadFeedHistory()
                },
                headlineContent = { Text("Sync now") },
                supportingContent = {
                    Text(syncStatus ?: "Upload local & download cloud recordings")
                },
                trailingContent = {
                    if (syncing) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                }
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            // Download full backup
            ListItem(
                modifier = Modifier.clickable(enabled = !backupDownloading && !syncing && uiContext != null) {
                    uiContext?.let { viewModel.downloadFullBackup(it) }
                },
                headlineContent = { Text("Download Full Backup") },
                supportingContent = {
                    Text(backupDownloadStatus ?: "Download all recordings and data as a zip file")
                },
                trailingContent = {
                    if (backupDownloading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                }
            )

            // Import backup
            ListItem(
                modifier = Modifier.clickable(enabled = !importing && !backupDownloading && uiContext != null) {
                    scope.launch {
                        val path = uiContext?.let { pickZipFile(it) }
                        if (path != null) {
                            viewModel.importBackup(path)
                        }
                    }
                },
                headlineContent = { Text("Import Backup") },
                supportingContent = {
                    Text(importStatus ?: "Restore recordings from a backup zip file")
                },
                trailingContent = {
                    if (importing) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                }
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            // Delete backup
            ListItem(
                modifier = Modifier.clickable(enabled = !backupLoading) {
                    showDeleteConfirm = true
                },
                headlineContent = {
                    Text("Delete my backup", color = MaterialTheme.colorScheme.error)
                },
                supportingContent = {
                    Text(backupStatus ?: "Permanently delete all cloud data")
                }
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            // --- Encryption section ---
            Text(
                "Encryption",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            // Generate encryption key
            ListItem(
                modifier = Modifier.clickable(enabled = !encryptionKeyLoading && uiContext != null) {
                    if (hasLocalKey) {
                        showOverwriteKeyConfirm = true
                    } else {
                        uiContext?.let { viewModel.generateAndStoreKey(it) }
                    }
                },
                headlineContent = { Text("Generate Encryption Key") },
                supportingContent = {
                    Text(
                        encryptionKeyStatus
                            ?: if (hasLocalKey) "Key exists — tap to regenerate"
                            else "Create AES-256 encryption key"
                    )
                },
                trailingContent = {
                    if (encryptionKeyLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                }
            )

            // Read key from password manager
            ListItem(
                modifier = Modifier.clickable(enabled = !encryptionKeyLoading && uiContext != null) {
                    uiContext?.let { viewModel.readKeyFromCloudKeychain(it) }
                },
                headlineContent = { Text("Restore Key from Password Manager") },
                supportingContent = {
                    Text("Read key from Google Password Manager or iCloud Keychain")
                }
            )

            // Enable/disable encryption
            val useEncryption by viewModel.useEncryption.collectAsState()
            val migrationStatus by viewModel.migrationStatus.collectAsState()
            val migrating by viewModel.migrating.collectAsState()

            if (hasLocalKey) {
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                ListItem(
                    modifier = Modifier.clickable(enabled = !migrating) {
                        if (!useEncryption) {
                            viewModel.enableEncryption()
                        } else {
                            viewModel.disableEncryption()
                        }
                    },
                    headlineContent = {
                        Text(if (useEncryption) "Disable Encryption" else "Enable Encryption")
                    },
                    supportingContent = {
                        Text(
                            migrationStatus
                                ?: if (useEncryption) "All new uploads are encrypted"
                                else "Encrypt all recordings before uploading to cloud"
                        )
                    },
                    trailingContent = {
                        if (migrating) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        }
                    }
                )
            }
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            // --- Danger zone ---
            Text(
                "Danger Zone",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            ListItem(
                modifier = Modifier.clickable(enabled = !backupLoading) {
                    showDeleteLocalConfirm = true
                },
                headlineContent = {
                    Text("Delete local feed", color = MaterialTheme.colorScheme.error)
                },
                supportingContent = {
                    Text("Remove all recordings from this device (cloud unaffected)")
                }
            )
        }
    }
}

@Composable
fun AuthorizedIntegrations(preferences: Preferences) {
    val gTasks = koinInject<GTasksIntegration>()
    val notion = koinInject<NotionIntegration>()
    val gTasksAuth by flow { emit(gTasks.isAuthorized()) }.collectAsState(false)
    val notionAuth by flow { emit(notion.isAuthorized()) }.collectAsState(false)
    val currentReminderProvider by preferences.reminderProvider.collectAsState()
    val currentNoteProvider by preferences.noteProvider.collectAsState()

    val platform = koinInject<Platform>()

    Column(modifier = Modifier.fillMaxWidth()) {
        if (platform.isIOS) {
            IntegrationItem(
                title = "Built-in",
                hasReminder = false,
                hasNotes = true,
                selectedReminderProvider = false,
                selectedNoteProvider = currentNoteProvider == NoteProvider.Builtin,
                onSelectReminderProvider = {},
                onSelectNoteProvider = { preferences.setNoteProvider(NoteProvider.Builtin) }
            )
            IntegrationItem(
                title = "iOS",
                hasReminder = true,
                hasNotes = false,
                selectedReminderProvider = currentReminderProvider == ReminderProvider.Native,
                selectedNoteProvider = false,
                onSelectReminderProvider = { preferences.setReminderProvider(ReminderProvider.Native) },
                onSelectNoteProvider = {}
            )
        } else {
            IntegrationItem(
                title = "Built-in",
                hasReminder = true,
                hasNotes = true,
                selectedReminderProvider = currentReminderProvider == ReminderProvider.Native,
                selectedNoteProvider = currentNoteProvider == NoteProvider.Builtin,
                onSelectReminderProvider = { preferences.setReminderProvider(ReminderProvider.Native) },
                onSelectNoteProvider = { preferences.setNoteProvider(NoteProvider.Builtin) }
            )
        }
        if (gTasksAuth) {
            IntegrationItem(
                title = GTasksIntegration.DEFINITION.title,
                hasReminder = true,
                hasNotes = false,
                selectedReminderProvider = currentReminderProvider == ReminderProvider.GoogleTasks,
                selectedNoteProvider = false,
                onSelectReminderProvider = { preferences.setReminderProvider(ReminderProvider.GoogleTasks) },
                onSelectNoteProvider = {}
            )
        }
        if (notionAuth) {
            IntegrationItem(
                title = NotionIntegration.DEFINITION.title,
                hasReminder = false,
                hasNotes = true,
                selectedReminderProvider = false,
                selectedNoteProvider = currentNoteProvider == NoteProvider.Notion,
                onSelectReminderProvider = {},
                onSelectNoteProvider = { preferences.setNoteProvider(NoteProvider.Notion) }
            )
        }
    }
}

@Composable
fun IntegrationItem(
    title: String,
    hasReminder: Boolean,
    hasNotes: Boolean,
    selectedReminderProvider: Boolean,
    selectedNoteProvider: Boolean,
    onSelectReminderProvider: () -> Unit,
    onSelectNoteProvider: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(title)
        Spacer(Modifier.weight(1f))
        RadioButton(
            enabled = hasReminder,
            selected = selectedReminderProvider,
            onClick = {
                onSelectReminderProvider()
            }
        )
        Text("Reminders")
        Spacer(Modifier.width(16.dp))
        RadioButton(
            enabled = hasNotes,
            selected = selectedNoteProvider,
            onClick = {
                onSelectNoteProvider()
            }
        )
        Text("Notes")
    }
}

@Composable
fun IndexDeviceListItem(
    headline: String,
    buttons: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(8.dp)
        ) {
            Image(
                imageResource(Res.drawable.ring_wireframe),
                contentDescription = null,
                modifier = Modifier.size(110.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(headline, style = MaterialTheme.typography.titleLarge)
                Row {
                    buttons()
                }
            }
        }
    }
}

@Preview
@Composable
fun PreviewDeviceListItem() {
    PreviewWrapper {
        IndexDeviceListItem(
            headline = "Pebble Index 0A1",
            buttons = {
                Button(
                    onClick = {}
                ) {
                    Text("Unpair")
                }
            }
        )
    }
}
