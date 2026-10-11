package coredevices.ring.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/** Text with a highlight band sweeping across it, for "still working" labels. */
@Composable
fun ShimmerText(
    text: String,
    baseColor: Color,
    highlightColor: Color,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 13.sp,
    fontWeight: FontWeight? = null,
) {
    var textWidth by remember { mutableIntStateOf(0) }
    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmer-progress",
    )
    val band = (textWidth * 0.6f).coerceAtLeast(1f)
    val start = -band + (textWidth + 2 * band) * progress
    Text(
        text,
        style = TextStyle(
            brush = Brush.linearGradient(
                colors = listOf(baseColor, highlightColor, baseColor),
                start = Offset(start, 0f),
                end = Offset(start + band, 0f),
                tileMode = TileMode.Clamp,
            ),
            fontSize = fontSize,
            fontWeight = fontWeight,
        ),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { textWidth = it.size.width },
        modifier = modifier,
    )
}
