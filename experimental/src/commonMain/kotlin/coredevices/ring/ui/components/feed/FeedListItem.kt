package coredevices.ring.ui.components.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.SpeakerNotesOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.filled.Error
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coredevices.indexai.data.entity.RecordingEntryStatus
import coredevices.indexai.database.dao.RecordingFeedItem
import coredevices.ring.ui.components.chat.ChatBubble
import coredevices.ring.ui.components.chat.ResponseBubble
import coredevices.ring.ui.components.chat.SemanticResultActionTaken
import coredevices.ring.ui.components.chat.SemanticResultIcon
import coredevices.ring.ui.isLocale24HourFormat
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.format.char
import kotlinx.datetime.toLocalDateTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.jetbrains.compose.ui.tooling.preview.Preview
import kotlin.random.Random
import kotlin.time.Instant

@Composable
fun FeedListItem(
    chatBubbleModifier: Modifier = Modifier,
    feedItem: RecordingFeedItem,
    onSelected: (() -> Unit)?,
    onHold: (() -> Unit)?,
) {
    var parentCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var userBubbleCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var responseBubbleCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var showOverflowIndicator by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .onGloballyPositioned { parentCoords = it }
            .drawConnectingLine(
                MaterialTheme.colorScheme.outlineVariant,
                parentCoords,
                userBubbleCoords,
                responseBubbleCoords
            )
    ) {
        ChatBubble(
            chatBubbleModifier
                .align(Alignment.End)
                .padding(start = 50.dp)
                .onGloballyPositioned { userBubbleCoords = it }
                .combinedClickable(
                    enabled = feedItem.entry != null,
                    onClick = { onSelected?.invoke() },
                    onLongClick = { onHold?.invoke() }
                ),
        ) {
            val timestamp = remember(feedItem.entry?.timestamp) {
                val ts = feedItem.entry?.ringTransferInfo?.buttonPressed?.let { Instant.fromEpochMilliseconds(it) }
                    ?: feedItem.localTimestamp
                ts.let {
                    it.toLocalDateTime(TimeZone.currentSystemDefault()).format(LocalDateTime.Format {
                        if (isLocale24HourFormat()) {
                            hour()
                            char(':')
                            minute()
                            char(':')
                            second()
                        } else {
                            amPmHour()
                            char(':')
                            minute()
                            char(':')
                            second()
                            char(' ')
                            amPmMarker("am", "pm")
                        }
                    })
                }
            }
            Column {
                Row(modifier = Modifier.align(Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                    if (feedItem?.entry?.status?.isError() == true) {
                        Icon(
                            Icons.Default.Error,
                            contentDescription = "Error",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    timestamp?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                when (feedItem.entry?.status) {
                    null, RecordingEntryStatus.pending -> AnimatedAudioBars()
                    RecordingEntryStatus.transcription_error -> AudioBars(randomSeed = feedItem.id.hashCode())
                    RecordingEntryStatus.agent_processing, RecordingEntryStatus.completed, RecordingEntryStatus.agent_error -> Text(
                        feedItem.entry!!.transcription ?: "No transcription",
                        overflow = TextOverflow.Clip,
                        maxLines = 2,
                        onTextLayout = { result ->
                            showOverflowIndicator = result.hasVisualOverflow
                        }
                    )
                }
                if (showOverflowIndicator) {
                    Text(
                        "Show more...",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        ),
                        modifier = Modifier
                            .align(Alignment.End)
                    )
                }
            }
        }
        Spacer(Modifier.size(8.dp))
        ResponseBubble(
            modifier = Modifier
                .align(Alignment.Start)
                .onGloballyPositioned { responseBubbleCoords = it }
                .clickable { onSelected?.invoke() },
            leading = {
                if (feedItem.semanticResult == null) {
                    if (feedItem.entry?.status == RecordingEntryStatus.transcription_error) {
                        Icon(
                            imageVector = Icons.Outlined.SpeakerNotesOff,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp)
                        )
                    } else {
                        Icon(Icons.Outlined.HourglassEmpty, null, Modifier.size(12.dp))
                    }
                } else {
                    SemanticResultIcon(feedItem.semanticResult!!, modifier = Modifier.size(12.dp))
                }
            }
        ) {
            if (feedItem.semanticResult == null) {
                if (feedItem.entry?.status == RecordingEntryStatus.transcription_error) {
                    Text("No action taken")
                } else {
                    Text("Thinking", overflow = TextOverflow.Ellipsis)
                }
            } else {
                SemanticResultActionTaken(feedItem.semanticResult!!)
            }
        }
    }
}

@Composable
fun AudioBars(
    modifier: Modifier = Modifier,
    barCount: Int = 16,
    maxBarHeight: Dp = 22.dp,
    barWidth: Dp = 6.dp,
    randomSeed: Int? = null
) {
    val random = remember(randomSeed) { randomSeed?.let { Random(randomSeed) } ?: Random }
    val baseHeight = remember { maxBarHeight.value / 3 }
    Row(
        modifier = modifier.height(maxBarHeight),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(barCount) { i ->
            val randomHeight = remember {
                val modifier = random.nextFloat() * (maxBarHeight.value - baseHeight)
                (baseHeight + modifier).dp
            }
            Box(
                modifier = Modifier
                    .width(barWidth)
                    .height(randomHeight)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            )
            if (i < barCount - 1) Spacer(modifier = Modifier.width(1.dp))
        }
    }
}

@Composable
fun AnimatedAudioBars(
    modifier: Modifier = Modifier,
    barCount: Int = 16,
    maxBarHeight: Dp = 22.dp,
    barWidth: Dp = 6.dp,
    intervalMs: Long = 650,
) {
    val baseHeight = remember { maxBarHeight.value / 3 }

    fun heightsForSeed(seed: Int): List<Float> {
        val random = Random(seed)
        return List(barCount) { baseHeight + random.nextFloat() * (maxBarHeight.value - baseHeight) }
    }

    val animatables = remember {
        heightsForSeed(0).map { Animatable(it) }
    }

    LaunchedEffect(Unit) {
        var seed = 1
        while (isActive) {
            delay(intervalMs)
            val targets = heightsForSeed(seed++)
            animatables.forEachIndexed { i, anim ->
                launch { anim.animateTo(targets[i], animationSpec = tween(durationMillis = (intervalMs * 0.75).toInt())) }
            }
        }
    }

    Row(
        modifier = modifier.height(maxBarHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        animatables.forEachIndexed { i, anim ->
            Box(
                modifier = Modifier
                    .width(barWidth)
                    .height(anim.value.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            )
            if (i < barCount - 1) Spacer(modifier = Modifier.width(1.dp))
        }
    }
}

@Preview
@Composable
fun AudioBarsPreview() {
    Box(modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
        Box(modifier = Modifier.padding(16.dp)) {
            AudioBars(randomSeed = 12)
        }
    }
}

private fun Modifier.drawConnectingLine(lineColor: Color, parentCoords: LayoutCoordinates?, userBubbleCoords: LayoutCoordinates?, responseBubbleCoords: LayoutCoordinates?): Modifier {
    return drawBehind {
        if (parentCoords == null || !parentCoords.isAttached) return@drawBehind

        if (userBubbleCoords != null && userBubbleCoords.isAttached && responseBubbleCoords != null && responseBubbleCoords.isAttached) {

            val userBounds = parentCoords.localBoundingBoxOf(userBubbleCoords)
            val responseBounds = parentCoords.localBoundingBoxOf(responseBubbleCoords)

            val startX = userBounds.right + 8.dp.toPx()
            val startY = userBounds.center.y

            val endX = responseBounds.left
            val endY = responseBounds.center.y

            val gutterX = size.width * 0.95f
            val cornerRadius = 16.dp.toPx()

            val path = Path().apply {
                moveTo(startX, startY)

                if (startX < gutterX) {
                    lineTo(gutterX, startY)
                } else {
                    moveTo(gutterX, startY)
                }

                lineTo(gutterX, endY - cornerRadius)

                quadraticTo(
                    gutterX, endY, // Control point (corner)
                    gutterX - cornerRadius, endY // End of curve
                )

                lineTo(endX, endY)
            }

            drawPath(
                path = path,
                color = lineColor,
                style = Stroke(width = 1.dp.toPx())
            )
        }
    }
}