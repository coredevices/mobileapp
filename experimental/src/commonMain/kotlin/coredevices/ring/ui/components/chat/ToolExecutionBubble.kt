package coredevices.ring.ui.components.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.dp
import coredevices.ring.ui.PreviewWrapper
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
fun ToolExecutionBubble(
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
    actionText: @Composable () -> Unit,
    trailingLink: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    val shape = remember { RoundedCornerShape(16.dp) }
    Box(modifier) {
        Column {
            Box(
                modifier = Modifier
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.inversePrimary,
                        shape
                    )
                    .fillMaxWidth()
            ) {
                content()
            }
            trailingLink()
        }
        ActionTakenChip(
            modifier = Modifier.align(Alignment.TopEnd).padding(end = 16.dp).offset(y = (-12).dp),
            icon = icon,
            text = actionText
        )
    }
}

@Preview
@Composable
fun ToolExecutionBubblePreview() {
    PreviewWrapper {
        Box(modifier = Modifier.fillMaxWidth().padding(32.dp)) {
            ToolExecutionBubble(
                modifier = Modifier.fillMaxWidth(),
                icon = { Icon(Icons.Outlined.CheckBox, contentDescription = null, modifier = Modifier.size(12.dp)) },
                actionText = { Text("Reminder created") },
                trailingLink = {
                    TextButton(
                        onClick = {},
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                    ) {
                        Text("Reminders")
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.AutoMirrored.Default.ArrowForward, contentDescription = null)
                    }
                },
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("December 11, 9:00pm", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    Row(modifier = Modifier.padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(false, onCheckedChange = {})
                        Text("Take out the trash")
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionTakenChip(
    icon: @Composable () -> Unit,
    text: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.height(24.dp).clipToBounds().border(
            1.dp,
            MaterialTheme.colorScheme.inversePrimary,
            RoundedCornerShape(100)
        ).background(MaterialTheme.colorScheme.surface).padding(vertical = 4.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        ProvideTextStyle(
            value = MaterialTheme.typography.bodySmall
        ) {
            icon()
            text()
        }
    }
}