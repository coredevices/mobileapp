package coredevices.ring.ui.screens.recording

import BugReportButton
import CoreNav
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth as foundationFillMaxWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.format.DayOfWeekNames
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.format.Padding
import kotlinx.datetime.format.char
import kotlinx.datetime.toLocalDateTime
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import co.touchlab.kermit.Logger
import coreapp.ring.generated.resources.Res
import coreapp.ring.generated.resources.export_recording
import coreapp.ring.generated.resources.more_options
import coreapp.util.generated.resources.back
import coredevices.indexai.data.entity.ConversationMessageEntity
import coredevices.indexai.data.entity.LocalRecording
import coredevices.indexai.data.entity.RecordingEntryEntity
import coredevices.indexai.data.entity.RecordingEntryStatus
import coredevices.indexai.database.dao.RecordingToolMessage
import coredevices.ring.ui.components.ShimmerText
import coredevices.ring.ui.components.feed.RecordingActionChip
import coredevices.ring.ui.components.recording.RecordingTraceTimeline
import coredevices.ring.ui.components.recording.ToolCallGroup
import coredevices.ring.ui.components.recording.ToolConfirmationCard
import coredevices.ring.ui.theme.IndexTheme
import coredevices.ring.ui.viewmodel.MessagePlaybackState
import coredevices.ring.ui.viewmodel.AssistantPart
import coredevices.ring.ui.viewmodel.ConversationTurn
import coredevices.ring.ui.viewmodel.RecordingAction
import coredevices.ring.ui.viewmodel.RecordingDetailsViewModel
import coredevices.ring.ui.viewmodel.conversationTurns
import coredevices.ring.ui.viewmodel.recordingActions
import coredevices.util.rememberUiContext
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.crashlytics.crashlytics
import kotlinx.coroutines.launch
import kotlin.time.Instant
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import coreapp.util.generated.resources.Res as UtilRes

