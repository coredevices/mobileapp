package coredevices.ring.ui.screens.recording

import BugReportButton
import CoreNav
import androidx.compose.foundation.combinedClickable
import coredevices.ring.data.entity.room.indexfeed.kind
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
import androidx.compose.ui.text.style.TextOverflow
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import coredevices.indexai.data.entity.MessageRole
import coredevices.indexai.data.entity.RecordingEntryEntity
import coredevices.indexai.data.entity.RecordingEntryStatus
import coredevices.mcp.data.SemanticResult
import coredevices.ring.ui.components.chat.actionText
import coredevices.ring.ui.components.recording.RecordingTraceTimeline
import coredevices.ring.ui.theme.IndexTheme
import coredevices.ring.ui.theme.IndexThemeHost
import coredevices.ring.ui.viewmodel.MessagePlaybackState
import coredevices.ring.ui.viewmodel.RecordingDetailsViewModel
import coredevices.util.rememberUiContext
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.crashlytics.crashlytics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlin.time.Instant
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import coreapp.util.generated.resources.Res as UtilRes

@Composable
fun RecordingDetails(id: Long, coreNav: CoreNav) {
    Firebase.crashlytics.setCustomKey("recording_details_recording_id", id)
    val snackbarHostState = remember { SnackbarHostState() }
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
    val linkedItems by viewModel.linkedItems.collectAsState()
    val allLists by viewModel.allLists.collectAsState()
    val durationSec by viewModel.durationSeconds.collectAsState()

    IndexThemeHost {
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
                                onClick = { viewModel.dismissMoreMenu(); viewModel.deleteRecording { coreNav.goBack() } },
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
                        )
                    }
                }
            }
        }
    }
    Firebase.crashlytics.setCustomKey("recording_details_recording_id", 0)
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
) {
    val transcription = entries.firstOrNull()?.transcription.orEmpty()
    val firstEntry = entries.firstOrNull()
    // Latest attempt decides the error state (a recording can accrue retries).
    // Matches IndexFeedViewModel / FullFeedViewModel.
    val transcriptionFailed = entries
        .sortedWith(compareBy<RecordingEntryEntity> { it.timestamp }.thenBy { it.id })
        .lastOrNull()
        ?.status == RecordingEntryStatus.transcription_error

    // Tool calls that produced a saved object render as a navigable item chip
    // instead of a raw call; everything else falls back to the tool call.
    val itemsByToolCallId = remember(linkedItems) {
        linkedItems.asSequence()
            .filter { !it.sourceToolCallId.isNullOrBlank() }
            .associateBy { it.sourceToolCallId!! }
    }
    // Items that weren't tied to a tool call in this conversation (no
    // sourceToolCallId, or one that matches no call we rendered) are appended
    // as trailing chips so nothing extracted from the recording is dropped.
    val referencedToolCallIds = remember(messages) {
        messages.asSequence()
            .flatMap { it.document.tool_calls.orEmpty().asSequence() }
            .map { it.id }
            .toSet()
    }
    val trailingItems = remember(linkedItems, referencedToolCallIds) {
        linkedItems.filter { it.sourceToolCallId.isNullOrBlank() || it.sourceToolCallId !in referencedToolCallIds }
    }
    // Tool-result (`tool` role) message content, keyed by tool_call_id. A tool
    // call collapses with its result into a single chip showing the result.
    val toolResultsByCallId = remember(messages) {
        messages.asSequence()
            .map { it.document }
            .filter { it.role == MessageRole.tool && !it.tool_call_id.isNullOrBlank() }
            .associate { it.tool_call_id!! to (it.semantic_result ?: SemanticResult.GenericSuccess) }
    }

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
        // bubbles; assistant turns show a reply bubble plus action chips for
        // their tool calls. Tool-result messages aren't shown — the chip on
        // the assistant turn already represents the call and its outcome.
        if (messages.isNotEmpty()) {
            items(messages, key = { it.id }, contentType = { it.document.role }) { message ->
                ConversationMessage(
                    message = message,
                    itemsByToolCallId = itemsByToolCallId,
                    toolResultsByCallId = toolResultsByCallId,
                    allLists = allLists,
                    onOpenObject = onOpenObject,
                )
            }
        } else if (transcription.isNotBlank()) {
            // Fallback for recordings captured before the conversation was
            // persisted: show the raw transcription as the user bubble.
            item("bubble") {
                Spacer(Modifier.height(16.dp))
                TranscriptionBubble(transcription)
            }
        }

        // 2b. Transcription failed — surface the error inline with a retry,
        // so the user can recover without digging into the more menu.
        if (transcriptionFailed) {
            item("transcription-error") {
                Spacer(Modifier.height(8.dp))
                TranscriptionErrorRow(onRetry = onRetry)
            }
        }

        // 3. Trailing chips for extracted items not surfaced by a tool call.
        if (trailingItems.isNotEmpty()) {
            item("trailing-items") {
                Spacer(Modifier.height(8.dp))
                TrailingItemChips(
                    items = trailingItems,
                    allLists = allLists,
                    onOpenObject = onOpenObject,
                )
            }
        }

        // 4. Debug surfaces — only with debug toggle on. Keeps the prototype
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
private fun TranscriptionBubble(text: String) {
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
                // Long-press copies the transcription to the clipboard.
                // We can't surface a snackbar here without threading the
                // SnackbarHostState through, so the haptic doubles as
                // the visual ack — same UX as iOS Notes.
                .combinedClickable(
                    onClick = {},
                    onLongClick = {
                        clipboard.setText(androidx.compose.ui.text.AnnotatedString(text))
                        haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
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

/** Renders a single conversation message as a chat row. User turns are
 *  right-aligned bubbles; assistant turns get the ◎ avatar, reply bubble and
 *  action chips. Tool-result messages render nothing. */
@Composable
private fun ConversationMessage(
    message: ConversationMessageEntity,
    itemsByToolCallId: Map<String, coredevices.ring.data.entity.room.indexfeed.CachedItem>,
    toolResultsByCallId: Map<String, SemanticResult>,
    allLists: List<coredevices.ring.data.entity.room.indexfeed.CachedList>,
    onOpenObject: (String) -> Unit,
) {
    val doc = message.document
    when (doc.role) {
        MessageRole.user -> {
            val text = doc.content?.trim().orEmpty()
            if (text.isNotBlank()) {
                Spacer(Modifier.height(16.dp))
                TranscriptionBubble(text)
            }
        }
        MessageRole.assistant -> {
            val text = doc.content?.trim().orEmpty()
            val toolCalls = doc.tool_calls.orEmpty()
            if (text.isNotBlank() || toolCalls.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                AssistantTurn(
                    replyText = text,
                    toolCalls = toolCalls,
                    itemsByToolCallId = itemsByToolCallId,
                    toolResultsByCallId = toolResultsByCallId,
                    allLists = allLists,
                    onOpenObject = onOpenObject,
                )
            }
        }
        // Tool results are represented by the chip on the assistant turn.
        MessageRole.tool -> Unit
    }
}

@OptIn(ExperimentalLayoutApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun AssistantTurn(
    replyText: String,
    toolCalls: List<coredevices.indexai.data.entity.ToolCall>,
    itemsByToolCallId: Map<String, coredevices.ring.data.entity.room.indexfeed.CachedItem>,
    toolResultsByCallId: Map<String, SemanticResult>,
    allLists: List<coredevices.ring.data.entity.room.indexfeed.CachedList>,
    onOpenObject: (String) -> Unit,
) {
    // Don't render empty assistant turns
    when {
        replyText.isBlank() && toolCalls.isEmpty() -> return
        replyText.trim().replace("OK", "", ignoreCase = true).length <= 2 && toolCalls.isEmpty() -> return
    }
    val colors = IndexTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.Top,
    ) {
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
        // An "answer" tool call is the assistant's reply, so render its body as
        // a bubble rather than a chip; every other call stays a chip.
        val answerItems = toolCalls.mapNotNull { call ->
            itemsByToolCallId[call.id]?.takeIf { it.kind == "answer" && it.body.isNotBlank() }
        }
        val chipCalls = toolCalls.filter { itemsByToolCallId[it.id]?.kind != "answer" }
        Column(
            modifier = Modifier.foundationFillMaxWidth(0.85f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (replyText.isNotBlank()) ReplyBubble(replyText)
            answerItems.forEach { ReplyBubble(it.body) }
            if (chipCalls.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    chipCalls.forEach { call ->
                        val item = itemsByToolCallId[call.id]
                        val result = toolResultsByCallId[call.id]
                        val resultActionText by remember {
                            flow { emit(result?.actionText()) }.flowOn(Dispatchers.IO)
                        }.collectAsState("")
                        when {
                            // Prefer the saved object's chip (navigable, richly
                            // labelled) when the tool call produced one.
                            item != null -> ActionChip(
                                glyph = chipGlyph(item.kind),
                                label = coredevices.ring.ui.viewmodel.IndexFeedViewModel
                                    .chipLabel(item, allLists).take(64),
                                onClick = { onOpenObject(item.firestoreId) },
                            )
                            // Otherwise collapse the call + its result into one
                            // chip showing the result.
                            resultActionText != null -> ActionChip(
                                glyph = "•",
                                label = resultActionText!!,
                                onClick = null,
                            )
                            // No result yet — show the pending tool call.
                            else -> ActionChip(
                                glyph = "•",
                                label = toolCallLabel(call),
                                onClick = null,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Index reply bubble (left-aligned, rounded). Long-press copies the text. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ReplyBubble(text: String) {
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
                    clipboard.setText(androidx.compose.ui.text.AnnotatedString(text))
                    haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                },
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(
            text,
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

/** Trailing chips for extracted items that weren't tied to a rendered tool
 *  call. Mirrors the assistant turn's avatar + chip flow so they read as part
 *  of the same conversation. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TrailingItemChips(
    items: List<coredevices.ring.data.entity.room.indexfeed.CachedItem>,
    allLists: List<coredevices.ring.data.entity.room.indexfeed.CachedList>,
    onOpenObject: (String) -> Unit,
) {
    val colors = IndexTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.Top,
    ) {
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
        // Answers read as reply bubbles; everything else as chips.
        val answerItems = items.filter { it.kind == "answer" && it.body.isNotBlank() }
        val chipItems = items.filter { it.kind != "answer" }
        Column(
            modifier = Modifier.foundationFillMaxWidth(0.85f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            answerItems.forEach { ReplyBubble(it.body) }
            if (chipItems.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    chipItems.forEach { item ->
                        ActionChip(
                            glyph = chipGlyph(item.kind),
                            label = coredevices.ring.ui.viewmodel.IndexFeedViewModel
                                .chipLabel(item, allLists).take(64),
                            onClick = { onOpenObject(item.firestoreId) },
                        )
                    }
                }
            }
        }
    }
}

/** Pill-shaped action chip. [onClick] null = non-interactive (raw tool call). */
@Composable
private fun ActionChip(glyph: String, label: String, onClick: (() -> Unit)?) {
    val colors = IndexTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(colors.redSurface)
            .border(1.dp, colors.chipOutline, RoundedCornerShape(percent = 50))
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(glyph, color = colors.primary, fontSize = 12.sp)
        Spacer(Modifier.width(5.dp))
        Text(
            label,
            color = colors.onPrimaryContainer,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Humanise a tool call's function name for display, e.g. `search_contacts`
 *  -> "Search contacts". Falls back to the call type or "Action". */
private fun toolCallLabel(call: coredevices.indexai.data.entity.ToolCall): String {
    val name = call.function?.name?.takeIf { it.isNotBlank() } ?: call.type
    return name.replace('_', ' ').trim()
        .replaceFirstChar { it.uppercase() }
        .ifBlank { "Action" }
        .take(64)
}

private fun chipGlyph(kind: String): String = when (kind) {
    "reminder" -> "⏰"
    "scheduled" -> "⏰"
    "note" -> "≡"
    "answer" -> "✨"
    "message" -> "✉"
    "action_log" -> "✉"
    else -> "•"
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
