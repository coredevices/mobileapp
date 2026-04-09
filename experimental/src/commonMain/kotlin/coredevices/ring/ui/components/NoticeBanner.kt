package coredevices.ring.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import theme.ExtendedTheme

@Composable
fun NoticeBanner(
    icon: @Composable () -> Unit,
    text: String,
    detailText: String? = null,
    background: Color = ExtendedTheme.colors.warning,
    contentColor: Color = ExtendedTheme.colors.onWarning
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = background,
        contentColor = contentColor
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                Box(modifier = Modifier.align(Alignment.CenterVertically).padding(end = 4.dp)) {
                    icon()
                }
                Text(
                    text,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.align(Alignment.CenterVertically))
            }
            detailText?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}