@Composable
fun RecordingDetails(id: Long, coreNav: CoreNav) {
    Firebase.crashlytics.setCustomKey("recording_details_recording_id", id)
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val uiContext = rememberUiContext()
    if (uiContext == null) {
        Logger.e("RecordingDetails") { "uiContext is null" }
        return
    }
    val viewModel = koinViewModel<RecordingDetailsViewModel> { parametersOf(id, snackbarHostState, uiContext) }
    val itemState by viewModel.itemState.collectAsState()
    val moreMenuExpanded by viewModel.moreMenuExpanded.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()
    val showDebugDetails by viewModel.showDebugDetails.collectAsState()
    val showTraceTimeline by viewModel.showTraceTimeline.collectAsState()
    val showDeleteDialog by viewModel.showDeleteDialog.collectAsState()
    val linkedItems by viewModel.linkedItems.collectAsState()
    val allLists by viewModel.allLists.collectAsState()
    val durationSec by viewModel.durationSeconds.collectAsState()

    val indexColors = IndexTheme.colors
    val statusBarPad = WindowInsets.statusBars.asPaddingValues()
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = indexColors.surface,
        modifier = Modifier.padding(top = statusBarPad.calculateTopPadding()),
        topBar = {
            androidx.compose.foundation.layout.Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(indexColors.surface)
                    .padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = coreNav::goBack) {
                    Icon(
                        Icons.AutoMirrored.Default.ArrowBack,
                        contentDescription = stringResource(UtilRes.string.back),
                        tint = indexColors.onSurface,
                    )
                }
                Text(
                    // Prototype shows the recording's date/time as the
                    // title, not the AI-generated assistantTitle.
                    (itemState as? RecordingDetailsViewModel.ItemState.Loaded)?.recording?.localTimestamp
                        ?.let { formatRecordingTitle(it) }
                        ?: "Index Recording",
                    color = indexColors.onSurface,
                    fontSize = 16.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                )
                BugReportButton(
                    coreNav,
                    pebble = false,
                    screenContext = mapOf(
                        "screen" to "RecordingDetails",
                        "transcriptionModel" to ((itemState as? RecordingDetailsViewModel.ItemState.Loaded)?.entries?.firstOrNull()?.transcribedUsingModel ?: "<unknown>"),
                        "state" to itemState.toString(),
                        "recordingId" to id.toString(),
                    ),
                    recordingPath = (viewModel.itemState.value as? RecordingDetailsViewModel.ItemState.Loaded)
                        ?.entries?.firstOrNull()?.fileName,
                )
                // Box anchors the DropdownMenu to the icon's bounds so
                // the menu opens directly below the dots — without
                // wrapping, the menu anchors to the right-slot start
                // and renders on the left side of the screen
                // (May 8 fix, mirrors ObjectItemDetail / ObjectListDetail).
                Box {
                    IconButton(onClick = viewModel::toggleMoreMenu) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = stringResource(Res.string.more_options),
                            tint = indexColors.onSurfaceVariant,
                        )
                    }
                    DropdownMenu(expanded = moreMenuExpanded, onDismissRequest = viewModel::dismissMoreMenu) {
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.export_recording)) },
                            onClick = { viewModel.exportRecording(); viewModel.dismissMoreMenu() },
                        )
                        // Re-run the agent ingestion against this recording.
                        // Always available — moving it out of the debug-only
                        // block so the user can recover from a failed
                        // ingestion or pick up new agent behaviour without
                        // toggling debug mode.
                        if (showDebugDetails) {
                            DropdownMenuItem(
                                text = { Text(if (showTraceTimeline) "Hide Trace Timeline" else "Show Trace Timeline") },
                                onClick = { viewModel.toggleTraceTimeline(); viewModel.dismissMoreMenu() },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Delete recording", color = indexColors.error) },
                            onClick = { viewModel.dismissMoreMenu(); viewModel.requestDelete() },
                        )
                    }
                }
            }
        },
    ) { insets ->
        Box(
            modifier = Modifier.padding(insets).fillMaxSize().background(indexColors.surface),
        ) {
            when (val state = itemState) {
                is RecordingDetailsViewModel.ItemState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is RecordingDetailsViewModel.ItemState.Error -> {
                    Text("Error loading recording", color = indexColors.onSurfaceVariant, modifier = Modifier.align(Alignment.Center))
                }
                is RecordingDetailsViewModel.ItemState.Loaded -> {
                    RecordingDetailsContents(
                        recording = state.recording,
                        messages = state.messages,
                        entries = state.entries,
                        linkedItems = linkedItems,
                        allLists = allLists,
                        durationSec = durationSec,
                        playbackState = playbackState,
                        togglePlayback = viewModel::togglePlayback,
                        showDebugDetails = showDebugDetails,
                        showTraceTimeline = showTraceTimeline,
                        onRetry = viewModel::retryRecording,
                        onOpenObject = { id ->
                            coreNav.navigateTo(coredevices.ring.ui.navigation.RingRoutes.ObjectDetails(id))
                        },
                        onCopied = { scope.launch { snackbarHostState.showSnackbar("Copied to clipboard") } },
                        onRespondToConfirmation = viewModel::respondToConfirmation,
                    )
                }
            }
        }
    }
    if (showDeleteDialog) {
        DeleteRecordingDialog(
            linkedItemCount = linkedItems.size,
            onDismiss = viewModel::dismissDeleteDialog,
            onDeleteRecordingOnly = {
                viewModel.deleteRecording(alsoDeleteItems = false) { coreNav.goBack() }
            },
            onDeleteRecordingAndItems = {
                viewModel.deleteRecording(alsoDeleteItems = true) { coreNav.goBack() }
            },
        )
    }
    Firebase.crashlytics.setCustomKey("recording_details_recording_id", 0)
}

