package coredevices.pebble.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import co.touchlab.kermit.Logger
import coredevices.pebble.rememberLibPebble
import io.rebble.libpebblecommon.connection.KnownPebbleDevice
import io.rebble.libpebblecommon.locker.AppType
import io.rebble.libpebblecommon.metadata.WatchType
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState
import sh.calvin.reorderable.rememberReorderableLazyListState
import kotlin.uuid.Uuid

private val logger = Logger.withTag("MyCollectionScreen")

class MyCollectionViewModel: ViewModel() {
    val showIncompatible = mutableStateOf(false)
    val showScaled = mutableStateOf(true)
}

@Composable
fun MyCollectionScreen(
    navBarNav: NavBarNav,
    topBarParams: TopBarParams,
    appType: AppType,
) {
    val viewModel = koinViewModel<MyCollectionViewModel>()
    val libPebble = rememberLibPebble()
    val watchesFiltered = remember {
        libPebble.watches.map {
            it.sortedWith(PebbleDeviceComparator).filterIsInstance<KnownPebbleDevice>()
                .firstOrNull()
        }
    }
    val lastConnectedWatch by watchesFiltered.collectAsState(null)
    val watchType = lastConnectedWatch?.watchType?.watchType ?: WatchType.DIORITE
    val searchState = rememberSearchState()
    LaunchedEffect(Unit) {
        topBarParams.searchAvailable(searchState)
        topBarParams.title(appType.myCollectionName())
        topBarParams.actions {}
    }
    val lockerEntries = loadLockerEntries(
        type = appType,
        searchQuery = searchState.query,
        watchType = watchType,
        showIncompatible = viewModel.showIncompatible.value,
        showScaled = viewModel.showScaled.value,
    )
    if (lockerEntries == null) {
        return
    }

    // Mutable copy which we will mutate during drag operations
    var mutableApps by remember(lockerEntries) { mutableStateOf(lockerEntries) }
    val lazyGridState = rememberLazyGridState()
    val lazyListState = rememberLazyListState()
    val hapticFeedback = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    fun onReorder(from: Int, to: Int) {
        logger.v { "drag: from from to $to" }
        mutableApps = mutableApps.toMutableList().apply {
            add(to, removeAt(from))
        }
        hapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
    }

    val reorderableLazyGridState = rememberReorderableLazyGridState(lazyGridState) { from, to ->
        onReorder(from.index, to.index)
    }
    val reorderableLazyListState = rememberReorderableLazyListState(lazyListState) { from, to ->
        onReorder(from.index, to.index)
    }

    fun onDragStarted() {
        hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
        logger.v { "onDragStarted" }
    }

    fun onDragStopped(uuid: Uuid) {
        hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureEnd)
        val newPosition = mutableApps.indexOfFirst { it.uuid == uuid }
        logger.v { "onDragStopped: newPosition = $newPosition" }
        scope.launch {
            libPebble.setAppOrder(uuid, newPosition)
        }
    }

    Column {
        AppsFilterRow(
            watchType = watchType,
            selectedType = null,
            showIncompatible = viewModel.showIncompatible,
            showScaled = viewModel.showScaled,
        )
        when (appType) {
            AppType.Watchface -> {
                LazyVerticalGrid(
                    state = lazyGridState,
                    columns = GridCells.FixedSize(120.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    items(mutableApps, key = { it.uuid }) { entry ->
                        ReorderableItem(reorderableLazyGridState, key = entry.uuid) { isDragging ->
                            Box(
                                modifier = Modifier.longPressDraggableHandle(
                                    onDragStarted = { onDragStarted() },
                                    onDragStopped = { onDragStopped(entry.uuid) },
                                ).shake(isDragging)
                            ) {
                                NativeWatchfaceCard(
                                    entry,
                                    navBarNav,
                                    width = 120.dp,
                                    topBarParams = topBarParams,
                                    highlightInLocker = false,
                                )
                            }
                        }
                    }
                }
            }

            AppType.Watchapp -> {
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(4.dp),
                ) {
                    items(mutableApps, key = { it.uuid }) { entry ->
                        ReorderableItem(reorderableLazyListState, key = entry.uuid) { isDragging ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.longPressDraggableHandle(
                                    onDragStarted = { onDragStarted() },
                                    onDragStopped = { onDragStopped(entry.uuid) },
                                ).shake(isDragging)
                                    .fillMaxWidth()
                            ) {
                                NativeWatchfaceListItem(
                                    entry = entry,
                                    onClick = {
                                        navBarNav.navigateTo(
                                            PebbleNavBarRoutes.LockerAppRoute(
                                                uuid = entry.uuid.toString(),
                                                storedId = entry.storeId,
                                                storeSource = entry.appstoreSource?.id,
                                            )
                                        )
                                    },
                                    topBarParams = topBarParams,
                                    highlightInLocker = false,
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
fun Modifier.shake(enabled: Boolean): Modifier {
    if (!enabled) return this

    val infiniteTransition = rememberInfiniteTransition()
    val rotation by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(100, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    return this.graphicsLayer {
        rotationZ = rotation // Slight rotation for a "jiggle" effect
        translationX = rotation * 2.dp.toPx() // Horizontal movement
    }
}
