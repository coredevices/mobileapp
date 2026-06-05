package coredevices.ring.ui.screens.settings

import BugReportButton
import CoreNav
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coreapp.util.generated.resources.back
import coreapp.util.generated.resources.ring_wireframe
import coreapp.util.generated.resources.settings
import coredevices.ring.agent.builtin_servlets.notes.NoteProvider
import coredevices.ring.agent.builtin_servlets.reminders.ReminderProvider
import coredevices.ring.agent.integrations.GTasksIntegration
import coredevices.ring.agent.integrations.NotionIntegration
import coredevices.ring.data.NoteShortcutType
import coredevices.ring.database.MusicControlMode
import coredevices.ring.database.Preferences
import coredevices.ring.database.SecondaryMode
import coredevices.ring.encryption.EncryptionSetupState
import coredevices.ring.encryption.KeyStorageStatus
import coredevices.ring.external.indexwebhook.IndexWebhookSettingsDialog
import coredevices.ring.external.indexwebhook.IndexWebhookSettingsViewModel
import coredevices.ring.ui.PreviewWrapper
import coredevices.ring.ui.components.QrCodeImage
import coredevices.ring.ui.components.SectionHeader
import coredevices.ring.ui.navigation.RingRoutes
import coredevices.ring.ui.viewmodel.SettingsViewModel
import coredevices.ring.ui.viewmodel.pickZipFile
import coredevices.ui.M3Dialog
import coredevices.ui.ModelDownloadDialog
import coredevices.ui.SignInDialog
import coredevices.util.Platform
import coredevices.util.isAndroid
import coredevices.util.isIOS
import coredevices.util.rememberUiContext
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
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
    val webhookIsLinked = !webhookUrl.isNullOrBlank()
    val webhookDialogOpen by webhookViewModel.dialogOpen.collectAsState()
    val currentRingFirmware by viewModel.currentRingFirmware.collectAsStateWithLifecycle()
    val currentRing by viewModel.currentRingName.collectAsStateWithLifecycle()
    val currentRingPaired = viewModel.ringPaired.collectAsStateWithLifecycle()
    val panicPending by viewModel.panicPending.collectAsStateWithLifecycle()
    val ringPaired by remember { derivedStateOf { currentRingPaired.value != null } }
    val accountUsername by viewModel.username.collectAsStateWithLifecycle()
    val preferences = koinInject<Preferences>()
    val musicControlMode by viewModel.musicControlMode.collectAsState()
    val secondaryMode by viewModel.secondaryMode.collectAsState()
    val noteShortcut by viewModel.noteShortcut.collectAsState()
    var showSignInDialog by remember { mutableStateOf(false) }
    var showBackupDialog by remember { mutableStateOf(false) }
    val availableNoteProviders by viewModel.availableNoteProviders.collectAsState()
    val availableReminderProviders by viewModel.availableReminderProviders.collectAsState()

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
        )
    }
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
                        modifier = Modifier.clickable(enabled = currentRingFirmware != null && !panicPending) {
                            viewModel.panicRing()
                        },
                        headlineContent = { Text("Panic Ring", color = Color.Red) }
                    )
                }
                item {
                    ListItem(
                        modifier = Modifier.clickable {
                            coreNav.navigateTo(RingRoutes.RingSyncInspector)
                        },
                        headlineContent = { Text("Ring Sync Inspector") }
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
        }
    }
}