@Composable
private fun DeleteRecordingDialog(
    linkedItemCount: Int,
    onDismiss: () -> Unit,
    onDeleteRecordingOnly: () -> Unit,
    onDeleteRecordingAndItems: () -> Unit,
) {
    val colors = IndexTheme.colors
    if (linkedItemCount == 0) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Delete recording?") },
            text = { Text("This will permanently delete the recording.") },
            confirmButton = {
                TextButton(onClick = onDeleteRecordingOnly) {
                    Text("Delete", color = colors.error)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("Cancel") }
            },
        )
    } else {
        val itemWord = if (linkedItemCount == 1) "item" else "items"
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Delete recording?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "This recording created $linkedItemCount $itemWord in your feed.",
                    )
                    // Use full-width selectable rows for the two delete
                    // choices so they read cleanly and don't fight the
                    // AlertDialog button slots (which only fit Cancel +
                    // one primary).
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        DeleteChoiceRow(
                            label = "Delete recording only",
                            sublabel = "Keep the $itemWord in your feed",
                            color = colors.error,
                            onClick = onDeleteRecordingOnly,
                        )
                        DeleteChoiceRow(
                            label = "Delete recording and $itemWord",
                            sublabel = "Remove everything",
                            color = colors.error,
                            onClick = onDeleteRecordingAndItems,
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun DeleteChoiceRow(
    label: String,
    sublabel: String,
    color: Color,
    onClick: () -> Unit,
) {
    val colors = IndexTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(label, color = color, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        Text(sublabel, color = colors.onSurfaceVariant, fontSize = 12.sp)
    }
}

@Composable
private fun RecordingDetailsContents(
    recording: LocalRecording,
    messages: List<ConversationMessageEntity>,
    entries: List<RecordingEntryEntity>,
    linkedItems: List<coredevices.ring.data.entity.room.indexfeed.CachedItem>,
    allLists: List<coredevices.ring.data.entity.room.indexfeed.CachedList>,
    durationSec: Float?,
    playbackState: MessagePlaybackState,
    togglePlayback: (RecordingEntryEntity) -> Unit,
    showDebugDetails: Boolean,
    showTraceTimeline: Boolean,
    onRetry: () -> Unit,
    onOpenObject: (String) -> Unit,
    onCopied: () -> Unit,
    onRespondToConfirmation: (callId: String, approved: Boolean) -> Unit,
) {
    val transcription = entries.firstOrNull()?.transcription.orEmpty()
    val firstEntry = entries.firstOrNull()
    // Latest attempt decides the error state (a recording can accrue retries).
    // Matches IndexFeedViewModel / FullFeedViewModel.
    val latestStatus = entries
        .sortedWith(compareBy<RecordingEntryEntity> { it.timestamp }.thenBy { it.id })
        .lastOrNull()
        ?.status
    val transcriptionFailed = latestStatus == RecordingEntryStatus.transcription_error
    val agentProcessing = latestStatus == RecordingEntryStatus.agent_processing

    // Each tool result, keyed by the call that produced it. Answers have no call; the
    // tagged assistant message's content is already rendered as a reply.
    val actionsByCall = remember(messages, linkedItems, allLists) {
        recordingActions(messages.map { RecordingToolMessage.from(it) }, allLists, linkedItems)[recording.id]
            .orEmpty()
            .filter { it.toolCallId != null }
            .associateBy { it.toolCallId }
    }
    val turns = remember(messages, actionsByCall, entries) { conversationTurns(messages, actionsByCall, entries) }
    // An in-flight tool-call group already shimmers; otherwise show the agent is still working.
    val thinking = agentProcessing &&
        (turns.lastOrNull() as? ConversationTurn.Assistant)?.parts?.lastOrNull() !is AssistantPart.ToolCalls

    LazyColumn(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        // 1. Audio player widget
        item("audio") {
            firstEntry?.let { entry ->
                AudioPlayerCard(
                    recordingId = recording.firestoreId ?: recording.id.toString(),
                    durationSec = durationSec,
                    playbackState = playbackState,
                    onTogglePlay = { togglePlayback(entry) },
                )
            }
        }

        // 2. Full conversation, in order. User turns are right-aligned red
        // bubbles; assistant turns show the intermediate tool calls, reply
        // bubbles and a chip per user-facing result.
        if (messages.isNotEmpty()) {
            val lastTurn = turns.lastIndex
            items(turns.size, key = { "turn-$it" }, contentType = { turns[it]::class }) { i ->
                when (val turn = turns[i]) {
                    is ConversationTurn.User -> {
                        Spacer(Modifier.height(16.dp))
                        TranscriptionBubble(turn.text, onCopied)
                        if (turn.transcribedOffline) UserOfflineLabel()
                    }
                    is ConversationTurn.Assistant -> AssistantTurn(
                        turn = turn,
                        active = agentProcessing && i == lastTurn,
                        onOpenObject = onOpenObject,
                        onCopied = onCopied,
                        onRespondToConfirmation = onRespondToConfirmation,
                    )
                }
            }
        } else if (transcription.isNotBlank()) {
            // Fallback for recordings captured before the conversation was
            // persisted: show the raw transcription as the user bubble.
            item("bubble") {
                Spacer(Modifier.height(16.dp))
                TranscriptionBubble(transcription, onCopied)
                if (firstEntry?.transcribedViaLocalFallback == true) UserOfflineLabel()
            }
        }

        if (thinking) {
            item("thinking") { ThinkingRow() }
        }

        // 2b. Transcription failed — surface the error inline with a retry,
        // so the user can recover without digging into the more menu.
        if (transcriptionFailed) {
            item("transcription-error") {
                Spacer(Modifier.height(8.dp))
                TranscriptionErrorRow(onRetry = onRetry)
            }
        }

        // 3. Debug surfaces — only with debug toggle on. Keeps the prototype
        // body clean for normal users.
        if (showDebugDetails) {
            items(entries.size, contentType = { "debug_details" } ) { i ->
                val timestamp = entries[i].timestamp
                entries[i].ringTransferInfo?.let { entry ->
                    OutlinedCard(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp).fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                "Ring Recording $i (Index ${entry.collectionStartIndex}, end: ${entry.collectionEndIndex})",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text("Release->RX Latency: ${entry.buttonReleaseAdvertisementLatencyMs} ms")
                            val rxFeed = entry.advertisementReceived?.let { ar ->
                                timestamp - Instant.fromEpochMilliseconds(ar)
                            }
                            Text("RX->Feed Latency: ${rxFeed?.inWholeMilliseconds ?: "—"} ms")
                        }
                    }
                }
            }
        }
        if (showTraceTimeline) {
            item {
                RecordingTraceTimeline(recording.id)
            }
        }
        item("tail") { Spacer(Modifier.height(80.dp)) }
    }
}

