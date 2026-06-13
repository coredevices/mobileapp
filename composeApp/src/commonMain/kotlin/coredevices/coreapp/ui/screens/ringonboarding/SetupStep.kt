package coredevices.coreapp.ui.screens.ringonboarding

import CoreNav
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cactus.isCactusSupported
import coredevices.pebble.ui.ModelDownloadPromptDialog
import coredevices.pebble.ui.SnackbarDisplay
import coredevices.ring.agent.builtin_servlets.notes.NoteProvider
import coredevices.ring.agent.builtin_servlets.reminders.ReminderProvider
import coredevices.ring.agent.integrations.GTasksIntegration
import coredevices.ring.agent.integrations.NotionIntegration
import coredevices.ring.database.MusicControlMode
import coredevices.ring.database.Preferences
import coredevices.ring.database.SecondaryMode
import coredevices.ring.ui.screens.settings.EncryptionKeyResultDialogs
import coredevices.ring.ui.screens.settings.EncryptionSetupDialog
import coredevices.ring.ui.screens.settings.GTasksDialog
import coredevices.ring.ui.screens.settings.NotionDialog
import coredevices.ring.ui.viewmodel.SettingsViewModel
import coredevices.ui.SignInDialog
import coredevices.util.CoreConfigHolder
import coredevices.util.STTConfig
import coredevices.util.emailOrNull
import coredevices.util.integrations.Integration
import coredevices.util.models.CactusSTTMode
import coredevices.util.models.ModelInfo
import coredevices.util.models.ModelManager
import coredevices.util.models.RecommendedModel
import coredevices.util.rememberUiContext
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import theme.AppTheme

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun SetupStep(
    coreNav: CoreNav,
    viewModel: SettingsViewModel,
    preferences: Preferences,
    snackbarDisplay: SnackbarDisplay,
    isAndroid: Boolean,
    onBack: () -> Unit,
    onExit: () -> Unit,
    onFinish: () -> Unit,
) {
    BackHandler { onBack() }
    val palette = LocalPalette.current
    val musicControlMode by viewModel.musicControlMode.collectAsState()
    val secondaryMode by viewModel.secondaryMode.collectAsState()
    val currentReminderProvider by preferences.reminderProvider.collectAsState()
    val currentNoteProvider by preferences.noteProvider.collectAsState()

    // Speech mode wiring — replicates the same checks as the existing
    // OfflineSpeechRecognition setting (sign-in required for non-Local,
    // model-download dialog for non-RemoteOnly when no offline models).
    val coreConfigHolder: CoreConfigHolder = koinInject()
    val coreConfig by coreConfigHolder.config.collectAsState()
    val modelManager: ModelManager = koinInject()
    val coreUser by Firebase.auth.authStateChanged
        .map { it?.emailOrNull }
        .distinctUntilChanged()
        .collectAsState(Firebase.auth.currentUser?.emailOrNull)
    val hasOfflineModels by produceState(false) {
        withContext(Dispatchers.Default) {
            value = modelManager.getDownloadedModelSlugs().any { it.startsWith("parakeet", false) }
        }
    }
    var pendingSTTModeDialog by remember { mutableStateOf<CactusSTTMode?>(null) }
    var showSignInDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    if (showSignInDialog) {
        SignInDialog(onDismiss = { showSignInDialog = false })
    }
    pendingSTTModeDialog?.let { pendingSTTMode ->
        val recommendedSTTModel = remember { modelManager.getRecommendedSTTModel() }
        val recommendedModel by produceState<ModelInfo?>(null) {
            withContext(Dispatchers.Default) {
                val models = modelManager.getAvailableSTTModels()
                value = models.firstOrNull { it.slug == recommendedSTTModel.modelSlug }
                    ?: run {
                        snackbarDisplay.showSnackbar("Error occurred. Please try again later.")
                        pendingSTTModeDialog = null
                        null
                    }
            }
        }
        val recommendedModelFinal = recommendedModel
        if (recommendedModelFinal != null) {
            ModelDownloadPromptDialog(
                isLite = recommendedSTTModel is RecommendedModel.Lite,
                downloadSizeInMb = recommendedModelFinal.sizeInMB,
                onGetRecommended = {
                    scope.launch {
                        if (!modelManager.downloadSTTModel(recommendedModelFinal, allowMetered = true)) {
                            snackbarDisplay.showSnackbar("Error starting download. Please try again later.")
                        } else {
                            coreConfigHolder.update(
                                coreConfig.copy(
                                    sttConfig = STTConfig(
                                        mode = pendingSTTMode,
                                        modelName = recommendedModelFinal.slug,
                                    )
                                )
                            )
                        }
                        pendingSTTModeDialog = null
                    }
                },
                onDismiss = { pendingSTTModeDialog = null },
            )
        }
    }
    val cactusSupported = remember { isCactusSupported() }
    val selectSpeechMode: (CactusSTTMode) -> Unit = { mode ->
        when {
            mode != CactusSTTMode.RemoteOnly && !cactusSupported -> {
                snackbarDisplay.showSnackbar("This device doesn't support local speech recognition")
            }
            mode != CactusSTTMode.LocalOnly && coreUser == null -> {
                snackbarDisplay.showSnackbar("You need to be signed in to use cloud speech recognition")
                showSignInDialog = true
            }
            mode != CactusSTTMode.RemoteOnly && !hasOfflineModels -> {
                pendingSTTModeDialog = mode
            }
            else -> {
                coreConfigHolder.update(
                    coreConfig.copy(sttConfig = coreConfig.sttConfig.copy(mode = mode))
                )
            }
        }
    }

    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(scrollState),
    ) {
        TopBarRow(onLeading = onBack, leadingIsClose = false, onTrailingClose = onExit)

        // Title — "Get Set Up"
        Column(modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 8.dp)) {
            Text(
                text = "Get Set Up",
                fontSize = 36.sp,
                lineHeight = 44.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.5).sp,
                color = palette.onSurface,
            )
        }

        // 1 — Speech recognition
        NumberedSection(num = 1, title = "Speech recognition") {
            CardContainer {
                SpeechModeChoice(
                    mode = coreConfig.sttConfig.mode,
                    onChange = selectSpeechMode,
                )
            }
        }

        // 2 — Notes & reminders
        NumberedSection(
            num = 2,
            title = "Notes & reminders",
            sub = "Set a default destination for recordings that contain notes or reminders.",
        ) {
            RoutingMatrix(
                preferences = preferences,
                isAndroid = isAndroid,
                currentReminderProvider = currentReminderProvider,
                currentNoteProvider = currentNoteProvider,
            )
        }

        // 3 — Music play/pause
        NumberedSection(
            num = 3,
            title = "Music play/pause",
            sub = if (isAndroid) "Single or double click without holding to play/pause music."
            else "Only available right now on Android.",
        ) {
            val musicEnabled = isAndroid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PressTile(
                    label = "Disabled",
                    pattern = PressPattern.None,
                    selected = musicControlMode == MusicControlMode.Disabled,
                    enabled = musicEnabled,
                    onClick = { viewModel.setMusicControlMode(MusicControlMode.Disabled) },
                    modifier = Modifier.weight(1f),
                )
                PressTile(
                    label = "Single",
                    pattern = PressPattern.Single,
                    selected = musicControlMode == MusicControlMode.SingleClick,
                    enabled = musicEnabled,
                    onClick = { viewModel.setMusicControlMode(MusicControlMode.SingleClick) },
                    modifier = Modifier.weight(1f),
                )
                PressTile(
                    label = "Double",
                    pattern = PressPattern.Double,
                    selected = musicControlMode == MusicControlMode.DoubleClick,
                    enabled = musicEnabled,
                    onClick = { viewModel.setMusicControlMode(MusicControlMode.DoubleClick) },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // 4 — Secondary action
        NumberedSection(
            num = 4,
            title = "Secondary action",
            sub = "Click before holding to perform a secondary voice action.",
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PressTile(
                    label = "Disabled",
                    pattern = PressPattern.None,
                    selected = secondaryMode == SecondaryMode.Disabled,
                    onClick = { viewModel.setSecondaryMode(SecondaryMode.Disabled) },
                    modifier = Modifier.weight(1f),
                )
                PressTile(
                    label = "Search",
                    pattern = PressPattern.ShortHold,
                    selected = secondaryMode == SecondaryMode.Search,
                    onClick = { viewModel.setSecondaryMode(SecondaryMode.Search) },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // 5 — Backups
        NumberedSection(
            num = 5,
            title = "Backups",
            sub = "Your recordings sync to the cloud so you can restore them on a new phone. " +
                    "Optionally encrypt them for extra privacy.",
        ) {
            BackupsContent(viewModel = viewModel)
        }

        // 6 — Try it out (live ring demo)
        NumberedSection(num = 6, title = "Try it out") {
            RingDemo(nav = coreNav)
        }

        Spacer(Modifier.height(28.dp))
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            PrimaryFilledButton(text = "Finish setup", onClick = onFinish)
            Text(
                "You can change these later in settings",
                fontSize = 13.sp,
                color = palette.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 6.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun BackupsContent(viewModel: SettingsViewModel) {
    val palette = LocalPalette.current
    val userId by viewModel.userId.collectAsState()
    val loggedIn = userId != null
    val backupEnabled by viewModel.backupEnabled.collectAsState()
    val useEncryption by viewModel.useEncryption.collectAsState()
    val encryptionStatus by viewModel.encryptionStatus.collectAsState()
    val enablingEncryption by viewModel.enablingEncryption.collectAsState()
    val uiContext = rememberUiContext()
    var showSignInDialog by remember { mutableStateOf(false) }

    // Encryption setup + key-result dialogs render against the app's regular
    // theme so they look like the rest of Settings, not the onboarding palette.
    AppTheme {
        EncryptionSetupDialog(viewModel)
        EncryptionKeyResultDialogs(viewModel)
    }
    if (showSignInDialog) {
        SignInDialog(onDismiss = { showSignInDialog = false })
    }

    if (!loggedIn) {
        CardContainer {
            Text(
                "Sign in to back up your recordings.",
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = palette.onSurface,
            )
            Spacer(Modifier.height(12.dp))
            PrimaryFilledButton(
                text = "Sign in",
                onClick = { showSignInDialog = true },
            )
        }
    } else {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = palette.surfaceContainerLowest,
            border = BorderStroke(1.dp, palette.outlineVariant),
        ) {
            Column {
                BackupSwitchRow(
                    title = "Cloud Backup",
                    subtitle = if (backupEnabled) "Recordings sync to the cloud"
                    else "Recordings stay on this device only",
                    checked = backupEnabled,
                    enabled = true,
                    onCheckedChange = { viewModel.setBackupEnabled(it) },
                )
                HorizontalDivider(thickness = 1.dp, color = palette.outlineVariant)
                BackupSwitchRow(
                    title = "Encrypt Backups",
                    subtitle = encryptionStatus
                        ?: if (useEncryption) "Only you can read your backups"
                        else if (backupEnabled) "Encrypt Index data so only you can read it"
                        else "Turn on Cloud Backup to set up encryption",
                    checked = useEncryption,
                    enabled = backupEnabled && !enablingEncryption &&
                            (useEncryption || uiContext != null),
                    onCheckedChange = { enable ->
                        if (enable) {
                            uiContext?.let { viewModel.beginEncryptionSetup(it) }
                        } else {
                            viewModel.disableEncryption()
                        }
                    },
                    trailing = if (enablingEncryption) {
                        {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = palette.primary,
                            )
                        }
                    } else null,
                )
            }
        }
    }
}

@Composable
private fun BackupSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    val palette = LocalPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = palette.onSurface,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = palette.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        if (trailing != null) {
            trailing()
        } else {
            Switch(
                checked = checked,
                enabled = enabled,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = palette.onPrimary,
                    checkedTrackColor = palette.primary,
                    uncheckedThumbColor = palette.outline,
                    uncheckedTrackColor = palette.surfaceContainerHigh,
                    uncheckedBorderColor = palette.outline,
                ),
            )
        }
    }
}

