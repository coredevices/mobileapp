@file:OptIn(ExperimentalTime::class)

package coredevices.ring.ui.screens.home

import coredevices.ring.ui.relativeTime
import CoreNav
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import coredevices.ring.ui.components.feed.TodoCheckCircle
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coredevices.indexai.data.entity.LocalRecording
import coredevices.indexai.data.entity.RecordingEntryEntity
import coredevices.ring.data.entity.room.indexfeed.CachedItem
import coredevices.ring.data.entity.room.indexfeed.CachedList
import coredevices.ring.data.entity.room.indexfeed.kind
import coredevices.ring.service.indexfeed.DefaultListsBootstrap.Companion.LIST_TODOS_ID
import coredevices.ring.ui.components.chat.IndexComposeBarHost
import coredevices.ring.ui.navigation.RingRoutes
import coredevices.ring.ui.theme.IndexTheme
import coredevices.ring.ui.theme.IndexThemeHost
import coredevices.ring.ui.theme.indexTextEntryStyle
import coredevices.ring.ui.viewmodel.IndexFeedViewModel
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import kotlin.math.abs

// Helper composables for the Index home feed — split out of
// IndexFeedScreen.kt to keep that file focused on the route entry.

@Composable
internal fun IndexHeader(
    searching: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onStartSearch: () -> Unit,
    onCancelSearch: () -> Unit,
    trailingActions: @Composable (RowScope.() -> Unit)? = null,
) {
    val colors = IndexTheme.colors
    val searchFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(searching) {
        if (searching) {
            searchFocus.requestFocus()
            keyboard?.show()
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (searching) {
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
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.weight(1f).focusRequester(searchFocus),
                    singleLine = true,
                    textStyle = TextStyle(color = colors.onSurface, fontSize = 15.sp).indexTextEntryStyle(),
                    cursorBrush = SolidColor(colors.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    decorationBox = { inner ->
                        if (query.isEmpty()) {
                            Text("Search…", color = colors.onSurfaceVariant, fontSize = 15.sp)
                        }
                        inner()
                    },
                )
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, "Clear", tint = colors.onSurfaceVariant, modifier = Modifier.size(16.dp))
                    }
                }
            }
            Text(
                "Cancel",
                color = colors.primary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable { onCancelSearch() }.padding(6.dp),
            )
        } else {
            // Wrap title + sync hint in a weighted Row so the trailing
            // icon buttons are always given their natural width first
            // (protects them under large font scaling / narrow devices).
            // Within the cluster, the title renders at natural width with
            // no wrap so "Index" never squishes, and the sync hint absorbs
            // any leftover space, right-aligning and truncating with an
            // ellipsis when the row gets tight.
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Index",
                    color = colors.onSurface,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.8).sp,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
                PulsingSyncHint(modifier = Modifier.weight(1f))
            }
            IconButton(onClick = onStartSearch) {
                Icon(Icons.Default.Search, "Search", tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
            }
            trailingActions?.invoke(this)
        }
    }
}

@Composable
internal fun PulsingSyncHint(modifier: Modifier = Modifier) {
    val colors = IndexTheme.colors
    var alpha by remember { mutableFloatStateOf(0.45f) }
    LaunchedEffect(Unit) {
        var rising = true
        while (true) {
            delay(40)
            alpha += if (rising) 0.04f else -0.04f
            if (alpha >= 1f) { alpha = 1f; rising = false }
            if (alpha <= 0.45f) { alpha = 0.45f; rising = true }
        }
    }
    Text(
        "Click ring to sync",
        color = colors.onSurfaceVariant,
        fontSize = 12.sp,
        letterSpacing = (-0.05).sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.End,
        modifier = modifier.alpha(alpha).padding(end = 4.dp),
    )
}

/**
 * The peek section header (Index feed) gets a red count chip and a red
 * "See all >" link on the right. It mirrors the prototype's `PeekHeader`.
 */
