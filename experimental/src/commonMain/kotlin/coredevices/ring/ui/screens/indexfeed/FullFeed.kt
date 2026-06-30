@file:OptIn(
    ExperimentalTime::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
)

package coredevices.ring.ui.screens.indexfeed

import CoreNav
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coredevices.indexai.data.entity.LocalRecording
import coredevices.ring.ui.components.chat.IndexComposeBarHost
import coredevices.ring.ui.navigation.RingRoutes
import coredevices.ring.ui.theme.IndexTheme
import coredevices.ring.ui.theme.IndexThemeHost
import coredevices.ring.ui.theme.indexTextEntryStyle
import coredevices.ring.ui.viewmodel.FullFeedViewModel
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.absoluteValue
import kotlin.math.roundToInt
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

/**
 * Chronological recordings stream — pixel-mirror of the JSX prototype's
 * `FullFeedScreen` (iMessage variant).
 *
 *   <  Index feed                          🔍
 *
 *   ◎  ┌──────┐                  ┌──────────┐
 *      │chip  │                  │ user txt │  ← red bubble
 *      └──────┘                  └──────────┘
 *
 *      TODAY
 *
 *                              ┌────────────┐
 *                              │ user txt   │
 *                              └────────────┘
 *   ◎  ┌─────────────────────┐
 *      │ Added to Notes to … │
 *      └─────────────────────┘
 *   …
 *
 *  [ Type or hold to record         🎤 ]   ← compose bar
 */
@Composable
fun FullFeed(coreNav: CoreNav) {
    val vm = koinViewModel<FullFeedViewModel>()
    val state by vm.state.collectAsStateWithLifecycle()
    val query by vm.query.collectAsState()

    var searching by remember { mutableStateOf(false) }
    LaunchedEffect(searching) { if (!searching) vm.setQuery("") }

    IndexThemeHost {
        val colors = IndexTheme.colors
        val statusBarPad = WindowInsets.statusBars.asPaddingValues()
        val listState = rememberLazyListState()
        val scope = rememberCoroutineScope()
        val timestampRevealState = remember { RecordingTimestampRevealState() }
        var preSearchIndex by rememberSaveable { mutableIntStateOf(0) }
        var preSearchOffset by rememberSaveable { mutableIntStateOf(0) }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.surface)
                .imePadding()
                .padding(top = statusBarPad.calculateTopPadding()),
        ) {
            if (searching) {
                FeedSearchTopBar(
                    value = query,
                    onChange = vm::setQuery,
                    onCancel = {
                        searching = false
                        vm.setQuery("")
                        scope.launch {
                            listState.scrollToItem(preSearchIndex, preSearchOffset)
                        }
                    },
                )
            } else {
                FullFeedTopBar(coreNav = coreNav, onSearch = {
                    preSearchIndex = listState.firstVisibleItemIndex
                    preSearchOffset = listState.firstVisibleItemScrollOffset
                    searching = true
                })
            }

            // ONE-SHOT auto-scroll-to-bottom: fires the first time the
            // entries flow emits a non-empty list, then never again. Both
            // the flag and `listState` are `rememberSaveable`, so when
            // the user pops back from a recording detail screen the saved
            // scroll position restores cleanly without the effect re-
            // firing and snapping to the bottom.
            //
            // Previously this was `LaunchedEffect(state.entries.size)`
            // with no guard — it re-fired on every recomposition (and
            // every list mutation) and clobbered the restored scroll.
            var didInitialScroll by rememberSaveable { mutableStateOf(false) }
            LaunchedEffect(state.entries.isNotEmpty()) {
                if (!didInitialScroll && state.entries.isNotEmpty()) {
                    listState.scrollToItem(state.entries.size - 1)
                    didInitialScroll = true
                }
            }

            // Stick-to-bottom when new entries arrive: if the user is
            // already viewing the bottom of the list (within the last
            // visible item), animate to the new last item so the freshly
            // recorded message + agent reply scroll into view. If they've
            // scrolled up to read older messages, leave them alone.
            var prevSize by remember { mutableIntStateOf(state.entries.size) }
            LaunchedEffect(state.entries.size, query) {
                val newSize = state.entries.size
                if (query.isBlank() && newSize > prevSize && didInitialScroll) {
                    val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
                    // `prevSize - 1` was the previous last index. If they
                    // could see it, treat them as "at the bottom" and
                    // follow the new content.
                    val wasAtBottom = lastVisible >= prevSize - 1
                    if (wasAtBottom) {
                        listState.animateScrollToItem(newSize - 1)
                    }
                }
                prevSize = newSize
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                state.entries.forEach { entry ->
                    when (entry) {
                        is FullFeedViewModel.Entry.DayDivider -> stickyHeader(key = entry.uniqueKey) {
                            DayDivider(entry.label, sticky = true)
                        }
                        is FullFeedViewModel.Entry.RecordingRow -> item(key = entry.uniqueKey) {
                            ImessageRecordingRow(
                                recording = entry.recording,
                                transcription = entry.transcription,
                                retryEntry = entry.retryEntry,
                                assistantReply = entry.assistantReply,
                                chips = entry.chips,
                                timestampRevealState = timestampRevealState,
                                onOpenRecording = { coreNav.navigateTo(RingRoutes.RecordingDetails(entry.recording.id)) },
                                onRetryRecording = { retryEntry ->
                                    vm.retryRecording(entry.recording.id, retryEntry)
                                },
                                onOpenObject = { id -> coreNav.navigateTo(RingRoutes.ObjectDetails(id)) },
                            )
                        }
                    }
                }
                if (state.entries.isEmpty()) {
                    item("empty") {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(48.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                if (query.isNotBlank()) "No matches." else "No recordings yet.",
                                color = IndexTheme.colors.onSurfaceVariant,
                            )
                        }
                    }
                }
                item { Spacer(Modifier.height(16.dp)) }
            }

            // The shell screen owns its own compose bar (HomeFeed renders it
            // inline). Full feed mirrors that — the bottom NavigationBar is
            // provided by the chrome on top of this column.
            IndexComposeBarHost(
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 2.dp, bottom = 4.dp),
                onTextSubmit = vm::submitText,
            )
        }
    }
}

