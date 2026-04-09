package coredevices.ring.ui.components.feed

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun ContextChip(icon: @Composable () -> Unit, text: String, background: Color, onBackground: Color) {
    /*AssistChip(
        onClick = {},
        leadingIcon = icon,
        label = { Text(text) },
        shape = RoundedCornerShape(percent = 100),
        border = BorderStroke(0.dp, Color.Transparent),
        colors = AssistChipDefaults.assistChipColors().copy(
            containerColor = background,
        )
    )*/
    Surface(
        color = background,
        contentColor = onBackground,
        shape = RoundedCornerShape(percent = 100)
    ) {
        Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            Box(modifier = Modifier.align(Alignment.CenterVertically).padding(end = 4.dp)) {
                icon()
            }
            Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.align(Alignment.CenterVertically))
        }
    }
}