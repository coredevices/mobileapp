package coredevices.ring.ui.components.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coredevices.ring.ui.PreviewWrapper
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
fun ResponseBubble(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(8.dp),
    maxHeight: Dp = 70.dp,
    leading: @Composable RowScope.() -> Unit = {},
    content: @Composable RowScope.() -> Unit
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Box(modifier = Modifier.padding(contentPadding).heightIn(max = maxHeight)) {
            CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.bodyMedium) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    leading()
                    content()
                }
            }
        }
    }
}

@Preview
@Composable
fun ResponseBubblePreview() {
    PreviewWrapper {
        Box(modifier = Modifier.background(MaterialTheme.colorScheme.background)) {
            ResponseBubble(Modifier.padding(16.dp)) {
                Text("Hello, this is a response bubble!")
            }
        }
    }
}

@Preview
@Composable
fun IconResponseBubblePreview() {
    PreviewWrapper {
        Box(modifier = Modifier.background(MaterialTheme.colorScheme.background)) {
            ResponseBubble(
                modifier = Modifier.padding(16.dp),
                leading = { Icon(Icons.Outlined.Search, null, Modifier.size(12.dp)) }
            ) {
                Text(
                    "On Monday, March 24, 2025, Nashville is expected to experience sunny " +
                            "weather with a high of 63°F (17°C) and a low of 44°F (6°C). Winds will " +
                            "be light and variable, making it a pleasant day to be outdoors.",
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}