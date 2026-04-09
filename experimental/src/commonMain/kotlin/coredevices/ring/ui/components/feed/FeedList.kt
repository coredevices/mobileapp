package coredevices.ring.ui.components.feed

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalClipboard
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import coredevices.pebble.ui.TopBarParams
import coredevices.ring.agent.ContextualActionType
import coredevices.ring.ui.viewmodel.FeedViewModel
import coredevices.util.Platform
import coredevices.util.isAndroid
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun FeedList(topBarParams: TopBarParams?, modifier: Modifier = Modifier, onItemSelected: (Long) -> Unit) {
    val clipboard = LocalClipboard.current
    val viewModel = koinViewModel<FeedViewModel> { parametersOf(clipboard) }
    val items = viewModel.items.collectAsLazyPagingItems()
    val listState = rememberLazyListState()
    val platform = koinInject<Platform>()

    val newestItemKey = items.itemSnapshotList.firstOrNull()?.let { item ->
        when (item) {
            is FeedViewModel.FeedData.Item -> "item-${item.data.id}"
            is FeedViewModel.FeedData.TransferPlaceholder -> "transfer-${item.data.id}"
            is FeedViewModel.FeedData.DateSeparator -> "date-${item.date}"
        }
    }

    LaunchedEffect(newestItemKey) {
        if (newestItemKey != null) {
            listState.animateScrollToItem(0)
        }
    }
    LaunchedEffect(Unit) {
        launch {
            topBarParams?.scrollToTop?.collect {
                listState.animateScrollToItem(0)
            }
        }
    }

    LazyColumn(modifier = modifier, state = listState, reverseLayout = true) {
        items(
            count = items.itemCount,
            key = items.itemKey {
                when (it) {
                    is FeedViewModel.FeedData.DateSeparator -> "date-${it.date}"
                    is FeedViewModel.FeedData.Item -> "item-${it.data.id}"
                    is FeedViewModel.FeedData.TransferPlaceholder -> "transfer-${it.data.id}"
                }
            },
            contentType = { i -> items[i]?.type ?: "null" }
        ) { i ->
            when (val item = items[i]) {
                is FeedViewModel.FeedData.DateSeparator -> {
                    FeedListSectionHeader(item.date)
                }
                is FeedViewModel.FeedData.Item -> {
                    val scope = rememberCoroutineScope()
                    var showContextMenu: Boolean by remember { mutableStateOf(false) }
                    var contextualActions by remember { mutableStateOf<Set<ContextualActionType>?>(null) }
                    ExposedDropdownMenuBox(
                        expanded = showContextMenu,
                        onExpandedChange = { showContextMenu = it }
                    ) {
                        FeedListItem(
                            chatBubbleModifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                            item.data,
                            onSelected = {
                                onItemSelected(item.data.id)
                            },
                            onHold = {
                                scope.launch {
                                    contextualActions = viewModel.getContextualActions(item.data)
                                }
                                showContextMenu = true
                            }
                        )
                        Box(Modifier.fillMaxWidth()) {
                            ExposedDropdownMenu(
                                modifier = Modifier.align(Alignment.CenterEnd),
                                expanded = showContextMenu,
                                onDismissRequest = { showContextMenu = false },
                                matchAnchorWidth = false
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Copy Text") },
                                    onClick = {
                                        viewModel.copyFeedItemTextToClipboard(item.data.entry?.transcription)
                                        showContextMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Mail to me") },
                                    onClick = {
                                        item.data.entry?.transcription?.let {
                                            viewModel.emailFeedItemText(it)
                                        }
                                        showContextMenu = false
                                    }
                                )
                                contextualActions?.forEach {
                                    when (it) {
                                        is ContextualActionType.TaskWithDeadline -> {
                                            DropdownMenuItem(
                                                text = { Text("Add to calendar") },
                                                onClick = {
                                                    item.data.entry?.transcription?.let { tr ->
                                                        viewModel.addToCalendar(
                                                            tr,
                                                            it.deadline,
                                                            it.allDay
                                                        )
                                                    }
                                                    showContextMenu = false
                                                }
                                            )
                                        }
                                        is ContextualActionType.Timer -> {
                                            if (platform.isAndroid) {
                                                DropdownMenuItem(
                                                    text = { Text("Set as timer") },
                                                    onClick = {
                                                        item.data.entry?.transcription?.let { tr ->
                                                            viewModel.setAsTimer(tr, it.fireTime)
                                                        }
                                                        showContextMenu = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                }
                is FeedViewModel.FeedData.TransferPlaceholder -> {
                    FeedTransferPlaceholder(item.data)
                }
                else -> {}
            }
        }
    }

}