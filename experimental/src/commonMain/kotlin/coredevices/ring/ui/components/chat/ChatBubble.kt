package coredevices.ring.ui.components.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coredevices.ring.ui.PreviewWrapper
import org.jetbrains.compose.ui.tooling.preview.Preview
import theme.ExtendedTheme

@Composable
fun ChatBubble(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(vertical = 8.dp, horizontal = 16.dp),
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.inversePrimary,
        contentColor = MaterialTheme.colorScheme.contentColorFor(MaterialTheme.colorScheme.inversePrimary),
        shape = MaterialTheme.shapes.large
    ) {
        Box(modifier = Modifier.padding(contentPadding)) {
            content()
        }
    }
}

@Preview
@Composable
fun ChatBubblePreview() {
    PreviewWrapper {
        ChatBubble {
            Text("Hello, this is a chat bubble!")
        }
    }
}
