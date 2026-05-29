package coredevices.ring.ui.components.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coredevices.ring.ui.theme.IndexTheme

/**
 * Single source of truth for the round "tick when done" checkbox used in
 * todo / reminder rows across the index-feed UI.
 *
 * Sizes are 15% smaller than the original (May 8 design tweak):
 * - [Size.Small] (was 22.dp → now 19.dp) — used in compact rows: home Todos
 *   carousel and the children rows on object-list detail screens.
 * - [Size.Large] (was 28.dp → now 24.dp) — used on the hero row of an object's
 *   detail page where the row text is larger.
 *
 * Edit one of [SmallDiameter] / [LargeDiameter] to scale every consumer at once.
 */
@Composable
fun TodoCheckCircle(
    done: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    size: Size = Size.Small,
) {
    val colors = IndexTheme.colors
    val (diameter, iconSize) = when (size) {
        Size.Small -> SmallDiameter to SmallIcon
        Size.Large -> LargeDiameter to LargeIcon
    }
    Box(
        modifier = modifier
            .size(diameter)
            .clip(CircleShape)
            .border(2.dp, if (done) colors.onSurfaceVariant else colors.primary, CircleShape)
            .background(if (done) colors.surfaceContainerHigh else Color.Transparent)
            .clickable { onToggle() },
        contentAlignment = Alignment.Center,
    ) {
        if (done) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(iconSize),
            )
        }
    }
}

enum class Size { Small, Large }

// 15% smaller than the original 22dp / 28dp / 12dp / 14dp values.
private val SmallDiameter: Dp = 19.dp
private val LargeDiameter: Dp = 24.dp
private val SmallIcon: Dp = 10.dp
private val LargeIcon: Dp = 12.dp