@Composable
private fun NumberedSection(
    num: Int,
    title: String,
    sub: String? = null,
    content: @Composable () -> Unit,
) {
    val palette = LocalPalette.current
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .shadow(
                        elevation = 8.dp,
                        shape = CircleShape,
                        ambientColor = palette.primary,
                        spotColor = palette.primary,
                    )
                    .clip(CircleShape)
                    .background(palette.primary),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = num.toString(),
                    color = palette.onPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.1).sp,
                color = palette.onSurface,
            )
        }
        if (sub != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = sub,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                letterSpacing = 0.1.sp,
                color = palette.onSurfaceVariant,
                modifier = Modifier.padding(start = 44.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun CardContainer(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = LocalPalette.current.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            content()
        }
    }
}

@Composable
internal fun SpeechModeChoice(
    mode: CactusSTTMode,
    onChange: (CactusSTTMode) -> Unit,
) {
    val options = listOf(
        Triple(CactusSTTMode.RemoteOnly, "Cloud only", "Best performance, requires connection"),
        Triple(CactusSTTMode.RemoteFirst, "Cloud, with local fallback", "Recommended, 670MB download"),
        Triple(CactusSTTMode.LocalFirst, "Local, cloud fallback", "670MB download"),
        Triple(CactusSTTMode.LocalOnly, "Local only", "Complete privacy, 670MB download"),
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (m, title, sub) ->
            SpeechRadioCard(
                title = title,
                sub = sub,
                selected = mode == m,
                onClick = { onChange(m) },
            )
        }
    }
}

