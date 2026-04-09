package coredevices.ring.ui.screens

import CoreNav
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import coredevices.indexai.database.dao.LocalRecordingDao
import coredevices.ring.data.entity.room.RingTransferStatus
import coredevices.ring.database.room.repository.RingTransferRepository
import org.koin.compose.koinInject

@Composable
fun RingSyncInspectorScreen(nav: CoreNav) {
    val recordingsDao = koinInject<LocalRecordingDao>()
    val transfersRepository = koinInject<RingTransferRepository>()
    val pager = remember {
        Pager(
            config = PagingConfig(pageSize = 10, enablePlaceholders = false),
            pagingSourceFactory = {
                transfersRepository.getPaginatedTransfersWithFeedItem(includeDiscarded = true)
            }
        ).flow
    }.collectAsLazyPagingItems()
    val listState = rememberLazyListState()

    LaunchedEffect(pager.itemCount) {
        listState.animateScrollToItem(0)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(
                        onClick = { nav.goBack() }
                    ) {
                        Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = "Go Back")
                    }
                },
                title = { Text("Ring Sync Inspector") }
            )
        }
    ) { insets ->
        LazyColumn(modifier = Modifier.padding(insets).fillMaxSize(), state = listState) {
            items(
                count = pager.itemCount,
                key = pager.itemKey { it.ringTransfer?.id ?: it.feedItem!!.id }
            ) { i ->
                val item = pager[i]
                if (item != null) {
                    val (transfer, recording) = item
                    ListItem(
                        overlineContent = {
                            if (transfer?.status == RingTransferStatus.Discarded) {
                                Text("DISCARDED")
                            }
                        },
                        headlineContent = { Text(buildString {
                            append("Index: ${transfer?.transferInfo?.collectionStartIndex}")
                            if (transfer?.transferInfo?.collectionStartIndex != transfer?.transferInfo?.collectionEndIndex) {
                                append("-${item.ringTransfer?.transferInfo?.collectionEndIndex}")
                            }
                        }) },
                        supportingContent = {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("ID: ${item.ringTransfer?.id ?: (recording?.id.toString() + "( legacy )")}")
                                Text("Timestamp: ${item.ringTransfer?.createdAt ?: recording?.localTimestamp}")
                                Text("Status: ${
                                    (transfer?.status.takeIf { it != RingTransferStatus.Completed }
                                        ?: recording?.entry?.status
                                        ?: RingTransferStatus.Completed).name
                                }")
                                Text("Transcription: ${recording?.entry?.transcription}")
                                Text("LLM Result: ${recording?.semanticResult}")
                                Text("Error: ${recording?.entry?.error}")
                            }
                        }
                    )
                } else {
                    ListItem(
                        headlineContent = { Text("Loading...") }
                    )
                }
            }
        }
    }
}