// ── Top bar ────────────────────────────────────────────────────────────

@Composable
private fun FullFeedTopBar(coreNav: CoreNav, onSearch: () -> Unit) {
    val colors = IndexTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = coreNav::goBack) {
            Icon(Icons.AutoMirrored.Default.ArrowBack, "Back", tint = colors.onSurface)
        }
        Text(
            "Index feed",
            color = colors.onSurface,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
        )
        IconButton(onClick = onSearch) {
            Icon(Icons.Default.Search, "Search", tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun FeedSearchTopBar(
    value: String,
    onChange: (String) -> Unit,
    onCancel: () -> Unit,
) {
    val colors = IndexTheme.colors
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }
    Row(
        modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .height(42.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(colors.surfaceContainerLowest)
                .border(1.5.dp, colors.outlineVariant, RoundedCornerShape(percent = 50))
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Search, null, tint = colors.onSurfaceVariant, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            BasicTextField(
                value = value,
                onValueChange = onChange,
                modifier = Modifier.weight(1f).focusRequester(focusRequester),
                singleLine = true,
                textStyle = TextStyle(color = colors.onSurface, fontSize = 15.sp).indexTextEntryStyle(),
                cursorBrush = SolidColor(colors.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    focusManager.clearFocus()
                    keyboard?.hide()
                }),
                decorationBox = { inner ->
                    if (value.isEmpty()) Text("Search…", color = colors.onSurfaceVariant, fontSize = 15.sp)
                    inner()
                },
            )
            if (value.isNotEmpty()) {
                IconButton(onClick = { onChange("") }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, "Clear", tint = colors.onSurfaceVariant, modifier = Modifier.size(16.dp))
                }
            }
        }
        Text(
            "Cancel",
            color = colors.primary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clickable { onCancel() }.padding(8.dp),
        )
    }
}

// ── Day divider (centered, no lines per prototype) ─────────────────────

@Composable
private fun DayDivider(label: String, sticky: Boolean = false) {
    val colors = IndexTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = if (sticky) 6.dp else 4.dp, bottom = if (sticky) 2.dp else 0.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label.uppercase(),
            modifier = Modifier
                .clip(RoundedCornerShape(percent = 50))
                .background(colors.surfaceContainerLow)
                .border(1.dp, colors.outlineVariant, RoundedCornerShape(percent = 50))
                .padding(horizontal = 10.dp, vertical = 4.dp),
            color = colors.onSurfaceVariant,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.8.sp,
        )
    }
}

// ── iMessage row: user bubble first (right), then ◎ + chips below (left) ─

