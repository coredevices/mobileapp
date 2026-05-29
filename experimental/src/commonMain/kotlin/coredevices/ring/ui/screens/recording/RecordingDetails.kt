package coredevices.ring.ui.screens.recording

import BugReportButton
import CoreNav
import androidx.compose.foundation.combinedClickable
import coredevices.ring.data.entity.room.indexfeed.kind
import coredevices.ring.data.entity.room.indexfeed.recipientName
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import coredevices.indexai.data.entity.RecordingDocument
import coredevices.indexai.data.entity.RecordingEntry
import coredevices.indexai.data.entity.RecordingEntryEntity
import coredevices.ring.ui.components.chat.ChatInput
import coredevices.ring.ui.components.recording.RecordingTraceTimeline
import coredevices.ring.ui.theme.IndexTheme
import coredevices.ring.ui.theme.IndexThemeHost
import coredevices.ring.ui.viewmodel.MessagePlaybackState
import coredevices.ring.ui.viewmodel.RecordingDetailsViewModel
import coredevices.util.rememberUiContext
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.crashlytics.crashlytics
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

        // 2. User transcription bubble (right-aligned, red)
        if (transcription.isNotBlank()) {
            item("bubble") {
                Spacer(Modifier.height(16.dp))
                TranscriptionBubble(transcription)
            }
        }

        // 3. Index reply + action chips. Mirrors the prototype's
        // RecordingViewChat: ◎ avatar on the left, then a reply bubble
        // (Q&A body or "To X: ..." for messages) and below it the
        // remaining chips. The bubble's source item is suppressed from
        // the chip list since the bubble already represents it.
        if (linkedItems.isNotEmpty()) {
            val answerItem = linkedItems.firstOrNull { it.kind == "answer" }
            val messageItem = if (answerItem == null)
                linkedItems.firstOrNull { it.kind == "message" && it.body.isNotBlank() }
                else null
            val replyText = answerItem?.body
                ?: messageItem?.let { msg ->
                    val recip = msg.recipientName()
                    if (!recip.isNullOrBlank()) "To $recip: ${msg.body}" else msg.body
                }
                ?: ""
            val suppressedId = answerItem?.firestoreId ?: messageItem?.firestoreId
            val chipsToShow = if (suppressedId != null)
                linkedItems.filter { it.firestoreId != suppressedId }
                else linkedItems
            item("reply-row") {
                Spacer(Modifier.height(8.dp))
                IndexReplyRow(
                    replyText = replyText,
                    bubbleTargetId = if (replyText.isNotBlank()) suppressedId else null,
                    chips = chipsToShow,
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

@OptIn(ExperimentalLayoutApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun IndexReplyRow(
    replyText: String,
    bubbleTargetId: String?,
    chips: List<coredevices.ring.data.entity.room.indexfeed.CachedItem>,
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
        Column(
            modifier = Modifier.foundationFillMaxWidth(0.85f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (replyText.isNotBlank()) {
                val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
                val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp))
                        .background(colors.surfaceContainerLow)
                        // Tap opens the linked object (if any); long-press
                        // copies the reply text to clipboard. We collapse
                        // the previous bubble.clickable into combinedClickable
                        // so both gestures work on the same target.
                        .combinedClickable(
                            onClick = { if (bubbleTargetId != null) onOpenObject(bubbleTargetId) },
                            onLongClick = {
                                clipboard.setText(androidx.compose.ui.text.AnnotatedString(replyText))
                                haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            },
                        )
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Text(
                        replyText,
                        color = colors.onSurface,
                        fontSize = 14.5.sp,
                        lineHeight = 21.sp,
                    )
                }
            }
            if (chips.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    chips.take(8).forEach { item ->
                        val label = coredevices.ring.ui.viewmodel.IndexFeedViewModel.chipLabel(item, allLists).take(64)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(percent = 50))
                                .background(colors.redSurface)
                                .border(1.dp, colors.chipOutline, RoundedCornerShape(percent = 50))
                                .clickable { onOpenObject(item.firestoreId) }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            Text(chipGlyph(item.kind), color = colors.primary, fontSize = 12.sp)
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
                }
            }
        }
    }
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