@Composable
fun NoteShortcutDialog(
    availableNoteProviders: List<NoteProvider>,
    availableReminderProviders: List<ReminderProvider>,
    currentShortcut: NoteShortcutType,
    onShortcutSelected: (NoteShortcutType) -> Unit,
    onDismissRequest: () -> Unit
) {
    var targetShortcut by remember { mutableStateOf(currentShortcut) }
    val options = remember {
        buildList {
            add(NoteShortcutType.SendToMe)
            availableNoteProviders.forEach {
                add(NoteShortcutType.SendToNoteProvider(it))
            }
            availableReminderProviders.forEach {
                add(NoteShortcutType.SendToReminderProvider(it))
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
    val keyStorageStatus by viewModel.keyStorageStatus.collectAsState()
    val encryptionKeyStatus by viewModel.encryptionKeyStatus.collectAsState()
    val encryptionKeyLoading by viewModel.encryptionKeyLoading.collectAsState()
    val debugDetailsEnabled by viewModel.debugDetailsEnabled.collectAsState()
    val uiContext = rememberUiContext()
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var deleteInput by remember { mutableStateOf("") }
    var showDeleteLocalConfirm by remember { mutableStateOf(false) }
    var deleteLocalInput by remember { mutableStateOf("") }
    var showOverwriteKeyConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.loadBackupCount()
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
                Text(buildAnnotatedString {
                    append("An encryption key already exists. Generating a new one will overwrite it.\n\n")
                    withStyle(SpanStyle(color = Color.Red, fontWeight = FontWeight.Bold)) {
                        append("Previously encrypted backups (cloud sync or local) will be permanently lost.")
                    }
                })
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
    EncryptionKeyResultDialogs(viewModel)
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
            if (debugDetailsEnabled) {
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
            }


            // Delete backup
            ListItem(
                modifier = Modifier.clickable(enabled = !backupLoading) {
                    showDeleteConfirm = true
                },
                headlineContent = {
                    Text("Delete cloud backup", color = MaterialTheme.colorScheme.error)
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

            // Generate encryption key. A key already exists (here or on
            // another device) unless status is NoKeyStored — regenerating it
            // orphans prior backups, so warn and gate behind debug details.
            val keyExists = keyStorageStatus != KeyStorageStatus.NoKeyStored
            ListItem(
                modifier = Modifier.clickable(enabled = !encryptionKeyLoading && uiContext != null && (!keyExists || debugDetailsEnabled)) {
                    if (keyExists) {
                        showOverwriteKeyConfirm = true
                    } else {
                        uiContext?.let { viewModel.generateAndStoreKey(it) }
                    }
                },
                headlineContent = { Text("Generate Encryption Key") },
                supportingContent = {
                    Text(
                        encryptionKeyStatus?.let { AnnotatedString(it) } ?: when (keyStorageStatus) {
                            KeyStorageStatus.KeyLocallyAvailable -> buildAnnotatedString {
                                append("Key exists")
                                if (debugDetailsEnabled) withStyle(SpanStyle(color = Color.Red)) {
                                    append(" — tap to regenerate")
                                }
                            }
                            KeyStorageStatus.KeyGeneratedBefore -> buildAnnotatedString {
                                append("A key was generated before but isn't on this device — restore it instead")
                                if (debugDetailsEnabled) withStyle(SpanStyle(color = Color.Red)) {
                                    append(", or tap to regenerate")
                                }
                            }
                            KeyStorageStatus.NoKeyStored -> AnnotatedString("Create encryption key")
                        }
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
            val encryptionStatus by viewModel.encryptionStatus.collectAsState()
            val enablingEncryption by viewModel.enablingEncryption.collectAsState()

            if (keyStorageStatus == KeyStorageStatus.KeyLocallyAvailable) {
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                ListItem(
                    modifier = Modifier.clickable(enabled = !enablingEncryption && (useEncryption || uiContext != null)) {
                        if (!useEncryption) {
                            uiContext?.let { viewModel.requestEnableEncryption(it) }
                        } else {
                            viewModel.disableEncryption()
                        }
                    },
                    headlineContent = {
                        Text(if (useEncryption) "Disable Encryption" else "Enable Encryption")
                    },
                    supportingContent = {
                        Text(
                            encryptionStatus
                                ?: if (useEncryption) "All new uploads are encrypted"
                                else "Encrypt all recordings before uploading to cloud"
                        )
                    },
                    trailingContent = {
                        if (enablingEncryption) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        }
                    }
                )
            }
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            // --- Danger zone ---
            if (debugDetailsEnabled) {
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
}

/**
 * The "key not backed up" warning and generated-key QR/backup sheet,
 * driven by [SettingsViewModel] so any host of the key flow can render them.
 */
@Composable
fun EncryptionKeyResultDialogs(viewModel: SettingsViewModel) {
    val showKeyNotBackedUpDialog by viewModel.showKeyNotBackedUpDialog.collectAsState()
    val generatedKey by viewModel.generatedKey.collectAsState()
    val clipboardManager = LocalClipboardManager.current

    if (showKeyNotBackedUpDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissKeyNotBackedUpDialog() },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = Color.Red) },
            title = { Text("Encryption key not backed up") },
            text = {
                Text(
                    buildAnnotatedString {
                        append("You're attempting to enable backup encryption without a confirmed keychain backup of your key, ")
                        append("please ensure you've saved your key either via QR code or copied it to your clipboard and saved it elsewhere.\n\n")
                        withStyle(SpanStyle(color = Color.Red, fontWeight = FontWeight.Bold)) {
                            append("If you lose this key, you will not be able to sync or restore anything from backups and will have to start fresh.")
                        }
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmEnableEncryption() }) {
                    Text("I confirm, my key is saved elsewhere")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissKeyNotBackedUpDialog() }) {
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
}

/**
 * Guided "turn on encryption" dialog, driven by
 * [SettingsViewModel.encryptionSetup] through generate or restore before
 * encryption is enabled, so it's reusable wherever the switch lives.
 */
@Composable
fun EncryptionSetupDialog(viewModel: SettingsViewModel) {
    val current by viewModel.encryptionSetup.collectAsState()
    if (current is EncryptionSetupState.Hidden) return

    val uiContext = rememberUiContext()
    val clipboardManager = LocalClipboardManager.current
    var pasted by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { viewModel.cancelEncryptionSetup() },
        title = { Text("Set up backup encryption") },
        text = {
            when (val s = current) {
                EncryptionSetupState.Hidden -> {}
                EncryptionSetupState.PromptGenerate -> Text(
                    "Generate an encryption key so only you can read your backups. " +
                        "You'll save a copy so you can restore on another device."
                )

                EncryptionSetupState.Generating -> Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("Generating your key and saving it to your password manager…")
                }

                is EncryptionSetupState.ShowKey -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Saved to your password manager. Save this key somewhere safe too — " +
                            "you'll need it to restore your backups and it can't be recovered.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        QrCodeImage(data = s.keyBase64, size = 220.dp)
                    }
                }

                EncryptionSetupState.Restoring -> Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("Looking for your key in your password manager…")
                }

                is EncryptionSetupState.PasteKey -> Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Your key was generated on another device and isn't in this " +
                            "password manager. Paste your backup key to continue."
                    )
                    OutlinedTextField(
                        value = pasted,
                        onValueChange = { pasted = it },
                        singleLine = true,
                        label = { Text("Encryption key") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    s.error?.let {
                        Text(
                            it,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                is EncryptionSetupState.Failed -> Text(
                    s.message,
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        confirmButton = {
            when (val s = current) {
                EncryptionSetupState.PromptGenerate -> TextButton(
                    enabled = uiContext != null,
                    onClick = { uiContext?.let { viewModel.generateKeyForSetup(it) } }
                ) { Text("Generate Key") }

                is EncryptionSetupState.ShowKey -> TextButton(
                    onClick = { viewModel.completeEncryptionSetup() }
                ) { Text("Done") }

                is EncryptionSetupState.PasteKey -> TextButton(
                    enabled = pasted.isNotBlank(),
                    onClick = { viewModel.restoreFromPastedKey(pasted) }
                ) { Text("Restore") }

                is EncryptionSetupState.Failed -> TextButton(
                    enabled = uiContext != null,
                    onClick = { uiContext?.let { viewModel.generateKeyForSetup(it) } }
                ) { Text("Retry") }

                else -> {}
            }
        },
        dismissButton = {
            when (val s = current) {
                is EncryptionSetupState.ShowKey -> TextButton(
                    onClick = { clipboardManager.setText(AnnotatedString(s.keyBase64)) }
                ) { Text("Copy Key") }

                EncryptionSetupState.Generating,
                EncryptionSetupState.Restoring,
                EncryptionSetupState.Hidden -> {}

                else -> TextButton(
                    onClick = { viewModel.cancelEncryptionSetup() }
                ) { Text("Cancel") }
            }
        }
    )
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
        IntegrationItem(
            title = "Built-in",
            hasReminder = true,
            hasNotes = true,
            selectedReminderProvider = currentReminderProvider == ReminderProvider.BuiltIn,
            selectedNoteProvider = currentNoteProvider == NoteProvider.Builtin,
            onSelectReminderProvider = { preferences.setReminderProvider(ReminderProvider.BuiltIn) },
            onSelectNoteProvider = { preferences.setNoteProvider(NoteProvider.Builtin) }
        )
        if (platform.isIOS) {
            IntegrationItem(
                title = "iOS Reminders",
                hasReminder = true,
                hasNotes = false,
                selectedReminderProvider = currentReminderProvider == ReminderProvider.IOSReminders,
                selectedNoteProvider = false,
                onSelectReminderProvider = { preferences.setReminderProvider(ReminderProvider.IOSReminders) },
                onSelectNoteProvider = {}
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
            var showNotionPageDialog by remember { mutableStateOf(false) }
            if (showNotionPageDialog) {
                NotionPageDialog(onDismiss = { showNotionPageDialog = false })
            }
            IntegrationItem(
                title = NotionIntegration.DEFINITION.title,
                hasReminder = false,
                hasNotes = true,
                selectedReminderProvider = false,
                selectedNoteProvider = currentNoteProvider == NoteProvider.Notion,
                onSelectReminderProvider = {},
                onSelectNoteProvider = { preferences.setNoteProvider(NoteProvider.Notion) },
                onConfigure = { showNotionPageDialog = true }
            )
        }
    }
}

/**
 * Lets the user pick which Notion page to-do block is placed
 */
@Composable
fun NotionPageDialog(onDismiss: () -> Unit) {
    val notion = koinInject<NotionIntegration>()
    val scope = rememberCoroutineScope()
    var pages by remember { mutableStateOf<List<NotionIntegration.NotionPage>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            val loaded = notion.listPages()
            selectedId = notion.selectedPageId() ?: loaded.firstOrNull()?.id
            pages = loaded
        } catch (e: Throwable) {
            error = e.message ?: "Failed to load pages"
        }
    }

    M3Dialog(
        onDismissRequest = onDismiss,
        title = { Text("Notion Page") },
        buttons = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
            TextButton(
                enabled = selectedId != null && !saving,
                onClick = {
                    val pageId = selectedId ?: return@TextButton
                    saving = true
                    scope.launch {
                        notion.selectPage(pageId)
                        onDismiss()
                    }
                }
            ) { Text("OK") }
        }
    ) {
        Column {
            Text(
                "Choose the page to place your notes' Todo list in.",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(12.dp))
            val loadedPages = pages
            when {
                error != null -> Text(error!!, color = MaterialTheme.colorScheme.error)
                loadedPages == null -> Box(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
                loadedPages.isEmpty() -> Text(
                    "No pages found. Give Index access to a page in Notion, then try again."
                )
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(loadedPages) { page ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedId = page.id }
                        ) {
                            RadioButton(
                                selected = selectedId == page.id,
                                onClick = { selectedId = page.id }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(page.title)
                        }
                    }
                }
            }
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
    onConfigure: (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(title)
        if (onConfigure != null) {
            IconButton(onClick = onConfigure, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = "Configure $title",
                    modifier = Modifier.size(20.dp)
                )
            }
        }
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
                imageResource(UtilRes.drawable.ring_wireframe),
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
