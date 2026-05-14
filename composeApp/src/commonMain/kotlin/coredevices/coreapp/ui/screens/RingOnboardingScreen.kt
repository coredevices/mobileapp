package coredevices.coreapp.ui.screens

import CommonRoutes
import CoreNav
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MobileOff
import androidx.compose.material.icons.filled.Shower
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.SpeakerNotesOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coredevices.indexai.data.entity.RecordingEntryStatus
import coredevices.libindex.LibIndex
import coredevices.libindex.database.dao.RingTransferDao
import coredevices.libindex.database.entity.RingTransferStatus
import coredevices.libindex.ui.components.Press
import coredevices.libindex.ui.components.PressPatternDot
import coredevices.pebble.ui.SettingsIds
import coredevices.pebble.ui.SnackbarDisplay
import coredevices.pebble.ui.rememberSettingsItemsState
import coredevices.ring.data.NoteShortcutType
import coredevices.ring.database.MusicControlMode
import coredevices.ring.database.Preferences
import coredevices.ring.database.SecondaryMode
import coredevices.ring.service.RingEvent
import coredevices.ring.service.RingSync
import coredevices.ring.ui.components.chat.ChatBubble
import coredevices.ring.ui.components.chat.ResponseBubble
import coredevices.ring.ui.components.chat.SemanticResultActionTaken
import coredevices.ring.ui.components.chat.SemanticResultIcon
import coredevices.ring.ui.components.feed.AnimatedAudioBars
import coredevices.ring.ui.components.feed.AudioBars
import coredevices.ring.ui.navigation.RingRoutes
import coredevices.ring.ui.screens.settings.AuthorizedIntegrations
import coredevices.ring.ui.screens.settings.IndexDeviceListItem
import coredevices.ring.ui.screens.settings.NoteShortcutDialog
import coredevices.ring.ui.viewmodel.SettingsViewModel
import coredevices.ui.PebbleElevatedButton
import coredevices.util.Platform
import coredevices.util.isAndroid
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import theme.onboardingScheme
import kotlin.time.Clock

