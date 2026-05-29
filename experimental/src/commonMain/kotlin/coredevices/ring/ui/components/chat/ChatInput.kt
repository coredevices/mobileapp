package coredevices.ring.ui.components.chat

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coredevices.ring.ui.PreviewWrapper
import coredevices.ring.ui.theme.IndexTheme
import coredevices.ring.ui.theme.IndexThemeHost
import coredevices.ring.ui.theme.indexTextEntryStyle
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Pebble Index compose bar — outlined pill text field + separate red mic
 * circle on the right. Mirrors the JSX prototype's `ComposeBar`. Holding
 * the mic kicks the existing audio-record path; in `isRecording` mode the
 * pill gets replaced by an animated waveform with Stop / Cancel buttons.
 *
 * Public API is unchanged from the previous version so all callers
 * (FeedTabContents, RecordingDetails, IndexFeedScreen, …) keep working.
 */
@Composable
fun ChatInput(
    modifier: Modifier = Modifier,
    isRecording: Boolean = false,
    onMicClick: () -> Unit = {},
    onStopClick: () -> Unit = {},
    onCancelClick: () -> Unit = {},
    onTextSubmit: ((String) -> Unit)? = null
) {
    IndexThemeHost {
    val colors = IndexTheme.colors
    var inputText by remember { mutableStateOf("") }
    val hasText = inputText.isNotBlank()

    fun submitText() {
        val text = inputText.trim()
        if (text.isEmpty()) return
        onTextSubmit?.invoke(text)
        inputText = ""
    }

    Row(
        modifier = Modifier.fillMaxWidth().then(modifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .clip(RoundedCornerShape(percent = 50))
                .border(1.dp, colors.outlineVariant, RoundedCornerShape(percent = 50)),
            contentAlignment = Alignment.CenterStart,
        ) {
            AnimatedContent(targetState = isRecording) { recording ->
                if (recording) {
                    RecordingIndicator(
                        onStop = onStopClick,
                        onCancel = onCancelClick,
                        modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                    )
                } else {
                    TextPill(
                        value = inputText,
                        enabled = onTextSubmit != null,
                        onValueChange = { inputText = it },
                        onSubmit = ::submitText,
                    )
                }
            }
        }
        if (!isRecording) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(colors.primary)
                    .clickable {
                        if (hasText) submitText() else onMicClick()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (hasText) Icons.Filled.ArrowUpward else Icons.Filled.Mic,
                    contentDescription = if (hasText) "Send" else "Hold to record",
                    tint = colors.onPrimary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
    }
}

@Composable
private fun TextPill(
    value: String,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    val colors = IndexTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            modifier = Modifier.weight(1f),
            singleLine = true,
            textStyle = TextStyle(
                color = colors.onSurface,
                fontSize = 15.sp,
                letterSpacing = (-0.1).sp,
            ).indexTextEntryStyle(),
            cursorBrush = SolidColor(colors.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { onSubmit() }),
            decorationBox = { inner ->
                if (value.isEmpty()) {
                    Text(
                        "Type or hold to record…",
                        color = colors.onSurfaceVariant,
                        fontSize = 15.sp,
                    )
                }
                inner()
            },
        )
        if (value.isNotEmpty()) {
            IconButton(onClick = { onValueChange("") }, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Filled.Close, "Clear", tint = colors.onSurfaceVariant, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun RecordingIndicator(
    onStop: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = IndexTheme.colors
    val transition = rememberInfiniteTransition()
    val barHeights = (0..4).map { index ->
        transition.animateFloat(
            initialValue = 0.3f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 400 + index * 80),
                repeatMode = RepeatMode.Reverse,
            ),
        )
    }

    Row(
        modifier = modifier.padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onCancel) {
            Icon(Icons.Filled.Close, "Cancel", tint = colors.error)
        }

        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Recording",
                color = colors.error,
                fontSize = 13.sp,
            )
            Spacer(Modifier.width(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                barHeights.forEach { height ->
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(16.dp * height.value)
                            .clip(RoundedCornerShape(2.dp))
                            .background(colors.error),
                    )
                }
            }
        }

        IconButton(onClick = onStop) {
            Icon(Icons.Filled.Stop, "Stop", tint = colors.error)
        }
    }
}

@Preview
@Composable
fun ChatInputPreview() {
    PreviewWrapper {
        ChatInput()
    }
}