// ── Prototype-shape body components ─────────────────────────────────────

@Composable
private fun AudioPlayerCard(
    recordingId: String,
    durationSec: Float?,
    playbackState: MessagePlaybackState,
    onTogglePlay: () -> Unit,
) {
    val colors = IndexTheme.colors
    val playing = playbackState !is MessagePlaybackState.Stopped
    val progress = when (playbackState) {
        is MessagePlaybackState.Playing -> playbackState.percentageComplete.toFloat().coerceIn(0f, 1f)
        else -> 0f
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surfaceContainerLow)
            .border(1.dp, colors.outlineVariant, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(colors.primary)
                .clickable(onClick = onTogglePlay),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (playing) "Pause" else "Play",
                tint = colors.onPrimary,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        WaveformBars(
            seed = recordingId,
            progress = progress,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            durationSec?.let { formatSeconds(it) } ?: "—",
            color = colors.onSurfaceVariant,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
        )
    }
}

/** Pseudo-random bar heights seeded by the recording id, like the prototype. */
@Composable
private fun WaveformBars(
    seed: String,
    progress: Float,
    modifier: Modifier = Modifier,
) {
    val colors = IndexTheme.colors
    val barCount = 22
    Row(
        modifier = modifier.height(20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        for (i in 0 until barCount) {
            val ch = if (seed.isEmpty()) (i + 7) else seed[i % seed.length].code
            val raw = ((ch + i * 13) % 13).coerceAtLeast(0) + 4
            val barH = raw.dp
            val active = i.toFloat() / barCount < progress
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(barH)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (active) colors.primary else colors.outlineVariant),
            )
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun TranscriptionBubble(text: String, onCopied: () -> Unit) {
    val colors = IndexTheme.colors
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.End,
    ) {
        Box(
            modifier = Modifier
                .foundationFillMaxWidth(0.85f)
                .clip(RoundedCornerShape(20.dp, 20.dp, 5.dp, 20.dp))
                .background(colors.primary)
                .combinedClickable(
                    onClick = {},
                    onLongClick = {
                        clipboard.setText(androidx.compose.ui.text.AnnotatedString(text))
                        haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        onCopied()
                    },
                )
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(
                text,
                color = colors.onPrimary,
                fontSize = 15.sp,
                lineHeight = 20.sp,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AssistantTurn(
    turn: ConversationTurn.Assistant,
    /** The agent is still working on this turn, so its tool calls may grow. */
    active: Boolean,
    onOpenObject: (String) -> Unit,
    onCopied: () -> Unit,
    onRespondToConfirmation: (callId: String, approved: Boolean) -> Unit,
) {
    val replies = turn.replies
    val hasCalls = turn.hasCalls
    // Don't render empty assistant turns
    when {
        replies.isEmpty() && !hasCalls -> return
        !hasCalls && replies.all { it.replace("OK", "", ignoreCase = true).length <= 2 } -> return
    }
    Spacer(Modifier.height(8.dp))
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        AssistantAvatar()
        Column(
            modifier = Modifier.foundationFillMaxWidth(0.85f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            turn.parts.forEachIndexed { i, part ->
                when (part) {
                    is AssistantPart.Reply -> ReplyBubble(part.text, onCopied)
                    is AssistantPart.Actions -> FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        part.actions.forEach { RecordingActionChip(action = it, onOpenObject = onOpenObject) }
                    }
                    // Only the newest group can still grow; anything after it has already superseded it.
                    is AssistantPart.ToolCalls -> ToolCallGroup(
                        steps = part.steps,
                        active = active && i == turn.parts.lastIndex,
                    )
                    is AssistantPart.Confirmation -> ToolConfirmationCard(
                        request = part.request,
                        onRespond = { approved -> onRespondToConfirmation(part.callId, approved) },
                    )
                }
            }
            if (turn.processedOffline) OfflineLabel("Processed offline")
        }
    }
}

@Composable
private fun UserOfflineLabel() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.End,
    ) {
        OfflineLabel("Transcribed offline")
    }
}

@Composable
private fun OfflineLabel(text: String) {
    val colors = IndexTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Outlined.CloudOff,
            contentDescription = null,
            tint = colors.onSurfaceVariant,
            modifier = Modifier.size(12.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(text, color = colors.onSurfaceVariant, fontSize = 11.sp, lineHeight = 13.sp)
    }
}

@Composable
private fun AssistantAvatar() {
    val colors = IndexTheme.colors
    Box(
        modifier = Modifier
            .padding(top = 2.dp)
            .size(28.dp)
            .clip(CircleShape)
            .background(colors.redSurface),
        contentAlignment = Alignment.Center,
    ) {
        RingGlyphCanvas(sizeDp = 14, color = colors.primary)
    }
    Spacer(Modifier.width(8.dp))
}

@Composable
private fun ThinkingRow() {
    val colors = IndexTheme.colors
    Spacer(Modifier.height(8.dp))
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AssistantAvatar()
        ShimmerText(
            "Thinking",
            baseColor = colors.onSurfaceVariant,
            highlightColor = colors.onSurface,
            fontWeight = FontWeight.Medium,
        )
    }
}

/** Index reply bubble (left-aligned, rounded). Long-press copies the text. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ReplyBubble(text: String, onCopied: () -> Unit) {
    val sanitized = text.replace(Regex("<[^>]*>"), "").trim()
    val colors = IndexTheme.colors
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp))
            .background(colors.surfaceContainerLow)
            .combinedClickable(
                onClick = {},
                onLongClick = {
                    clipboard.setText(androidx.compose.ui.text.AnnotatedString(sanitized))
                    haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    onCopied()
                },
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(
            sanitized,
            color = colors.onSurface,
            fontSize = 14.5.sp,
            lineHeight = 21.sp,
        )
    }
}

/** Assistant-side row shown when transcription failed: an error bubble with
 *  an inline Retry button that re-enqueues the recording. Mirrors the
 *  assistant turn's avatar so it reads as part of the conversation. */
@Composable
private fun TranscriptionErrorRow(onRetry: () -> Unit) {
    val colors = IndexTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        AssistantAvatar()
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp))
                .background(colors.errorContainer)
                .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Transcription error",
                color = colors.onErrorContainer,
                fontSize = 14.5.sp,
                lineHeight = 21.sp,
            )
            Spacer(Modifier.width(10.dp))
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(percent = 50))
                    .background(colors.primary)
                    .clickable(onClick = onRetry)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.Refresh,
                    contentDescription = "Retry transcription",
                    tint = colors.onPrimary,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    "Retry",
                    color = colors.onPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