@Composable
fun RingOnboardingScreen(
    coreNav: CoreNav,
) {
    val libIndex: LibIndex = koinInject()
    val preferences: Preferences = koinInject()
    val platform: Platform = koinInject()
    val viewModel = koinViewModel<SettingsViewModel>()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val snackbarDisplay =
        remember { SnackbarDisplay { scope.launch { snackbarHostState.showSnackbar(message = it) } } }
    val settings = rememberSettingsItemsState(
        navBarNav = null,
        snackbarDisplay = snackbarDisplay,
    )

    var step by remember { mutableStateOf(0) }

    val rings by libIndex.rings.collectAsState()
    val ringPairedState = viewModel.ringPaired.collectAsStateWithLifecycle()
    val ringPaired by derivedStateOf { ringPairedState.value != null }
    val currentRingName by viewModel.currentRingName.collectAsStateWithLifecycle()
    val musicControlMode by viewModel.musicControlMode.collectAsState()
    val secondaryMode by viewModel.secondaryMode.collectAsState()
    val noteShortcut by viewModel.noteShortcut.collectAsState()

    val showNoteShortcutDialog by viewModel.showNoteShortcutDialog.collectAsState()
    val availableNoteProviders by viewModel.availableNoteProviders.collectAsState()
    val availableReminderProviders by viewModel.availableReminderProviders.collectAsState()
    val isAndroid = remember { platform.isAndroid }

    if (showNoteShortcutDialog) {
        NoteShortcutDialog(
            availableNoteProviders = availableNoteProviders,
            availableReminderProviders = availableReminderProviders,
            currentShortcut = noteShortcut,
            onShortcutSelected = {
                viewModel.setNoteShortcut(it)
                viewModel.closeNoteShortcutDialog()
            },
            onDismissRequest = { viewModel.closeNoteShortcutDialog() },
        )
    }

    val scrollState = rememberScrollState()
    LaunchedEffect(step) { scrollState.scrollTo(0) }

    MaterialTheme(colorScheme = onboardingScheme) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            floatingActionButton = {
                if (step == 0 && scrollState.canScrollForward) {
                    FloatingActionButton(
                        onClick = { scope.launch { scrollState.animateScrollTo(scrollState.maxValue) } },
                    ) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Scroll down")
                    }
                }
            },
        ) { windowInsets ->
            Box(modifier = Modifier.padding(windowInsets).fillMaxSize()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(20.dp)
                        .verticalScroll(scrollState),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Crossfade(targetState = step, label = "onboarding_step") { currentStep ->
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                    when (currentStep) {
                    0 -> RingFaqStep(onContinue = { step = 1 })
                    else -> {

                    Text(
                        text = "Get Started!",
                        fontSize = 32.sp,
                        textAlign = TextAlign.Center,
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    IndexDeviceListItem(
                        headline = when {
                            ringPaired && currentRingName != null -> currentRingName!!
                            ringPaired -> "Paired to Index 01"
                            else -> "No Ring Paired"
                        },
                        buttons = {},
                        modifier = Modifier.padding(vertical = 8.dp),
                    )

                    settings.Show(SettingsIds.OfflineSpeechRecognition)

                    Spacer(modifier = Modifier.height(15.dp))

                    // --- Notification integration section ---
                    SectionText("Default Notes & Reminders")
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        "Choose where your notes and reminders are saved by default.",
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    AuthorizedIntegrations(preferences)

                    Spacer(modifier = Modifier.height(10.dp))

                    PebbleElevatedButton(
                        text = "Add Integration",
                        onClick = { coreNav.navigateTo(RingRoutes.AddIntegration) },
                        primaryColor = true,
                        icon = Icons.Default.Add,
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    ListItem(
                        modifier = Modifier.clickable { viewModel.showNoteShortcutDialog() },
                        headlineContent = { Text("Notification Shortcut") },
                        supportingContent = {
                            Text(
                                when (val shortcut = noteShortcut) {
                                    is NoteShortcutType.SendToMe -> "Email me"
                                    is NoteShortcutType.SendToNoteProvider -> shortcut.provider.title
                                    is NoteShortcutType.SendToReminderProvider -> shortcut.provider.title
                                }
                            )
                        },
                    )

                    SectionDivider()

                    // --- Button Actions section ---
                    SectionText("Music Play/Pause")
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        "Single or double click without holding to play/pause music on your phone.",
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    if (!isAndroid) {
                        Text(
                            "Music controls are Android only.",
                            textAlign = TextAlign.Center,
                        )
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            PressPatternTile(
                                label = "Disabled",
                                pattern = emptyList(),
                                selected = musicControlMode == MusicControlMode.Disabled,
                                onClick = { viewModel.setMusicControlMode(MusicControlMode.Disabled) },
                            )
                            PressPatternTile(
                                label = "Single click",
                                pattern = listOf(Press.Short),
                                selected = musicControlMode == MusicControlMode.SingleClick,
                                onClick = { viewModel.setMusicControlMode(MusicControlMode.SingleClick) },
                            )
                            PressPatternTile(
                                label = "Double click",
                                pattern = listOf(Press.Short, Press.Short),
                                selected = musicControlMode == MusicControlMode.DoubleClick,
                                onClick = { viewModel.setMusicControlMode(MusicControlMode.DoubleClick) },
                            )
                        }
                    }

                    SectionDivider()

                    SectionText("Secondary action")
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        "You can click before holding to perform a secondary action with your voice.",
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        PressPatternTile(
                            label = "Disabled",
                            pattern = emptyList(),
                            selected = secondaryMode == SecondaryMode.Disabled,
                            onClick = { viewModel.setSecondaryMode(SecondaryMode.Disabled) },
                        )
                        PressPatternTile(
                            label = "Search",
                            pattern = listOf(Press.Short, Press.HoldAndSpeak),
                            selected = secondaryMode == SecondaryMode.Search,
                            onClick = { viewModel.setSecondaryMode(SecondaryMode.Search) },
                        )
                    }

                    SectionDivider()

                    RingDemo(
                        nav = coreNav,
                    )

                    SectionDivider()

                    PebbleElevatedButton(
                        text = "Finished",
                        onClick = { coreNav.goBack() },
                        primaryColor = true,
                    )

                    } // else
                    } // when
                    } // Column (crossfade)
                    } // Crossfade
                }
            }
        }
    }
}

