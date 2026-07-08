@file:OptIn(ExperimentalTime::class)

package coredevices.ring.ui.screens.home

import CoreNav
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
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
import coredevices.ring.data.entity.room.indexfeed.CachedItem
import coredevices.ring.data.entity.room.indexfeed.CachedList
import coredevices.ring.service.indexfeed.DefaultListsBootstrap.Companion.LIST_TODOS_ID
import coredevices.ring.ui.components.chat.IndexComposeBarHost
import coredevices.ring.ui.navigation.RingRoutes
import coredevices.ring.ui.theme.IndexTheme
import coredevices.ring.ui.theme.IndexThemeHost
import coredevices.ring.ui.viewmodel.IndexFeedViewModel
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

/**
 * Index home: peek strip of recent recordings, Todos / Notes / Q&A
 * sections, and a hold-to-record compose bar at the bottom. Hosts the
 * inline [IndexHeader] in place of the bottom-nav shell's TopAppBar.
 */
@Composable
fun IndexFeedScreen(
    coreNav: CoreNav,
    contentPadding: PaddingValues = PaddingValues(),
    scrollToTop: kotlinx.coroutines.flow.Flow<Unit>? = null,
    /** Action icons rendered in [IndexHeader] to the right of the search
     *  button — bug-report / debug / settings. */
    headerActions: @Composable (RowScope.() -> Unit)? = null,
) {
    IndexThemeHost {
        val vm = koinViewModel<IndexFeedViewModel>()
        val state by vm.state.collectAsStateWithLifecycle()
        val query by vm.query.collectAsState()
        var searching by remember { mutableStateOf(false) }
        LaunchedEffect(searching) { if (!searching) vm.clearQuery() }

        val colors = IndexTheme.colors
        val listState = remember { androidx.compose.foundation.lazy.LazyListState() }

        // Re-tap of the bottom-nav Index tab fires `scrollToTop` — bring
        // the home back to the top + close any open search. Initial scroll
        // position is preserved on tab-switch / pop-back, matching every
        // other tab in the app.
        if (scrollToTop != null) {
            LaunchedEffect(scrollToTop) {
                scrollToTop.collect {
                    searching = false
                    vm.clearQuery()
                    listState.animateScrollToItem(0)
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.surface)
                .windowInsetsPadding(WindowInsets.statusBars)
                .imePadding()
                .padding(contentPadding),
        ) {
            IndexHeader(
                searching = searching,
                query = query,
                onQueryChange = vm::setQuery,
                onStartSearch = { searching = true },
                onCancelSearch = { searching = false; vm.clearQuery() },
                trailingActions = headerActions,
            )

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
                // ROOT CAUSE FIX: keep a zero-height stable item pinned at
                // index 0 so async content updates (e.g. recordings loading
                // and the peek section appearing) insert items AFTER this
                // anchor instead of before whatever was previously visible.
                //
                // Without this anchor, LazyColumn's default scroll-anchoring
                // tries to keep the *previously-visible* item at the same
                // pixel position when new items are inserted above it. So
                // when the app launches with state.recordings = empty, the
                // first item is todos-header. Then state populates,
                // peek-header + peek-strip are inserted at the front, and
                // firstVisibleItemIndex shifts from 0 → 2 to keep
                // todos-header pinned. The user lands "scrolled down" with
                // the peek section above the viewport, even though no
                // scroll ever happened.
                //
                // With this anchor permanently at index 0, all content
                // insertions happen at index 1+, the anchor stays at 0,
                // and the user always sees from the top of the feed.
                item("__top_anchor__") { Spacer(Modifier.height(0.dp)) }

                if (state.searching) {
                    item("matchcount") {
                        Text(
                            "${state.matches} ${if (state.matches == 1) "match" else "matches"}",
                            color = colors.onSurfaceVariant,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 4.dp, bottom = 8.dp),
                        )
                    }
                }

                if (state.recordings.isNotEmpty()) {
                    item("peek-header") {
                        PeekSectionHeader(
                            title = "Index feed",
                            count = state.recordings.size,
                            onSeeAll = { coreNav.navigateTo(RingRoutes.FullFeed) },
                        )
                    }
                    item("peek-strip") {
                        PeekStrip(
                            peeks = state.recordings.take(8),
                            onOpenRecording = { coreNav.navigateTo(RingRoutes.RecordingDetails(it.id)) },
                            onRetryRecording = { rec, entry -> vm.retryRecording(rec.id, entry) },
                        )
                    }
                }

                if (state.todosPreview.isNotEmpty() || (!state.searching && state.totalTodos == 0)) {
                    item("todos-header") {
                        FeedSectionHeader(
                            left = "Reminders",
                            right = state.totalTodos.toString(),
                            onClick = { coreNav.navigateTo(RingRoutes.ObjectDetails(LIST_TODOS_ID)) },
                            topPad = 14.dp,
                        )
                    }
                    if (state.todosPreview.isEmpty() && !state.searching) {
                        item("todos-empty") {
                            Text(
                                "You're all done!",
                                color = colors.onSurfaceVariant,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 12.dp, bottom = 4.dp),
                            )
                        }
                    }
                    todosCarousel(
                        todos = state.todosPreview,
                        onToggle = { task -> vm.toggleDoneById(task.firestoreId) },
                        onOpen = { task -> coreNav.navigateTo(RingRoutes.ObjectDetails(task.firestoreId)) },
                    )
                }

                if (state.notesLists.isNotEmpty()) {
                    item("notes-header") {
                        FeedSectionHeader(
                            left = "Notes",
                            right = state.totalNotesLists.toString(),
                            onClick = { coreNav.navigateTo(RingRoutes.AllLists) },
                            topPad = 12.dp,
                        )
                    }
                    notesGrid(state.notesLists, onOpen = { l ->
                        coreNav.navigateTo(RingRoutes.ObjectDetails(l.firestoreId))
                    })
                }

                if (state.answersPreview.isNotEmpty()) {
                    item("answers-header") {
                        FeedSectionHeader(
                            left = "You asked",
                            right = state.totalAnswers.toString(),
                            onClick = { coreNav.navigateTo(RingRoutes.AllAnswers) },
                            topPad = 26.dp,
                        )
                    }
                    items(items = state.answersPreview, key = { it.firestoreId }) { answer ->
                        AnswerCard(
                            answer = answer,
                            onClick = { coreNav.navigateTo(RingRoutes.ObjectDetails(answer.firestoreId)) },
                        )
                    }
                }

                if (state.searching && state.matches == 0) {
                    item("no-matches") {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(36.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "No matches for \"$query\"",
                                color = colors.onSurfaceVariant,
                                fontSize = 14.sp,
                            )
                        }
                    }
                }

                item { Spacer(Modifier.height(16.dp)) }
            }

            IndexComposeBarHost(
                // Match the 12dp side gutters; add an 8dp bottom gap so the
                // chat bar doesn't touch the WatchHomeScreen tab bar that
                // hosts this screen (May 8 fix).
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 2.dp, bottom = 8.dp),
                onTextSubmit = vm::submitText,
            )
        }
    }
}

internal fun formatDue(at: Instant): String {
    val diffMs = at.toEpochMilliseconds() - Clock.System.now().toEpochMilliseconds()
    if (diffMs < 0) return "Overdue"
    val m = (diffMs / 60_000L)
    if (m < 60) return "in ${m}m"
    val h = m / 60
    if (h < 24) return "in ${h}h"
    return "in ${h / 24}d"
}
