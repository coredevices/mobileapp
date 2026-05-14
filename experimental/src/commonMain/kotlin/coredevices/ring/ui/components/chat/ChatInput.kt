package coredevices.ring.ui.components.chat

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import coredevices.ring.ui.PreviewWrapper
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
fun ChatInput(
    modifier: Modifier = Modifier,
    isRecording: Boolean = false,
    onMicClick: () -> Unit = {},
    onStopClick: () -> Unit = {},
    onCancelClick: () -> Unit = {},
    onTextSubmit: ((String) -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    var inputText by remember { mutableStateOf("") }
    var focused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    fun clearAndDismiss() {
        inputText = ""
        focusManager.clearFocus()
    }
    Box(
        modifier = Modifier
            .border(2.dp, MaterialTheme.colorScheme.inversePrimary, MaterialTheme.shapes.large)
            .height(48.dp)
            .then(modifier)
    ) {
        AnimatedContent(targetState = isRecording) { recording ->
            if (recording) {
                RecordingIndicator(
                    onStop = onStopClick,
                    onCancel = onCancelClick,
                    modifier = Modifier.fillMaxWidth().fillMaxHeight()
                )
            } else {
                TextField(
                    enabled = onTextSubmit != null,
                    interactionSource = interactionSource,
                    value = inputText,
                    modifier = Modifier.fillMaxWidth().onFocusChanged {
                        focused = it.isFocused
                    },
                    onValueChange = { newText -> inputText = newText },
                    placeholder = { Text("Add to Index...", style = MaterialTheme.typography.bodySmall) },
                    trailingIcon = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            AnimatedContent(targetState = focused) { focused ->
                                if (focused) {
                                    IconButton(onClick = ::clearAndDismiss) { Icon(Icons.Filled.Clear, "Clear") }
                                } else {
                                    IconButton(onClick = onMicClick) { Icon(Icons.Filled.Mic, "Speech Input") }
                                }
                            }
                        }
                    },
                    shape = MaterialTheme.shapes.large,
                    singleLine = true,
                    keyboardActions = KeyboardActions(
                        onSend = {
                            onTextSubmit?.invoke(inputText)
                            inputText = ""
                        }
                    ),
                    keyboardOptions = KeyboardOptions.Default.copy(
                        imeAction = ImeAction.Send
                    ),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        disabledIndicatorColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        errorIndicatorColor = Color.Transparent
                    ),
                    textStyle = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun RecordingIndicator(
    onStop: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition()
    val barHeights = (0..4).map { index ->
        transition.animateFloat(
            initialValue = 0.3f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 400 + index * 80),
                repeatMode = RepeatMode.Reverse
            )
        )
    }

    Row(
        modifier = modifier.padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onCancel) {
            Icon(Icons.Filled.Close, "Cancel", tint = MaterialTheme.colorScheme.error)
        }

        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Recording",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.width(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                barHeights.forEach { height ->
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(16.dp * height.value)
                            .clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.error)
                    )
                }
            }
        }

        IconButton(onClick = onStop) {
            Icon(Icons.Filled.Stop, "Stop", tint = MaterialTheme.colorScheme.error)
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
