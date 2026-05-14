package coredevices.libindex.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

enum class Press(val durationMs: Long, val mic: Boolean = false) {
    Short(140),
    Long(650),
    HoldAndSpeak(1600, true),
    Rest(200);

    companion object {
        val SOS = listOf(Short, Short, Short, Rest, Long, Long, Long, Rest, Short, Short, Short)
    }
}

@Composable
fun PressPatternDot(
    pattern: List<Press>,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    idleColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
    gapBetweenPressesMs: Long = 220,
    micOffsetMs: Long = 50,
    idleAfterPatternMs: Long = 2000,
) {
    var pressed by remember { mutableStateOf(false) }
    var micOn by remember { mutableStateOf(false) }

    LaunchedEffect(pattern, gapBetweenPressesMs, idleAfterPatternMs, micOffsetMs) {
        if (pattern.isEmpty()) return@LaunchedEffect
        while (true) {
            pattern.forEachIndexed { index, press ->
                if (press == Press.Rest) {
                    delay(press.durationMs)
                    return@forEachIndexed
                }
                pressed = true
                if (press.mic) {
                    delay(micOffsetMs)
                    micOn = true
                }
                delay(press.durationMs)
                pressed = false
                if (press.mic) {
                    micOn = false
                }
                if (index != pattern.lastIndex) {
                    delay(gapBetweenPressesMs)
                }
            }
            delay(idleAfterPatternMs)
        }
    }

    val color by animateColorAsState(if (pressed) activeColor else idleColor)
    val scale by animateFloatAsState(if (pressed) 1f else 0.8f)
    val micAlpha by animateFloatAsState(
        targetValue = if (micOn) 1f else 0f,
        animationSpec = tween(durationMillis = 200),
    )

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .scale(scale)
                .background(color = color, shape = CircleShape),
        )
        if (micAlpha > 0f) {
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = null,
                tint = Color.Red.copy(alpha = 0.9f),
                modifier = Modifier
                    .size(size * 0.6f)
                    .alpha(micAlpha),
            )
        }
    }
}
