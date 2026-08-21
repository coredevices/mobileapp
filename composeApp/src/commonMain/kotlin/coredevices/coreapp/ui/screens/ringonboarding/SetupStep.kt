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
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import coredevices.ring.service.button.GestureDestination
import coredevices.ring.service.button.RingGesture
import coredevices.ring.service.button.musicRoutesFor
import coredevices.ring.ui.screens.settings.EncryptionKeyResultDialogs
import coredevices.ring.ui.screens.settings.EncryptionSetupDialog
import coredevices.ring.ui.screens.settings.GTasksDialog
import coredevices.ring.ui.screens.settings.NotionDialog
import coredevices.ring.ui.viewmodel.SettingsViewModel
import coredevices.ui.SignInDialog
import coredevices.util.CoreConfigHolder
import coredevices.util.Permission
import coredevices.util.PermissionRequester
import coredevices.util.STTConfig
import coredevices.util.emailOrNull
import coredevices.util.integrations.Integration
import coredevices.util.models.CactusSTTMode
import coredevices.util.models.ModelInfo
import coredevices.util.models.ModelManager
import coredevices.util.models.RecommendedModel
import coredevices.util.rememberUiContext
import coredevices.util.transcription.PlatformSpeechRecognizer
import coredevices.util.transcription.SpokenLanguageOptions
import coredevices.util.transcription.coversLanguage
import androidx.compose.ui.text.intl.Locale
import coredevices.ring.ui.screens.settings.IndexAgentActionsSection
import coredevices.ring.ui.screens.settings.RingButtonSection
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
    val gestureRoutes by viewModel.gestureRoutes.collectAsState()
    val musicPresetSelected = { mode: MusicControlMode ->
        musicRoutesFor(mode).all { (gesture, destination) -> gestureRoutes[gesture] == destination }
    }
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
    val platformSpeechRecognizer: PlatformSpeechRecognizer = koinInject()
    val platformSttAvailable by produceState(false) {
        value = withContext(Dispatchers.Default) { platformSpeechRecognizer.isAvailable() }
    }
    val deviceLanguage = Locale.current.language
    val platformLanguageTags by platformSpeechRecognizer.supportedLanguageTags.collectAsState()
    val platformSupportsDeviceLanguage = platformLanguageTags.coversLanguage(deviceLanguage)
    val permissionRequester: PermissionRequester = koinInject()
    val uiContext = rememberUiContext()
    // The permission is also nagged for while this mode is configured; asking here means the
    // engine is usable straight away rather than falling back to cloud until the nag is answered.
    val selectPlatformStt: () -> Unit = {
        coreConfigHolder.update(
            coreConfig.copy(sttConfig = coreConfig.sttConfig.copy(mode = CactusSTTMode.PlatformOnly))
        )
        uiContext?.let { context ->
            scope.launch {
                permissionRequester.requestPermission(Permission.SpeechRecognizer, context)
            }
        }
    }
    // Default to the free on-device system engine when this phone supports it (iOS 26+)
    // and can transcribe the phone's language; otherwise keep the cloud default. One-shot
    // (persisted) so re-entering onboarding never overrides a deliberate cloud choice.
    LaunchedEffect(platformSttAvailable, platformSupportsDeviceLanguage) {
        if (platformSttAvailable && platformSupportsDeviceLanguage &&
            !preferences.platformSttDefaulted &&
            coreConfig.sttConfig.mode == CactusSTTMode.RemoteOnly
        ) {
            preferences.setPlatformSttDefaulted()
            selectPlatformStt()
        }
    }
    var showPlatformInfoDialog by remember { mutableStateOf(false) }
    if (showPlatformInfoDialog) {
        val languages = remember(platformLanguageTags) {
            platformLanguageTags
                .map { it.substringBefore('-').lowercase() }.distinct()
                .mapNotNull { code -> SpokenLanguageOptions.firstOrNull { it.first == code }?.second }
                .sorted()
        }
        AlertDialog(
            onDismissRequest = { showPlatformInfoDialog = false },
            title = { Text("On-device speech recognition") },
            text = {
                Text(
                    "Uses Apple's built-in speech recognition — no download needed. " +
                        "Audio is processed on your phone, falling back to cloud " +
                        "transcription if the language isn't supported or on-device " +
                        "transcription fails." +
                        if (languages.isEmpty()) {
                            ""
                        } else {
                            "\n\nSupported languages:\n" + languages.joinToString(", ")
                        }
                )
            },
            confirmButton = {
                TextButton(onClick = { showPlatformInfoDialog = false }) { Text("OK") }
            },
        )
    }
    val selectSpeechMode: (CactusSTTMode) -> Unit = { mode ->
        // An explicit choice disables the one-shot auto-default, even if it hasn't fired yet.
        preferences.setPlatformSttDefaulted()
        when {
            mode == CactusSTTMode.PlatformOnly -> selectPlatformStt()
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
                    showPlatformOption = platformSttAvailable,
                    onPlatformInfo = { showPlatformInfoDialog = true },
                )
            }
        }

        // 2 — Ring button
        NumberedSection(
            num = 2,
            title = "Ring Button",
            sub = "Choose what each press does. You can change this later in Index settings.",
        ) {
            RingButtonSection(viewModel)
        }

        // 3 — Index agent actions
        NumberedSection(
            num = 3,
            title = "Index Agent",
            sub = "Pick which actions Index can take, and where notes and reminders are saved."
        ) {
            IndexAgentActionsSection(coreNav, viewModel, showHeader = false)
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
                    selected = gestureRoutes[RingGesture.ClickHold] == GestureDestination.IndexAgent,
                    onClick = {
                        viewModel.setGestureRoute(RingGesture.ClickHold, GestureDestination.IndexAgent)
                    },
                    modifier = Modifier.weight(1f),
                )
                PressTile(
                    label = "Search",
                    pattern = PressPattern.ShortHold,
                    selected = gestureRoutes[RingGesture.ClickHold] == GestureDestination.WebSearch,
                    onClick = {
                        viewModel.setGestureRoute(RingGesture.ClickHold, GestureDestination.WebSearch)
                    },
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
    showPlatformOption: Boolean = false,
    onPlatformInfo: (() -> Unit)? = null,
) {
    val options = listOfNotNull(
        Triple(CactusSTTMode.PlatformOnly, "On-device", "Recommended - private, stays on this iPhone")
            .takeIf { showPlatformOption },
        Triple(CactusSTTMode.RemoteOnly, "Cloud only", "Best performance, requires connection"),
        Triple(CactusSTTMode.RemoteFirst, "Cloud, with local fallback", "Recommended, 400MB download")
            .takeIf { !showPlatformOption },
        Triple(CactusSTTMode.LocalOnly, "Local only", "Complete privacy, 400MB download"),
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (m, title, sub) ->
            SpeechRadioCard(
                title = title,
                sub = sub,
                selected = mode == m,
                onClick = { onChange(m) },
                onInfo = onPlatformInfo.takeIf { m == CactusSTTMode.PlatformOnly },
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
    onInfo: (() -> Unit)? = null,
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
            if (onInfo != null) {
                IconButton(onClick = onInfo, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Outlined.Info,
                        contentDescription = "Supported languages",
                        tint = palette.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}