@Composable
private fun ImessageRecordingRow(
    recording: LocalRecording,
    transcription: String,
    retryEntry: coredevices.indexai.data.entity.RecordingEntryEntity?,
    assistantReply: String?,
    chips: List<FullFeedViewModel.Chip>,
    timestampRevealState: RecordingTimestampRevealState,
    onOpenRecording: () -> Unit,
    onRetryRecording: (coredevices.indexai.data.entity.RecordingEntryEntity) -> Unit,
    onOpenObject: (String) -> Unit,
) {
    val colors = IndexTheme.colors

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
        // 1. User transcription bubble first — right-aligned, red, white text.
        SwipeRevealRecordingBubble(
            text = transcription.takeIf { it.isNotBlank() }
                ?: recording.assistantTitle?.takeIf { it.isNotBlank() }
                ?: "Index Recording",
            timestamp = recording.localTimestamp,
            revealState = timestampRevealState,
            onOpenRecording = onOpenRecording,
            onRetry = retryEntry?.let { entry -> { onRetryRecording(entry) } },
        )

        // 2. Assistant action chips below the user bubble (matches prototype).
        if (chips.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(colors.redSurface),
                    contentAlignment = Alignment.Center,
                ) {
                    RingGlyph(sizeDp = 13, color = colors.primary)
                }
                Spacer(Modifier.width(6.dp))
                FlowRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    chips.forEach { chip ->
                        ActionChip(chip = chip, onClick = { onOpenObject(chip.itemId) })
                    }
                }
            }
        }

        // 3. Optional assistant reply bubble (left-aligned, after chips).
        if (!assistantReply.isNullOrBlank()) {
            Spacer(Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Spacer(Modifier.width(32.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .clip(RoundedCornerShape(18.dp, 18.dp, 18.dp, 5.dp))
                        .background(colors.surfaceContainerLow)
                        .clickable { onOpenRecording() }
                        .padding(horizontal = 13.dp, vertical = 9.dp),
                ) {
                    Text(
                        assistantReply,
                        color = colors.onSurface,
                        fontSize = 13.5.sp,
                        lineHeight = 18.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private class RecordingTimestampRevealState {
    var dragging by mutableStateOf(false)
    var dragOffsetPx by mutableFloatStateOf(0f)
    var settledOffsetPx by mutableFloatStateOf(0f)
}

@Composable
private fun SwipeRevealRecordingBubble(
    text: String,
    timestamp: Instant,
    revealState: RecordingTimestampRevealState,
    onOpenRecording: () -> Unit,
    onRetry: (() -> Unit)?,
) {
    val colors = IndexTheme.colors
    val revealWidthPx = with(LocalDensity.current) { 82.dp.toPx() }
    val animatedOffsetPx by animateFloatAsState(
        targetValue = if (revealState.dragging) revealState.dragOffsetPx else revealState.settledOffsetPx,
        label = "recording timestamp reveal",
    )
    val revealProgress = (animatedOffsetPx.absoluteValue / revealWidthPx).coerceIn(0f, 1f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(revealWidthPx) {
                detectHorizontalDragGestures(
                    onDragStart = {
                        revealState.dragging = true
                        revealState.dragOffsetPx = revealState.settledOffsetPx
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        revealState.dragOffsetPx =
                            (revealState.dragOffsetPx + dragAmount).coerceIn(-revealWidthPx, 0f)
                    },
                    onDragEnd = {
                        revealState.settledOffsetPx = if (revealState.dragOffsetPx.absoluteValue > revealWidthPx * 0.42f) {
                            -revealWidthPx
                        } else {
                            0f
                        }
                        revealState.dragging = false
                    },
                    onDragCancel = {
                        revealState.settledOffsetPx = 0f
                        revealState.dragging = false
                    },
                )
            },
    ) {
        Text(
            formatBubbleTimestamp(timestamp),
            color = colors.onSurfaceVariant,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 2.dp)
                .alpha(revealProgress),
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset { IntOffset(animatedOffsetPx.roundToInt(), 0) }
                .clip(RoundedCornerShape(20.dp, 20.dp, 5.dp, 20.dp))
                .background(colors.primary)
                .clickable { onOpenRecording() }
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(0.85f),
            ) {
                if (onRetry != null) {
                    IconButton(
                        onClick = onRetry,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Retry transcription",
                            tint = colors.onPrimary,
                            modifier = Modifier.size(17.dp),
                        )
                    }
                    Spacer(Modifier.width(2.dp))
                }
                Text(
                    text,
                    color = colors.onPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Normal,
                    lineHeight = 20.sp,
                    letterSpacing = (-0.1).sp,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

private fun formatBubbleTimestamp(timestamp: Instant): String {
    val local = timestamp.toLocalDateTime(TimeZone.currentSystemDefault())
    val hour12 = (local.hour % 12).let { if (it == 0) 12 else it }
    val minute = local.minute.toString().padStart(2, '0')
    val amPm = if (local.hour < 12) "AM" else "PM"
    return "$hour12:$minute $amPm"
}

@Composable
private fun ActionChip(chip: FullFeedViewModel.Chip, onClick: () -> Unit) {
    val colors = IndexTheme.colors
    // Prototype: bg = redSurface, fg = onPrimaryContainer (light pink in dark
    // mode, dark red in light), border = chipOutline (always #FFDAD4),
    // icon = primary.
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(colors.redSurface)
            .border(1.dp, colors.chipOutline, RoundedCornerShape(percent = 50))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(chip.glyph, color = colors.primary, fontSize = 12.sp)
        Spacer(Modifier.width(5.dp))
        Text(
            chip.label,
            color = colors.onPrimaryContainer,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Two concentric outlined circles + a small filled mic-pip dot at the top.
 *  Matches the prototype's `NIcon.ring` SVG (no filled center). */
@Composable
private fun RingGlyph(sizeDp: Int, color: Color) {
    androidx.compose.foundation.Canvas(modifier = Modifier.size(sizeDp.dp)) {
        val s = this.size.minDimension / 24f
        val cx = 12f * s
        val cy = 12.5f * s
        drawCircle(
            color = color,
            radius = 8f * s,
            center = androidx.compose.ui.geometry.Offset(cx, cy),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.8f * s),
        )
        drawCircle(
            color = color.copy(alpha = 0.55f),
            radius = 4.5f * s,
            center = androidx.compose.ui.geometry.Offset(cx, cy),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.4f * s),
        )
        drawCircle(
            color = color,
            radius = 1.6f * s,
            center = androidx.compose.ui.geometry.Offset(cx, 4.2f * s),
        )
    }
}
