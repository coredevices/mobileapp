package coredevices.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ElevatedAssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.russhwolf.settings.Settings
import com.russhwolf.settings.set
import org.koin.compose.koinInject

@Composable
fun PebbleElevatedButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    contentDescription: String? = null,
    primaryColor: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = when {
        primaryColor -> AssistChipDefaults.elevatedAssistChipColors(
            containerColor = MaterialTheme.colorScheme.primary,
            labelColor = MaterialTheme.colorScheme.onPrimary,
            leadingIconContentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
//            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )
        else -> AssistChipDefaults.elevatedAssistChipColors(
            containerColor = MaterialTheme.colorScheme.background,
            disabledContainerColor = MaterialTheme.colorScheme.background,
        )
    }
    val borderColor = when {
        enabled -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    }
    val border = when {
        primaryColor -> null
        else -> BorderStroke(0.5.dp, borderColor)
    }
    ElevatedAssistChip(
        label = {
            Text(
                text = text,
                modifier = Modifier.padding(top = 8.dp, bottom = 8.dp, start = 0.dp, end = 4.dp),
            )
        },
        onClick = onClick,
        enabled = enabled,
        leadingIcon = {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    modifier = Modifier.height(30.dp),
                )
            }
        },
        elevation = AssistChipDefaults.elevatedAssistChipElevation(
            elevation = 5.dp,
            disabledElevation = 5.dp,
            pressedElevation = 5.dp,
        ),
        modifier = modifier,
        colors = colors,
        border = border,
    )
}

@Composable
fun CoreLinearProgressIndicator(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.tertiaryContainer,
) {
    LinearProgressIndicator(
        progress = progress,
        modifier = modifier,
        color = color,
        trackColor = trackColor,
    )
}

/**
 * Tooltip which is shown the first time the screen is displayed, then only when long pressed after
 * that (uses a setting with [settingsKey] to remember that the screen has already been shown).
 *
 * To avoid the same tooltip being shown multiple times inside a list, set [firstOrOnlyItem]
 * appropriately.
 */
@Composable
fun ShowOnceTooltipBox(
    settingsKey: String,
    persistent: Boolean,
    firstOrOnlyItem: Boolean,
    text: String,
    content: @Composable () -> Unit
) {
    val settings: Settings = koinInject()
    val shownTooltipBefore = remember { settings.getBoolean(settingsKey, false) }
    var tooltipInitiallyVisible by remember { mutableStateOf(!shownTooltipBefore) }

    LaunchedEffect(firstOrOnlyItem) {
        if (firstOrOnlyItem) {
            settings[settingsKey] = true
        }
    }

    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = {
            PlainTooltip {
                Text(
                    text = text,
                    modifier =   Modifier.padding(5.dp),
                )
            }
        },
        state = rememberTooltipState(
            isPersistent = persistent,
            initialIsVisible = firstOrOnlyItem && tooltipInitiallyVisible,
        ),
    ) {
        content()
    }
}