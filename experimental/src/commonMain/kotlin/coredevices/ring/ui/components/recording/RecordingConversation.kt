package coredevices.ring.ui.components.recording

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coredevices.indexai.data.entity.ConversationMessageEntity
import coredevices.indexai.data.entity.MessageRole
import coredevices.indexai.data.entity.RecordingEntryEntity
import coredevices.mcp.data.SemanticResult
import coredevices.ring.ui.components.chat.ChatBubble
import coredevices.ring.ui.components.chat.RecordingChatBubble
import coredevices.ring.ui.components.chat.ResponseBubble
import coredevices.ring.ui.components.chat.SemanticResultActionTaken
import coredevices.ring.ui.components.chat.SemanticResultDetails
import coredevices.ring.ui.components.chat.SemanticResultIcon
import coredevices.ring.ui.components.chat.ToolExecutionBubble
import coredevices.ring.ui.components.chat.hasExpandedDetails
import coredevices.ring.ui.viewmodel.MessagePlaybackState

fun LazyListScope.recordingConversation(recordingEntries: List<RecordingEntryEntity>, messages: List<ConversationMessageEntity>, onPlayPause: (RecordingEntryEntity) -> Unit, playbackState: MessagePlaybackState) {
    if (messages.isEmpty()) {
        item {
            val recording = remember(recordingEntries) {
                recordingEntries.firstOrNull()
            }
            if (recording != null) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    RecordingChatBubble(
                        enabled = recording.fileName != null,
                        onPlayPause = {
                            onPlayPause(recording)
                        },
                        playing = playbackState is MessagePlaybackState.Playing && playbackState.id == (recording.userMessageId ?: -1),
                        buffering = playbackState is MessagePlaybackState.Buffering && playbackState.id == (recording.userMessageId ?: -1),
                        playbackPercentage = if (playbackState is MessagePlaybackState.Playing && playbackState.id == (recording.userMessageId ?: -1)) playbackState.percentageComplete else 0.0,
                        content = {
                            Text(
                                text = "<No transcription available>",
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    )
                }
            }
        }
    }
    items(messages.size, contentType = { messages[it].document.role }) { i ->
        val message = messages[i]
        when (message.document.role) {
            MessageRole.user -> Box(modifier = Modifier.padding(start = 32.dp, end = 8.dp, bottom = 8.dp, top = 8.dp).fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                val recording = remember(message, recordingEntries) {
                    recordingEntries.firstOrNull { it.userMessageId == message.id }
                }
                if (recording != null) {
                    val playing = remember(playbackState, recording) { playbackState is MessagePlaybackState.Playing && playbackState.id == recording.userMessageId }
                    val buffering = remember(playbackState, recording) { playbackState is MessagePlaybackState.Buffering && playbackState.id == recording.userMessageId }
                    val playbackPercentage = remember(playbackState, recording) {
                        if (playbackState is MessagePlaybackState.Playing && playbackState.id == recording.userMessageId) {
                            playbackState.percentageComplete
                        } else {
                            0.0
                        }
                    }
                    RecordingChatBubble(
                        enabled = recording.fileName != null,
                        onPlayPause = {
                            onPlayPause(recording)
                        },
                        playing = playing,
                        buffering = buffering,
                        playbackPercentage = playbackPercentage,
                        content = {
                            Text(
                                text = message.document.content ?: "<No content>",
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    )
                } else {
                    ChatBubble {
                        Text(message.document.content ?: "")
                    }
                }
            }
            MessageRole.assistant -> Box(modifier = Modifier.padding(start = 8.dp, end = 32.dp, top = 8.dp, bottom = 8.dp)) {
                message.document.content?.ifBlank { null }?.let {
                    if (message.document.tool_calls.isNullOrEmpty()) {
                        ResponseBubble(maxHeight = Dp.Infinity) {
                            Text(it, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            MessageRole.tool -> {
                val semanticResult = message.document.semantic_result
                var expandData by remember { mutableStateOf<Boolean>(false) }
                val modifier = if (semanticResult is SemanticResult.GenericSuccess || semanticResult is SemanticResult.GenericFailure) {
                    Modifier.clickable {
                        expandData = !expandData
                    }
                } else {
                    Modifier
                }
                Column {
                    ToolExecutionBubble(
                        modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 16.dp),
                        icon = {
                            semanticResult?.let { SemanticResultIcon(it, modifier = Modifier.size(12.dp)) }
                                ?: Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(12.dp))
                        },
                        actionText = { semanticResult?.let { SemanticResultActionTaken(it) } ?: Text("Tool") },
                        trailingLink = {},
                    ) {
                        semanticResult?.let {
                            if (semanticResult.hasExpandedDetails()) {
                                SemanticResultDetails(semanticResult, modifier = Modifier.padding(16.dp))
                            }
                        } ?: Text("Tool executed", modifier = Modifier.padding(16.dp))
                    }
                    if (expandData) {
                        val id = message.document.tool_call_id
                        val caller = messages.firstOrNull { it.document.tool_calls?.any { it.id == id } ?: false }
                        if (caller != null) {
                            val call = caller.document.tool_calls?.firstOrNull { it.id == id }
                            Text("${call?.function?.name}():\n${call?.function?.arguments}")
                        }
                    }
                }
            }
        }
    }
}