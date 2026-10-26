package coredevices.ring.ui.components.recording

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coredevices.ring.ui.components.ShimmerText
import coredevices.ring.ui.theme.IndexTheme
import coredevices.ring.ui.viewmodel.ToolCallStep
import coredevices.ring.ui.viewmodel.toolCallLabel
import coredevices.ring.ui.viewmodel.toolCallsLabel

/**
 * Collapsed summary of an assistant turn's intermediate tool calls. The header shimmers while
 * [active]; tapping the caret lists each call.
 */
@Composable
fun ToolCallGroup(
    steps: List<ToolCallStep>,
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    if (steps.isEmpty()) return
    val colors = IndexTheme.colors
    var expanded by remember { mutableStateOf(false) }
    val caretRotation by animateFloatAsState(if (expanded) 0f else -90f, label = "caret")
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable { expanded = !expanded }
            .padding(horizontal = 4.dp, vertical = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.KeyboardArrowDown,
                contentDescription = if (expanded) "Hide tool calls" else "Show tool calls",
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(16.dp).rotate(caretRotation),
            )
            Spacer(Modifier.width(4.dp))
            val label = toolCallsLabel(steps, active)
            if (active) {
                ShimmerText(
                    label,
                    baseColor = colors.onSurfaceVariant,
                    highlightColor = colors.onSurface,
                    fontWeight = FontWeight.Medium,
                )
            } else {
                Text(label, color = colors.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
        }
        AnimatedVisibility(expanded) {
            Column(
                modifier = Modifier.padding(start = 20.dp, top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                steps.forEach { step ->
                    val label = toolCallLabel(step)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (step.done) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                tint = colors.onSurfaceVariant,
                                modifier = Modifier.size(12.dp),
                            )
                        } else {
                            Spacer(Modifier.size(12.dp))
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (step.done) label.done else label.active,
                            color = colors.onSurfaceVariant,
                            fontSize = 12.5.sp,
                        )
                    }
                }
            }
        }
    }
}
