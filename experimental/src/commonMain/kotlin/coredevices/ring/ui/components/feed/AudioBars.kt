package coredevices.ring.ui.components.feed

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.jetbrains.compose.ui.tooling.preview.Preview
import kotlin.random.Random

@Composable
fun AudioBars(
    modifier: Modifier = Modifier,
    barCount: Int = 16,
    maxBarHeight: Dp = 22.dp,
    barWidth: Dp = 6.dp,
    randomSeed: Int? = null
) {
    val random = remember(randomSeed) { randomSeed?.let { Random(randomSeed) } ?: Random }
    val baseHeight = remember { maxBarHeight.value / 3 }
    Row(
        modifier = modifier.height(maxBarHeight),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(barCount) { i ->
            val randomHeight = remember {
                val modifier = random.nextFloat() * (maxBarHeight.value - baseHeight)
                (baseHeight + modifier).dp
            }
            Box(
                modifier = Modifier
                    .width(barWidth)
                    .height(randomHeight)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            )
            if (i < barCount - 1) Spacer(modifier = Modifier.width(1.dp))
        }
    }
}

@Composable
fun AnimatedAudioBars(
    modifier: Modifier = Modifier,
    barCount: Int = 16,
    maxBarHeight: Dp = 22.dp,
    barWidth: Dp = 6.dp,
    intervalMs: Long = 650,
) {
    val baseHeight = remember { maxBarHeight.value / 3 }

    fun heightsForSeed(seed: Int): List<Float> {
        val random = Random(seed)
        return List(barCount) { baseHeight + random.nextFloat() * (maxBarHeight.value - baseHeight) }
    }

    val animatables = remember {
        heightsForSeed(0).map { Animatable(it) }
    }

    LaunchedEffect(Unit) {
        var seed = 1
        while (isActive) {
            delay(intervalMs)
            val targets = heightsForSeed(seed++)
            animatables.forEachIndexed { i, anim ->
                launch { anim.animateTo(targets[i], animationSpec = tween(durationMillis = (intervalMs * 0.75).toInt())) }
            }
        }
    }

    Row(
        modifier = modifier.height(maxBarHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        animatables.forEachIndexed { i, anim ->
            Box(
                modifier = Modifier
                    .width(barWidth)
                    .height(anim.value.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            )
            if (i < barCount - 1) Spacer(modifier = Modifier.width(1.dp))
        }
    }
}

@Preview
@Composable
fun AudioBarsPreview() {
    Box(modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
        Box(modifier = Modifier.padding(16.dp)) {
            AudioBars(randomSeed = 12)
        }
    }
}