/** Two concentric outlined circles + a small filled mic-pip dot. */
@Composable
private fun RingGlyphCanvas(sizeDp: Int, color: Color) {
    Canvas(modifier = Modifier.size(sizeDp.dp)) {
        val s = this.size.minDimension / 24f
        val cx = 12f * s
        val cy = 12.5f * s
        drawCircle(
            color = color,
            radius = 8f * s,
            center = Offset(cx, cy),
            style = Stroke(width = 1.8f * s),
        )
        drawCircle(
            color = color.copy(alpha = 0.55f),
            radius = 4.5f * s,
            center = Offset(cx, cy),
            style = Stroke(width = 1.4f * s),
        )
        drawCircle(
            color = color,
            radius = 1.6f * s,
            center = Offset(cx, 4.2f * s),
        )
    }
}

private val recordingTitleFormat = LocalDateTime.Format {
    dayOfWeek(DayOfWeekNames.ENGLISH_ABBREVIATED)
    chars(", ")
    monthName(MonthNames.ENGLISH_ABBREVIATED)
    char(' ')
    dayOfMonth(Padding.NONE)
    chars(", ")
    amPmHour(Padding.NONE)
    char(':')
    minute()
    char(' ')
    amPmMarker("AM", "PM")
}

private fun formatRecordingTitle(at: Instant): String =
    at.toLocalDateTime(TimeZone.currentSystemDefault()).format(recordingTitleFormat)

private fun formatSeconds(value: Float): String {
    val whole = value.toInt()
    val tenths = ((value - whole) * 10f).toInt().coerceIn(0, 9)
    return "$whole.${tenths}s"
}
