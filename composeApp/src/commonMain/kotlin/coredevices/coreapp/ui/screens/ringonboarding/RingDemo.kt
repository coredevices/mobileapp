package coredevices.coreapp.ui.screens.ringonboarding

import CoreNav
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.SpeakerNotesOff
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coredevices.indexai.data.entity.RecordingEntryStatus
import coredevices.libindex.database.dao.RingTransferDao
import coredevices.libindex.database.entity.RingTransferStatus
import coredevices.ring.service.RingEvent
import coredevices.ring.service.RingSync
import coredevices.ring.storage.RecordingStorage
import coredevices.util.CoreConfigFlow
import coredevices.util.models.CactusSTTMode
import coredevices.util.models.ModelDownloadStatus
import coredevices.util.models.ModelManager
import coredevices.ring.ui.components.chat.ChatBubble
import coredevices.ring.ui.components.chat.RecordingChatBubble
import coredevices.ring.ui.components.chat.ResponseBubble
import coredevices.ring.ui.components.chat.SemanticResultActionTaken
import coredevices.ring.ui.components.chat.SemanticResultIcon
import coredevices.ring.ui.components.feed.AnimatedAudioBars
import coredevices.ring.ui.components.feed.AudioBars
import coredevices.ring.util.AudioPlayer
import coredevices.ring.util.PlaybackState
import coredevices.util.AudioEncoding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.io.Buffer
import org.koin.compose.koinInject
import kotlin.time.Clock