@Composable
internal fun PeekSectionHeader(
    title: String,
    count: Int,
    onSeeAll: () -> Unit,
) {
    val colors = IndexTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSeeAll() }
            .padding(start = 22.dp, end = 22.dp, top = 4.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            color = colors.onSurfaceVariant,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.1.sp,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "($count)",
            color = colors.onSurfaceVariant,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "See all",
                color = colors.primary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.width(4.dp))
            Text("›", color = colors.primary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** Body section header — `Title (count)` only, no right-side widget.
 *  Matches the prototype's `FeedSectionHeader` for Todos / Notes / You-asked.
 *  The whole row is clickable (still navigates), but the prototype doesn't
 *  surface a "See all" affordance — only the peek header does. */
@Composable
internal fun FeedSectionHeader(
    left: String,
    right: String,
    onClick: (() -> Unit)?,
    topPad: Dp = 12.dp,
) {
    val colors = IndexTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable { onClick() } else it }
            .padding(start = 22.dp, end = 22.dp, top = topPad, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            left,
            color = colors.onSurfaceVariant,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.1.sp,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            "($right)",
            color = colors.onSurfaceVariant,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
internal fun PeekStrip(
    peeks: List<IndexFeedViewModel.UiState.RecordingPeek>,
    onOpenRecording: (LocalRecording) -> Unit,
    onRetryRecording: (LocalRecording, RecordingEntryEntity) -> Unit,
) {
    val visiblePeeks = peeks.take(5)
    val firstId = visiblePeeks.firstOrNull()?.recording?.id
    val listState = rememberLazyListState()
    // When a new recording shows up at the front, LazyRow's default
    // anchoring keeps the previously-visible items pinned to their pixel
    // position — meaning the new card at index 0 ends up off-screen to
    // the left while the user only sees a "hint" of it on the edge.
    // After every change to the leading id, animate-scroll back to 0
    // so the new card actually slides into the prime spot, pushing the
    // older cards to the right.
    LaunchedEffect(firstId) {
        if (firstId != null) {
            listState.animateScrollToItem(0)
        }
    }
    LazyRow(
        state = listState,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 4.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(items = visiblePeeks, key = { it.recording.id }) { peek ->
            // LazyRow + animateItem(fadeInSpec, placementSpec) is doing
            // the work: when a new key enters the list it fades in
            // while existing keys spring rightward to their new layout
            // positions. Combined with the LaunchedEffect above
            // scroll-resetting to 0, the new card visibly takes the
            // leading slot and shoves the rest rightward.
            PeekCard(
                peek = peek,
                onClick = { onOpenRecording(peek.recording) },
                onRetry = { peek.retryEntry?.let { onRetryRecording(peek.recording, it) } },
                modifier = Modifier.animateItem(
                    fadeInSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    placementSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                    fadeOutSpec = spring(stiffness = Spring.StiffnessMediumLow),
                ),
            )
        }
    }
}

@Composable
internal fun PeekCard(
    peek: IndexFeedViewModel.UiState.RecordingPeek,
    onClick: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = IndexTheme.colors
    Column(
        modifier = modifier
            .width(220.dp)
            .height(108.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surfaceContainerLow)
            .border(1.dp, colors.outlineVariant, RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.height(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.size(13.dp), contentAlignment = Alignment.Center) {
                RingGlyph(size = 13.dp, color = colors.primary)
            }
            Spacer(Modifier.width(6.dp))
            Text(
                relativeTime(peek.recording.localTimestamp).uppercase(),
                color = colors.primary,
                fontSize = 11.sp,
                lineHeight = 13.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.6.sp,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            peek.transcription.takeIf { it.isNotBlank() }
                ?: peek.recording.assistantTitle?.takeIf { it.isNotBlank() }
                ?: "Index Recording",
            color = colors.onSurface,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            letterSpacing = (-0.05).sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.weight(1f)) // pushes chip text to the bottom of the fixed-height card
        if (peek.retryEntry != null) {
            // Transcription failed — surface the error and a retry affordance
            // in place of the action chip.
            Row(
                modifier = Modifier.height(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Transcription error",
                    color = colors.error,
                    fontSize = 11.sp,
                    lineHeight = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    "Retry",
                    color = colors.primary,
                    fontSize = 11.sp,
                    lineHeight = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(percent = 50))
                        .clickable { onRetry() }
                        .padding(horizontal = 6.dp, vertical = 1.dp),
                )
            }
        } else {
            Row(
                modifier = Modifier.height(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.size(14.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = if (peek.orphan) colors.onSurfaceVariant else colors.primary,
                        modifier = Modifier.size(13.dp),
                    )
                }
                Spacer(Modifier.width(4.dp))
                Text(
                    peek.primaryChip,
                    color = if (peek.orphan) colors.onSurfaceVariant else colors.primary,
                    fontSize = 11.sp,
                    lineHeight = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
internal fun TaskRow(
    task: CachedItem,
    onToggle: () -> Unit,
    onClick: () -> Unit,
) {
    val colors = IndexTheme.colors
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(horizontal = 22.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            TodoCheckCircle(
                done = task.done,
                onToggle = onToggle,
                modifier = Modifier.padding(top = 1.dp),
            )
            Spacer(Modifier.width(12.dp))
            // Strike-through + faded look while the row lingers post-toggle
            // (the row is dropped from the list ~600 ms later by the
            // viewmodel's animatingDoneIds machinery). The opacity
            // transition gives the row time to fade as it leaves.
            val rowAlpha by animateFloatAsState(
                targetValue = if (task.done) 0.45f else 1f,
                animationSpec = tween(durationMillis = 600),
                label = "task-row-fade",
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .graphicsLayer { alpha = rowAlpha },
            ) {
                Text(
                    task.title,
                    color = colors.onSurface,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = (-0.1).sp,
                    textDecoration = if (task.done) TextDecoration.LineThrough else null,
                    // Tight line-height so the ⏰ subline below feels close,
                    // matching the prototype task row.
                    lineHeight = 17.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                taskSubline(task)?.let { sub ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 0.dp),
                    ) {
                        Icon(
                            Icons.Outlined.AccessTime,
                            null,
                            tint = colors.onSurfaceVariant,
                            modifier = Modifier.size(12.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(sub, color = colors.onSurfaceVariant, fontSize = 12.sp, lineHeight = 13.sp)
                    }
                }
            }
        }
        // Divider intentionally dropped — testing the divider-less list look.
    }
}

internal fun LazyListScope.todosCarousel(
    todos: List<CachedItem>,
    onToggle: (CachedItem) -> Unit,
    onOpen: (CachedItem) -> Unit,
) {
    val pages = todos.chunked(IndexFeedViewModel.TODO_PAGE_SIZE)
    if (pages.isEmpty()) return
    item("todos-pages") {
        // HorizontalPager (compose-foundation) gives us the standard
        // snap-during-fling behaviour out of the box: when the user
        // releases mid-swipe the page settles immediately at the nearest
        // boundary using the same fling physics as the system.
        // The previous implementation waited for `isScrollInProgress`
        // to flip to false (i.e. fling fully stopped) and then ran a
        // separate animateScrollToItem — a two-stage motion that felt
        // slow and jerky on release.
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            // 36dp of next-page peek visible at the right edge so the
            // user knows there's more. Identical visual to the old
            // implementation, just driven by a real pager.
            val pageWidth = maxWidth - 36.dp
            val pagerState = rememberPagerState(pageCount = { pages.size })
            HorizontalPager(
                state = pagerState,
                pageSize = PageSize.Fixed(pageWidth),
                pageSpacing = 8.dp,
                contentPadding = PaddingValues(end = 36.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp, bottom = 10.dp),
            ) { pageIndex ->
                val page = pages[pageIndex]
                Column {
                    page.forEach { task ->
                        TaskRow(
                            task = task,
                            onToggle = { onToggle(task) },
                            onClick = { onOpen(task) },
                        )
                    }
                    // Intentionally NO empty-row spacers. Pager pages can
                    // have natural heights; the pager wraps to the
                    // tallest page. A partial last page just shows empty
                    // space below its content when scrolled to.
                }
            }
        }
    }
}

internal fun taskSubline(task: CachedItem): String? {
    val due = task.dueAt
    return when (task.kind) {
        "reminder" -> due?.let { formatDue(it) }
        "scheduled" -> {
            // We could parse fields for fireKind = alarm/timer; for the home
            // preview just show due-time / "Timer".
            due?.let { formatDue(it) } ?: "Scheduled"
        }
        else -> null
    }
}

internal fun LazyListScope.notesGrid(
    lists: List<IndexFeedViewModel.UiState.NotesList>,
    onOpen: (CachedList) -> Unit,
) {
    val pages = lists.chunked(IndexFeedViewModel.NOTES_PAGE_SIZE)
    item("notes-pages") {
        // ≤ 1 page: render as a static 2x2 grid filling the screen with the
        // same 22dp horizontal padding the rest of the home feed uses, so a
        // user with 4 or fewer note lists sees no awkward right-side gap.
        // > 1 page: keep the snap-paginated LazyRow with a small end-peek
        //   so the next page's first card hints scrollability. The peek
        //   gap (10dp inter-page) matches the intra-page column gap so all
        //   visible card-to-card spacings are identical.
        if (pages.size <= 1) {
            val page = pages.firstOrNull() ?: return@item
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 2.dp),
            ) {
                repeat(2) { rowIndex ->
                    val row = page.drop(rowIndex * 2).take(2)
                    if (row.isEmpty()) return@repeat
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 5.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        row.forEach { entry ->
                            Box(modifier = Modifier.weight(1f)) {
                                ListCard(
                                    entry = entry,
                                    onClick = { onOpen(entry.list) },
                                )
                            }
                        }
                        if (row.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
            return@item
        }
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            // 22dp leading edge to align with the rest of the home feed,
            // ~32dp peek of the next page on the trailing edge. Driven
            // by HorizontalPager so swipe physics match the system snap
            // behaviour (snaps mid-fling rather than after-fling).
            val pageWidth = maxWidth - 22.dp - 32.dp - 10.dp
            val pagerState = rememberPagerState(pageCount = { pages.size })
            HorizontalPager(
                state = pagerState,
                pageSize = PageSize.Fixed(pageWidth),
                pageSpacing = 10.dp,
                contentPadding = PaddingValues(start = 22.dp, end = 32.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp, bottom = 10.dp),
            ) { pageIndex ->
                val page = pages[pageIndex]
                Column {
                    repeat(2) { rowIndex ->
                        val row = page.drop(rowIndex * 2).take(2)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 5.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            row.forEach { entry ->
                                Box(modifier = Modifier.weight(1f)) {
                                    ListCard(
                                        entry = entry,
                                        onClick = { onOpen(entry.list) },
                                    )
                                }
                            }
                            if (row.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                            if (row.isEmpty()) {
                                Spacer(modifier = Modifier.weight(1f).height(NOTES_EMPTY_ROW_HEIGHT))
                                Spacer(modifier = Modifier.weight(1f).height(NOTES_EMPTY_ROW_HEIGHT))
                            }
                        }
                    }
                }
            }
        }
    }
}

private val NOTES_EMPTY_ROW_HEIGHT = 124.dp

@Composable
internal fun ListCard(
    entry: IndexFeedViewModel.UiState.NotesList,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NoteListCard(
        list = entry.list,
        items = entry.items,
        onClick = onClick,
        modifier = modifier,
    )
}

@Composable
fun NoteListCard(
    list: CachedList,
    items: List<CachedItem>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = IndexTheme.colors
    val isChecklist = list.listKind == "checklist"
    val icon = list.icon.trim()
    // Fixed min-height so every Notes card in the home grid lines up with
    // its sibling — short lists (0–2 items) used to render shorter than
    // a full 3-item card, leaving the grid uneven. Sized for header (~22dp)
    // + spacer (8dp) + three item rows (~18dp each) + 12dp top/bottom pad.
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 124.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surfaceContainerLow)
            .border(1.dp, colors.outlineVariant, RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon.isNotEmpty()) {
                Text(text = icon, fontSize = 16.sp)
                Spacer(Modifier.width(6.dp))
            }
            Text(
                list.title.ifBlank { "List" },
                color = colors.onSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.1).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                items.size.toString(),
                color = colors.onSurfaceVariant,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(8.dp))
        if (items.isEmpty()) {
            Text("No 🎶 yet", color = colors.onSurfaceVariant, fontSize = 12.sp)
        } else {
            items.take(3).forEach { item ->
                // Bullet glyph rendered as Text at the same fontSize as the
                // title — Compose baseline-aligns Text composables in a Row by
                // default, so "○ Dried mangoes" reads cleanly without manual
                // top padding (which an Icon would need to fudge).
                //
                // The glyph reflects the **item's own kind** first, so a
                // mixed list (a Notes-to-self list that has both notes and
                // a couple of checklist items in it) renders correctly:
                // checklist items always get "○", notes always get "−",
                // regardless of the parent list's listKind. We fall back
                // to the parent listKind only when the item kind isn't
                // one of those two (shouldn't happen in practice — the
                // Notes filter upstream is `note || checklist`).
                Row(
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier.padding(top = 2.dp),
                ) {
                    val glyph = when (item.kind) {
                        "checklist" -> "○"
                        "note" -> "−"
                        else -> if (isChecklist) "○" else "−"
                    }
                    Text(
                        glyph,
                        color = colors.onSurfaceVariant,
                        fontSize = 12.sp,
                        lineHeight = 15.sp,
                        modifier = Modifier.padding(top = 0.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    // Item title is on-surface (light) per the prototype —
                    // the bullet stays meta-gray so the line reads "○ Title".
                    Text(
                        item.title,
                        color = colors.onSurface,
                        fontSize = 12.sp,
                        lineHeight = 15.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
internal fun AnswerCard(answer: CachedItem, onClick: () -> Unit) {
    val colors = IndexTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surfaceContainerLow)
            .border(1.dp, colors.outlineVariant, RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(
            answer.title,
            color = colors.onSurface,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = (-0.1).sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (answer.body.isNotBlank()) {
            Text(
                answer.body,
                color = colors.onSurfaceVariant,
                fontSize = 12.5.sp,
                lineHeight = 17.5.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/**
 * Pebble Index ring glyph — two concentric outlined circles plus a small
 * filled "mic pip" dot at the top. 1:1 port of the prototype's
 * `NIcon.ring` SVG (frame.jsx) so the icon doesn't have a filled center.
 */
@Composable
internal fun RingGlyph(size: Dp, color: Color) {
    Canvas(modifier = Modifier.size(size)) {
        // SVG viewBox is 24×24; scale to current size.
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

// `relativeTime` lives in `coredevices.ring.ui.util` (single source of truth).