@Composable
private fun RingDemo(
    modifier: Modifier = Modifier,
    nav: CoreNav
) {
    val ringSync = koinInject<RingSync>()
    val ringTransferDao = koinInject<RingTransferDao>()
    val latestRingEvent by ringSync.ringEvents.collectAsStateWithLifecycle(null)
    val latestTransfer by ringTransferDao.getLatestTransferFeedItemFlow().collectAsStateWithLifecycle(null)

    val sessionStart = remember { Clock.System.now() }
    val transfer = latestTransfer?.ringTransfer?.takeIf { it.createdAt >= sessionStart }
    val feedItem = latestTransfer?.feedItem?.takeIf { transfer != null }

    @Composable
    fun NotWorkingText() {
        Row(
            modifier = Modifier.clickable {
                nav.navigateTo(CommonRoutes.BugReport(
                    pebble = false
                ))
            },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Not working? Report a bug", fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f))
            Spacer(Modifier.width(4.dp))
            Icon(
                Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = "Click to open bug report",
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
            )
        }
    }

    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when {
                transfer == null -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Default.Mic,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Try it out! Hold the button and speak into the ring",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    NotWorkingText()
                }

                transfer.status == RingTransferStatus.Started -> {
                    Text(
                        "Receiving recording...",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    val progress = (latestRingEvent as? RingEvent.Transfer.InProgress)
                        ?.takeIf { it.transferId == transfer.id }
                        ?.progress
                    if (progress != null) {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    NotWorkingText()
                }

                transfer.status == RingTransferStatus.Discarded -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Recording too short! Try holding the button a bit longer.",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f)
                        )
                    }

                }

                transfer.status == RingTransferStatus.Failed -> {
                    Icon(
                        Icons.Default.Error,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Transfer failed",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    NotWorkingText()
                }

                else -> {
                    // Completed — show feed-style chat bubbles
                    val entryStatus = feedItem?.entry?.status
                    Column(modifier = Modifier.fillMaxWidth()) {
                        ChatBubble(
                            modifier = Modifier.align(Alignment.End).padding(start = 50.dp)
                        ) {
                            when {
                                feedItem == null || entryStatus == RecordingEntryStatus.pending ->
                                    AnimatedAudioBars()
                                entryStatus == RecordingEntryStatus.transcription_error ->
                                    AudioBars(randomSeed = feedItem.id.hashCode())
                                else ->
                                    Text(feedItem.entry?.transcription ?: "...")
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        ResponseBubble(
                            modifier = Modifier.align(Alignment.Start),
                            leading = {
                                val result = feedItem?.semanticResult
                                when {
                                    result != null ->
                                        SemanticResultIcon(result, modifier = Modifier.size(12.dp))
                                    entryStatus == RecordingEntryStatus.transcription_error ->
                                        Icon(Icons.Outlined.SpeakerNotesOff, null, Modifier.size(12.dp))
                                    else ->
                                        Icon(Icons.Outlined.HourglassEmpty, null, Modifier.size(12.dp))
                                }
                            }
                        ) {
                            when {
                                feedItem?.semanticResult != null ->
                                    SemanticResultActionTaken(feedItem.semanticResult!!)
                                entryStatus == RecordingEntryStatus.transcription_error ->
                                    Text("No action taken")
                                else ->
                                    Text("Thinking...")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RingFaqStep(onContinue: () -> Unit) {
    Text(
        text = "Before you begin",
        fontSize = 28.sp,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(8.dp))
    Text(
        text = "A few quick things to know about your Index 01.",
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.bodyMedium,
    )
    Spacer(Modifier.height(24.dp))

    FaqCard(
        icon = Icons.Default.BugReport,
        question = "What if I find a bug?",
        answer = "Please report it! Functionality might break as we continue to develop features, but if you report bugs (Get Help -> Report a bug in Settings) we'll fix it as soon as we can.",
    )
    Spacer(Modifier.height(10.dp))
    FaqCard(
        icon = Icons.Default.Shower,
        question = "Can I wear it in the shower?",
        answer = "We don't recommend it. Index 01 is splash resistant but not waterproof especially in hot or soapy water."
    )
    Spacer(Modifier.height(10.dp))
    FaqCard(
        icon = Icons.Default.Mic,
        question = "How do I use it?",
        answer = "Hold the button, speak your note or request, then release and forget - or see the notification on your watch.",
    )
    Spacer(Modifier.height(10.dp))
    FaqCard(
        icon = Icons.Outlined.Lightbulb,
        question = "What can I ask?",
        answer = "Notes, reminders, timers, alarms, quick questions — the AI on your phone figures out what to do and replies in the notification.",
    )
    Spacer(Modifier.height(10.dp))
    FaqCard(
        icon = Icons.Default.Bluetooth,
        question = "Is it always listening?",
        answer = "Nope! Index 01 only listens while you're holding the button. When you release, it'll be processed by your phone if in range.",
    )
    Spacer(Modifier.height(10.dp))
    FaqCard(
        icon = Icons.Default.MobileOff,
        question = "What if I want to ditch my phone?",
        answer = buildAnnotatedString {
            append("Index 01 can hold up to 5 minutes of recordings and sleeps after 30 minutes when the phone isn't around. It'll sync to your phone when you're back in range, ")
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                append("just press the button to wake it!")
            }
        },
    )
    Spacer(Modifier.height(10.dp))
    FaqCard(
        icon = Icons.Default.BatteryChargingFull,
        question = "How do I charge it?",
        answer = "You don't! Index 01 will last around two years with daily use.",
    )
    Spacer(Modifier.height(10.dp))
    FaqCard(
        icon = Icons.Default.WifiOff,
        question = "Can it be used while offline / in bad cell service?",
        answer = "You can opt to use local speech recognition and a local AI model, so it can work without a connection. We recommend setting it up as cloud with a local fallback for the best experience",
    )
    Spacer(Modifier.height(24.dp))
    PebbleElevatedButton(
        text = "Continue",
        onClick = onContinue,
        primaryColor = true,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FaqCard(icon: ImageVector, question: String, answer: AnnotatedString) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp).padding(top = 2.dp),
                tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(question, style = MaterialTheme.typography.titleSmallEmphasized)
                Spacer(Modifier.height(4.dp))
                Text(answer, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun FaqCard(icon: ImageVector, question: String, answer: String) {
    FaqCard(icon, question, buildAnnotatedString { append(answer) })
}

@Composable
private fun RowScope.PressPatternTile(
    label: String,
    pattern: List<Press>,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val contentColor = if (selected) MaterialTheme.colorScheme.onPrimary
                       else MaterialTheme.colorScheme.onSurface
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primary
                             else MaterialTheme.colorScheme.surface,
            contentColor = contentColor,
        ),
        border = if (!selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier.weight(1f),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(label, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(10.dp))
            Box(
                modifier = Modifier.height(28.dp).fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                if (pattern.isNotEmpty()) {
                    PressPatternDot(
                        pattern = pattern,
                        activeColor = contentColor,
                        idleColor = contentColor.copy(alpha = 0.25f),
                    )
                }
            }
        }
    }
}