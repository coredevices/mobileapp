package coredevices.ring.ui.screens.recording

import BugReportButton
import CoreNav
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.style.TextAlign
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
import coredevices.ring.ui.components.recording.recordingConversation
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

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(
                        onClick = coreNav::goBack
                    ) {
                        Icon(
                            Icons.AutoMirrored.Default.ArrowBack,
                            contentDescription = stringResource(
                                UtilRes.string.back
                            )
                        )
                    }
                },
                title = {
                    if (itemState is RecordingDetailsViewModel.ItemState.Loaded) {
                        Text((itemState as RecordingDetailsViewModel.ItemState.Loaded).recording.assistantTitle ?: "Index Recording")
                    }
                },
                actions = {
                    BugReportButton(
                        coreNav,
                        pebble = false,
                        screenContext = mapOf(
                            "screen" to "RecordingDetails",
                            "transcriptionModel" to( (itemState as? RecordingDetailsViewModel.ItemState.Loaded)?.entries?.firstOrNull()?.transcribedUsingModel ?: "<unknown>"),
                            "state" to itemState.toString(),
                            "recordingId" to id.toString()
                        ),
                        recordingPath = (viewModel.itemState.value as? RecordingDetailsViewModel.ItemState.Loaded)
                            ?.entries?.firstOrNull()?.fileName,
                    )
                    IconButton(
                        onClick = viewModel::toggleMoreMenu
                    ) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = stringResource(Res.string.more_options)
                        )
                    }
                    DropdownMenu(
                        expanded = moreMenuExpanded,
                        onDismissRequest = viewModel::dismissMoreMenu
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.export_recording)) },
                            onClick = {
                                viewModel.exportRecording()
                                viewModel.dismissMoreMenu()
                            }
                        )
                    }
                }
            )
        },
        bottomBar = {
            BottomAppBar(Modifier.padding(8.dp)) {
                ChatInput(onMicClick = viewModel::beginRecordingReply)
            }
        }
    ) { insets ->
        Box(
            modifier = Modifier.padding(insets).fillMaxSize(),
        ) {
            when (val state = itemState) {
                is RecordingDetailsViewModel.ItemState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                is RecordingDetailsViewModel.ItemState.Error -> {
                    Text("Error loading recording", modifier = Modifier.align(Alignment.Center))
                }

                is RecordingDetailsViewModel.ItemState.Loaded -> {
                    RecordingDetailsContents(
                        recording = state.recording,
                        messages = state.messages,
                        entries = state.entries,
                        playbackState = playbackState,
                        togglePlayback = viewModel::togglePlayback,
                        showDebugDetails = showDebugDetails
                    )
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
    playbackState: MessagePlaybackState,
    togglePlayback: (RecordingEntryEntity) -> Unit,
    showDebugDetails: Boolean,
) {
    LazyColumn {
        if (showDebugDetails) {
            items(entries.size, contentType = { "debug_details" } ) { i ->
                val timestamp = entries[i].timestamp
                entries[i].ringTransferInfo?.let { entry ->
                    OutlinedCard(modifier = Modifier.padding(horizontal = 8.dp).fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                "Ring Recording $i (Index ${entry.collectionStartIndex}, end: ${entry.collectionEndIndex})",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text("Release->RX Latency: ${entry.buttonReleaseAdvertisementLatencyMs} ms")
                            val rxFeed =
                                (timestamp - Instant.fromEpochMilliseconds(
                                    entry.advertisementReceived
                                ))
                            Text("RX->Feed Latency: ${rxFeed.inWholeMilliseconds} ms")
                        }
                    }
                }
            }
            item(contentType = "spacer") {
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
        try {
            recordingConversation(messages = messages, recordingEntries = entries, onPlayPause = togglePlayback, playbackState = playbackState)
        } catch (e: Exception) {
            Firebase.crashlytics.recordException(e)
            item {
                Text(
                    "Error loading conversation",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}