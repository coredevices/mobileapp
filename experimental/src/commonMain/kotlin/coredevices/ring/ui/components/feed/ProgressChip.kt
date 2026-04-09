package coredevices.ring.ui.components.feed

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
fun ProgressChip(
    text: String,
    progress: Float,
    modifier: Modifier = Modifier,
    base: Color = MaterialTheme.colorScheme.surface,
    background: Color = MaterialTheme.colorScheme.inversePrimary.copy(alpha = 0.4f),
    fillColor: Color = MaterialTheme.colorScheme.inversePrimary,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    spinnerColor: Color = MaterialTheme.colorScheme.onSurface,
    borderColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 300, easing = LinearEasing),
    )
    val shape = RoundedCornerShape(percent = 100)
    Box(
        modifier = modifier
            .clip(shape)
            .drawBehind {
                drawRect(base)
                drawRect(background)
                drawRect(
                    color = fillColor,
                    size = size.copy(width = size.width * animatedProgress),
                )
            }
            .border(width = 1.dp, color = borderColor, shape = shape),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(17.dp),
                color = spinnerColor,
                strokeWidth = 2.2.dp,
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = contentColor,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
    }
}

@Preview
@Composable
fun ProgressChipPreviewLight() {
    MaterialTheme(lightColorScheme(), typography = MaterialTheme.typography, shapes = MaterialTheme.shapes) {
        Surface(Modifier.background(MaterialTheme.colorScheme.background)) {
            ProgressChip(
                text = "Doing...",
                progress = 0.6f,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}

@Preview
@Composable
fun ProgressChipPreviewDark() {
    MaterialTheme(darkColorScheme(), typography = MaterialTheme.typography, shapes = MaterialTheme.shapes) {
        Surface(Modifier.background(MaterialTheme.colorScheme.background)) {
            ProgressChip(
                text = "Doing...",
                progress = 0.6f,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}