@Composable
internal fun RingDemo(nav: CoreNav) {
    val ringSync = koinInject<RingSync>()
    val ringTransferDao = koinInject<RingTransferDao>()
    val audioPlayer = koinInject<AudioPlayer>()
    val recordingStorage = koinInject<RecordingStorage>()
    val coreConfigFlow = koinInject<CoreConfigFlow>()
    val modelManager = koinInject<ModelManager>()
    val scope = rememberCoroutineScope()
    val latestRingEvent by ringSync.ringEvents.collectAsStateWithLifecycle(null)
    val latestTransfer by ringTransferDao.getLatestTransferFeedItemFlow().collectAsStateWithLifecycle(null)
    val playerState by audioPlayer.playbackState.collectAsState()
    val coreConfig by coreConfigFlow.flow.collectAsStateWithLifecycle()
    val downloadStatus by modelManager.modelDownloadStatus.collectAsState()

    var buffering by remember { mutableStateOf(false) }
    val buffer = remember { Buffer() }
    DisposableEffect(audioPlayer) {
        onDispose { audioPlayer.stop() }
    }

    val sessionStart = remember { Clock.System.now() }
    val transfer = latestTransfer?.ringTransfer?.takeIf { it.createdAt >= sessionStart }
    val feedItem = latestTransfer?.feedItem?.takeIf { transfer != null }

    val playing = playerState is PlaybackState.Playing
    val playbackPct = (playerState as? PlaybackState.Playing)?.percentageComplete ?: 0.0

    val onPlayPause: () -> Unit = {
        if (playing || buffering) {
            audioPlayer.stop()
            buffering = false
        } else {
            val fileName = feedItem?.entry?.fileName
            if (fileName != null) {
                scope.launch {
                    buffering = true
                    try {
                        // Prefer the processed variant, fall back to original.
                        val (samples, info) = try {
                            recordingStorage.openRecordingSource(fileName)
                        } catch (e: Throwable) {
                            recordingStorage.openRecordingSource(fileName, true)
                        }
                        withContext(Dispatchers.IO) {
                            buffer.clear()
                            samples.use {
                                it.transferTo(buffer)
                            }
                        }
                        audioPlayer.playRaw(
                            buffer,
                            info.cachedMetadata.sampleRate.toLong(),
                            AudioEncoding.PCM_16BIT,
                            info.size,
                        )
                    } catch (_: Throwable) {
                        // Swallow — user can retry from the bug report flow.
                    } finally {
                        buffering = false
                    }
                }
            }
        }
    }

    @Composable
    fun NotWorkingText() {
        Row(
            modifier = Modifier.clickable {
                nav.navigateTo(CommonRoutes.BugReport(pebble = false))
            },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Not working? Report a bug",
                fontSize = 11.sp,
                color = LocalPalette.current.onPrimary.copy(alpha = 0.7f),
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = "Open bug report",
                modifier = Modifier.size(14.dp),
                tint = LocalPalette.current.onPrimary.copy(alpha = 0.7f),
            )
        }
    }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = LocalPalette.current.primary,
            contentColor = LocalPalette.current.onPrimary,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when {
                transfer == null -> {
                    val isLocalOnly = coreConfig.sttConfig.mode == CactusSTTMode.LocalOnly
                    val downloading = downloadStatus as? ModelDownloadStatus.Downloading
                    if (isLocalOnly && downloading != null) {
                        Text(
                            "Downloading speech model\u2026",
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(8.dp))
                        val progress = downloading.progress
                        if (progress != null) {
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Please wait for the download to complete before trying.",
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            color = LocalPalette.current.onPrimary.copy(alpha = 0.7f),
                        )
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(
                                Icons.Default.Mic,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Try it out! Hold the button and speak into the ring, then release.",
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        NotWorkingText()
                    }
                }

                transfer.status == RingTransferStatus.Started -> {
                    Text("Receiving recording...", fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    val progress = (latestRingEvent as? RingEvent.Transfer.InProgress)
                        ?.takeIf { it.transferId == transfer.id }
                        ?.progress
                    if (progress != null) {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    NotWorkingText()
                }

                transfer.status == RingTransferStatus.Discarded -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = LocalPalette.current.error,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Recording too short! Try holding the button a bit longer.",
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                transfer.status == RingTransferStatus.Failed -> {
                    Icon(
                        Icons.Default.Error,
                        contentDescription = null,
                        tint = LocalPalette.current.error,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Transfer failed",
                        color = LocalPalette.current.error,
                        fontSize = 14.sp,
                    )
                    NotWorkingText()
                }

                else -> {
                    val entryStatus = feedItem?.entry?.status
                    val canPlay = feedItem?.entry?.fileName != null &&
                            entryStatus != RecordingEntryStatus.pending
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Box(modifier = Modifier.align(Alignment.End).padding(start = 50.dp)) {
                            if (canPlay) {
                                RecordingChatBubble(
                                    enabled = true,
                                    playing = playing,
                                    buffering = buffering,
                                    playbackPercentage = playbackPct,
                                    onPlayPause = onPlayPause,
                                ) {
                                    if (entryStatus == RecordingEntryStatus.transcription_error) {
                                        AudioBars(randomSeed = feedItem.id.hashCode())
                                    } else {
                                        Text(feedItem.entry?.transcription ?: "...")
                                    }
                                }
                            } else {
                                ChatBubble {
                                    if (feedItem == null) {
                                        AnimatedAudioBars()
                                    } else {
                                        AnimatedAudioBars()
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        ResponseBubble(
                            modifier = Modifier.align(Alignment.Start),
                            leading = {
                                val result = feedItem?.semanticResult
                                when {
                                    result != null ->
                                        SemanticResultIcon(result, modifier = Modifier.size(12.dp))
                                    entryStatus == RecordingEntryStatus.transcription_error ->
                                        Icon(Icons.Outlined.SpeakerNotesOff, null, Modifier.size(12.dp))
                                    else ->
                                        Icon(Icons.Outlined.HourglassEmpty, null, Modifier.size(12.dp))
                                }
                            },
                        ) {
                            when {
                                feedItem?.semanticResult != null ->
                                    SemanticResultActionTaken(feedItem.semanticResult!!)
                                entryStatus == RecordingEntryStatus.transcription_error ->
                                    Text("No action taken")
                                else ->
                                    Text("Thinking...")
                            }
                        }
                    }
                }
            }
        }
    }
}