@Composable
private fun SpeechRadioCard(
    title: String,
    sub: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val palette = LocalPalette.current
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (selected) palette.primaryContainer else palette.surfaceContainerLowest,
        border = BorderStroke(1.dp, if (selected) palette.primary else palette.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Radio circle
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(if (selected) Color.White else Color.Transparent)
                    .border(
                        width = if (selected) 6.dp else 2.dp,
                        color = if (selected) palette.primary else palette.outline,
                        shape = CircleShape,
                    ),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (selected) palette.onPrimaryContainer else palette.onSurface,
                )
                Spacer(Modifier.height(1.dp))
                Text(
                    text = sub,
                    fontSize = 12.sp,
                    color = palette.onSurfaceVariant,
                )
            }
        }
    }
}

private data class RoutingProvider(
    val name: String,
    val sub: String?,
    val supportsReminder: Boolean,
    val supportsNote: Boolean,
    val reminderValue: ReminderProvider?,
    val noteValue: NoteProvider?,
)

private data class AvailableProvider(
    val name: String,
    val sub: String?,
    val kindsLabel: String,
    val onAdd: () -> Unit,
)

@Composable
internal fun RoutingMatrix(
    preferences: Preferences,
    isAndroid: Boolean,
    currentReminderProvider: ReminderProvider,
    currentNoteProvider: NoteProvider,
) {
    val palette = LocalPalette.current
    val gTasks = koinInject<GTasksIntegration>()
    val notion = koinInject<NotionIntegration>()
    // Bumped after a sign-in dialog dismisses, to re-check auth state.
    var authRefresh by remember { mutableStateOf(0) }
    val gTasksAuth by produceAuthState(authRefresh, gTasks::isAuthorized)
    val notionAuth by produceAuthState(authRefresh, notion::isAuthorized)

    var showGTasksDialog by remember { mutableStateOf(false) }
    var showNotionDialog by remember { mutableStateOf(false) }
    if (showGTasksDialog) {
        GTasksDialog(onDismiss = {
            showGTasksDialog = false
            authRefresh++
        })
    }
    if (showNotionDialog) {
        NotionDialog(onDismiss = {
            showNotionDialog = false
            authRefresh++
        })
    }

    val visible = buildList {
        add(
            RoutingProvider(
                name = "Index",
                sub = "Built into Pebble app",
                supportsReminder = true,
                supportsNote = true,
                reminderValue = ReminderProvider.BuiltIn,
                noteValue = NoteProvider.Builtin,
            )
        )
        if (!isAndroid) {
            add(
                RoutingProvider(
                    name = "iPhone Reminders",
                    sub = "Built into iOS",
                    supportsReminder = true,
                    supportsNote = false,
                    reminderValue = ReminderProvider.IOSReminders,
                    noteValue = null,
                )
            )
        }
        if (gTasksAuth) {
            add(
                RoutingProvider(
                    name = "Google Tasks",
                    sub = null,
                    supportsReminder = true,
                    supportsNote = false,
                    reminderValue = ReminderProvider.GoogleTasks,
                    noteValue = null,
                )
            )
        }
        if (notionAuth) {
            add(
                RoutingProvider(
                    name = "Notion",
                    sub = "Append notes to a Notion page",
                    supportsReminder = false,
                    supportsNote = true,
                    reminderValue = null,
                    noteValue = NoteProvider.Notion,
                )
            )
        }
    }

    val available = buildList {
        if (!gTasksAuth) {
            add(
                AvailableProvider(
                    name = "Google Tasks",
                    sub = null,
                    kindsLabel = "REMINDERS",
                    onAdd = { showGTasksDialog = true },
                )
            )
        }
        if (!notionAuth) {
            add(
                AvailableProvider(
                    name = "Notion",
                    sub = "Append notes to a Notion page",
                    kindsLabel = "NOTES",
                    onAdd = { showNotionDialog = true },
                )
            )
        }
    }

    var showAdd by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = palette.surfaceContainerLowest,
        border = BorderStroke(1.dp, palette.outlineVariant),
    ) {
        Column {
            // Column headers
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(Modifier.weight(1f))
                Text(
                    "REMINDERS",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    color = palette.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(88.dp),
                )
                Text(
                    "NOTES",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    color = palette.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(88.dp),
                )
            }
            visible.forEach { p ->
                HorizontalDivider(thickness = 1.dp, color = palette.outlineVariant)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            p.name,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.onSurface,
                        )
                        if (p.sub != null) {
                            Text(
                                p.sub,
                                fontSize = 11.sp,
                                color = palette.onSurfaceVariant,
                            )
                        }
                    }
                    Box(modifier = Modifier.width(88.dp), contentAlignment = Alignment.Center) {
                        if (p.supportsReminder && p.reminderValue != null) {
                            RouteRadio(
                                selected = currentReminderProvider == p.reminderValue,
                                onClick = { preferences.setReminderProvider(p.reminderValue) },
                            )
                        }
                    }
                    Box(modifier = Modifier.width(88.dp), contentAlignment = Alignment.Center) {
                        if (p.supportsNote && p.noteValue != null) {
                            RouteRadio(
                                selected = currentNoteProvider == p.noteValue,
                                onClick = { preferences.setNoteProvider(p.noteValue) },
                            )
                        }
                    }
                }
            }

            if (available.isNotEmpty()) {
                HorizontalDivider(thickness = 1.dp, color = palette.outlineVariant)
                if (!showAdd) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showAdd = true }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = palette.primary,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Add destination",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.primary,
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(palette.surfaceContainerLow)
                            .padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 12.dp),
                    ) {
                        // Header row: ADD DESTINATION ... Cancel
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 4.dp, end = 4.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "ADD DESTINATION",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp,
                                color = palette.onSurfaceVariant,
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                "Cancel",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = palette.onSurfaceVariant,
                                modifier = Modifier
                                    .clickable { showAdd = false }
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            available.forEach { p ->
                                AvailableProviderCard(
                                    provider = p,
                                    onAdd = p.onAdd,
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
private fun AvailableProviderCard(
    provider: AvailableProvider,
    onAdd: () -> Unit,
) {
    val palette = LocalPalette.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = palette.surfaceContainerLowest,
        border = BorderStroke(1.dp, palette.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = provider.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.onSurface,
                )
                if (provider.sub != null) {
                    Spacer(Modifier.height(1.dp))
                    Text(
                        text = provider.sub,
                        fontSize = 11.sp,
                        color = palette.onSurfaceVariant,
                    )
                }
            }
            // Kind badge
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = palette.primaryContainer,
            ) {
                Text(
                    text = provider.kindsLabel,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.6.sp,
                    color = palette.primary,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            // + button
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(palette.primary)
                    .clickable { onAdd() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Add ${provider.name}",
                    modifier = Modifier.size(16.dp),
                    tint = palette.onPrimary,
                )
            }
        }
    }
}

@Composable
private fun RouteRadio(
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(26.dp)
            .clip(CircleShape)
            .border(
                width = 2.dp,
                color = if (selected) LocalPalette.current.primary else LocalPalette.current.outline,
                shape = CircleShape,
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(LocalPalette.current.primary),
            )
        }
    }
}