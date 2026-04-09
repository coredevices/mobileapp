package coredevices.ring.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemColors
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

@Composable
fun RowScope.GlowNavigationBarItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: @Composable (() -> Unit)? = null,
    alwaysShowLabel: Boolean = true,
    colors: NavigationBarItemColors = NavigationBarItemDefaults.colors(),
    interactionSource: MutableInteractionSource? = null
) {
    val primaryColor = MaterialTheme.colorScheme.primary

    NavigationBarItem(
        selected,
        onClick,
        icon,
        if (selected) {
            modifier.drawWithCache {
                val size = this.size
                val brush = Brush.radialGradient(
                    0.0f to primaryColor.copy(alpha = 0.2f),
                    1.0f to primaryColor.copy(alpha = 0.0f),
                    center = Offset(size.width / 2, 0f),
                    radius = size.height
                )
                onDrawBehind {
                    drawRect(brush = brush)
                }
            }
        } else {
            modifier
        },
        enabled,
        label,
        alwaysShowLabel,
        colors.copy(
            selectedIndicatorColor = Color.Transparent,
            selectedIconColor = primaryColor,
            selectedTextColor = primaryColor
        ),
        interactionSource
    )
}