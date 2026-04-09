package coredevices.pebble.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import coredevices.pebble.rememberLibPebble
import kotlinx.coroutines.flow.collectLatest
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

private const val MAX_RESPONSES = 8
private const val MAX_RESPONSE_LENGTH = 30

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun CannedRepliesScreen(nav: NavBarNav, topBarParams: TopBarParams) {
    LaunchedEffect(Unit) {
        topBarParams.searchAvailable(null)
        topBarParams.actions {}
        topBarParams.title("Quick replies")
    }

    val libPebble = rememberLibPebble()
    val config by libPebble.config.collectAsState()
    val saved = config.notificationConfig.cannedResponses

    val responses = remember(saved) { mutableStateListOf(*saved.toTypedArray()) }
    val responseKeys = remember { mutableStateListOf(*Array(saved.size) { it }) }
    var nextKey by remember { mutableIntStateOf(saved.size) }
    var dirty by remember(saved) { mutableStateOf(false) }
    var focusNewIndex by remember { mutableStateOf(-1) }
    var showExitDialog by remember { mutableStateOf(false) }

    val dirtyState = rememberUpdatedState(dirty)
    val navState = rememberUpdatedState(nav)

    LaunchedEffect(Unit) {
        topBarParams.overrideGoBack.collectLatest {
            if (dirtyState.value) {
                showExitDialog = true
            } else {
                navState.value.goBack()
            }
        }
    }

    BackHandler(enabled = dirty) {
        showExitDialog = true
    }

    // Sync keys after external config changes (different number of items)
    if (responseKeys.size != responses.size) {
        responseKeys.clear()
        responseKeys.addAll(List(responses.size) { it })
        nextKey = responses.size
    }

    fun save() {
        val trimmed = responses.map { it.trim() }.filter { it.isNotEmpty() }
        if (trimmed.size != responses.size) {
            val keptKeys = responses.indices
                .filter { responses[it].trim().isNotEmpty() }
                .map { responseKeys[it] }
            responses.clear()
            responses.addAll(trimmed)
            responseKeys.clear()
            responseKeys.addAll(keptKeys)
        }
        libPebble.updateConfig(
            config.copy(
                notificationConfig = config.notificationConfig.copy(
                    cannedResponses = trimmed,
                ),
            ),
        )
        dirty = false
    }

    fun exitAfterSaveOrDiscard() {
        showExitDialog = false
        nav.goBack()
    }

    if (showExitDialog) {
        UnsavedRepliesDialog(
            onSave = {
                save()
                exitAfterSaveOrDiscard()
            },
            onDiscard = { exitAfterSaveOrDiscard() },
            onCancel = { showExitDialog = false },
        )
    }

    val hapticFeedback = LocalHapticFeedback.current
    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val fromIdx = from.index - 1
        val toIdx = to.index - 1
        if (fromIdx in responses.indices && toIdx in responses.indices) {
            responses.add(toIdx, responses.removeAt(fromIdx))
            responseKeys.add(toIdx, responseKeys.removeAt(fromIdx))
            dirty = true
            hapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item(key = "header") {
                Text(
                    text = "Custom replies appear in the \"Canned messages\" menu when replying to notifications on the watch.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }

            itemsIndexed(responses, key = { index, _ -> responseKeys[index] }) { index, text ->
                ReorderableItem(reorderableState, key = responseKeys[index]) { _ ->
                    val focusRequester = remember { FocusRequester() }

                    LaunchedEffect(focusNewIndex) {
                        if (focusNewIndex == index) {
                            focusRequester.requestFocus()
                            focusNewIndex = -1
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        IconButton(
                            onClick = {},
                            modifier = Modifier.draggableHandle(
                                onDragStarted = {
                                    hapticFeedback.performHapticFeedback(
                                        HapticFeedbackType.GestureThresholdActivate
                                    )
                                },
                                onDragStopped = {
                                    hapticFeedback.performHapticFeedback(
                                        HapticFeedbackType.GestureEnd
                                    )
                                },
                            ),
                        ) {
                            Icon(Icons.Default.DragHandle, contentDescription = "Reorder")
                        }
                        OutlinedTextField(
                            value = text,
                            onValueChange = {
                                if (it.length <= MAX_RESPONSE_LENGTH) {
                                    responses[index] = it
                                    dirty = true
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(focusRequester),
                            singleLine = true,
                            label = { Text("Reply ${index + 1}") },
                        )
                        IconButton(onClick = {
                            responses.removeAt(index)
                            responseKeys.removeAt(index)
                            dirty = true
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "Remove")
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Button(
                onClick = {
                    responses.add("")
                    responseKeys.add(nextKey++)
                    dirty = true
                    focusNewIndex = responses.size - 1
                },
                enabled = responses.size < MAX_RESPONSES,
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text("Add reply", modifier = Modifier.padding(start = 4.dp))
            }
            Button(
                onClick = { save() },
                enabled = dirty,
            ) {
                Text("Save")
            }
        }
    }
}

@Composable
fun UnsavedRepliesDialog(
    onSave: () -> Unit,
    onDiscard: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Unsaved changes") },
        text = { Text("You have unsaved changes to your quick replies.") },
        confirmButton = {
            TextButton(onClick = onSave) { Text("Save") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onCancel) { Text("Cancel") }
                TextButton(onClick = onDiscard) { Text("Discard") }
            }
        },
    )
}
