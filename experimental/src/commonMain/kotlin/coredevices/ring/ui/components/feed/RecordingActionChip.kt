package coredevices.ring.ui.components.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Power
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coredevices.indexai.data.entity.ItemDocument.ItemMetadata
import coredevices.mcp.data.SemanticResult
import coredevices.ring.ui.openSystemClockApp
import coredevices.ring.ui.theme.IndexTheme
import coredevices.ring.ui.viewmodel.RecordingAction
import coredevices.ring.util.openSystemCalendarAt

/**
 * Chip for one tool result. Tapping opens what the action produced; when it produced
 * nothing openable, [onNothingToOpen] runs instead (null leaves the chip inert).
 */
@Composable
fun RecordingActionChip(
    action: RecordingAction,
    onOpenObject: (String) -> Unit,
    onNothingToOpen: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val onClick: (() -> Unit)? = when {
        action.canOpen -> ({ action.open(onOpenObject) })
        onNothingToOpen != null -> onNothingToOpen
        else -> null
    }
    ActionChip(
        glyph = action.glyph,
        label = action.label.take(64),
        subtext = action.source,
        subtextGlyph = Icons.Default.Power.takeIf { action.source != null },
        onClick = onClick,
        modifier = modifier,
    )
}

val RecordingAction.canOpen: Boolean
    get() = itemId != null || result is SemanticResult.CalendarEventCreation

/** Alarms, timers and calendar events belong to system apps, so those deep link first. */
fun RecordingAction.open(onOpenObject: (String) -> Unit) {
    val opened = when (val r = result) {
        is SemanticResult.AlarmCreation -> openSystemClockApp(ItemMetadata.Scheduled.FireKind.Alarm)
        is SemanticResult.TimerCreation -> openSystemClockApp(ItemMetadata.Scheduled.FireKind.Timer)
        is SemanticResult.CalendarEventCreation -> { openSystemCalendarAt(r.startTime); true }
        else -> false
    }
    if (!opened) itemId?.let(onOpenObject)
}

/**
 * Pill-shaped action chip. [subtext] is drawn in the chip's bottom padding, suffixed by
 * [subtextGlyph], without moving the centred label. [onClick] null = non-interactive.
 */
@Composable
fun ActionChip(
    glyph: ImageVector,
    label: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    subtext: String? = null,
    subtextGlyph: ImageVector? = null,
) {
    val colors = IndexTheme.colors
    val glyphSize = with(LocalDensity.current) { 12.sp.toDp() }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(colors.redSurface)
            .border(1.dp, colors.chipOutline, RoundedCornerShape(percent = 50))
            .let { if (onClick != null) it.clickable(onClick = onClick) else it },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 11.dp),
        ) {
            Icon(
                glyph,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(glyphSize),
            )
            Spacer(Modifier.width(5.dp))
            Text(
                label,
                color = colors.onPrimaryContainer,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (subtext != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 10.dp + glyphSize + 5.dp, end = 10.dp, bottom = 5.dp),
            ) {
                Text(
                    subtext,
                    color = colors.onSurfaceVariant,
                    fontSize = 9.sp,
                    lineHeight = 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtextGlyph != null) {
                    Spacer(Modifier.width(3.dp))
                    with(LocalDensity.current) {
                        Icon(
                            subtextGlyph,
                            contentDescription = null,
                            tint = colors.onSurfaceVariant,
                            modifier = Modifier.size(9.sp.toDp()),
                        )
                    }
                }
            }
        }
    }
}
