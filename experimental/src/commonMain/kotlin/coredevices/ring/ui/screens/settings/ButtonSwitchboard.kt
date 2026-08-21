package coredevices.ring.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SettingsEthernet
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Webhook
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coredevices.indexai.data.entity.mcp_sandbox.McpSandboxGroupEntity
import coredevices.ring.external.indexwebhook.IndexWebhookConfig
import coredevices.ring.service.button.GestureDestination
import coredevices.ring.service.button.GestureKind
import coredevices.ring.service.button.RingGesture
import coredevices.ring.ui.theme.IndexTheme

private val DOT = 9.dp
private val TILE_WIDTH = 168.dp
private val LABEL_WIDTH = 106.dp
private val TILE_SHAPE = RoundedCornerShape(10.dp)

val RingGesture.gestureLabel: String
    get() = when (this) {
        RingGesture.Click -> "Click"
        RingGesture.DoubleClick -> "Double click"
        RingGesture.TripleClick -> "Triple click"
        RingGesture.Hold -> "Hold & Talk"
        RingGesture.ClickHold -> "Double click & hold"
    }

/** Press pattern drawn as one rounded segment per press: [DOT] wide is a click, wider is a hold. */
val RingGesture.glyph: List<Dp>
    get() = when (this) {
        RingGesture.Click -> listOf(DOT)
        RingGesture.DoubleClick -> listOf(DOT, DOT)
        RingGesture.TripleClick -> listOf(DOT, DOT, DOT)
        RingGesture.Hold -> listOf(38.dp)
        RingGesture.ClickHold -> listOf(DOT, 26.dp)
    }

fun destinationsFor(kind: GestureKind, hasSandboxGroups: Boolean): List<GestureDestination> = when (kind) {
    GestureKind.Music -> listOf(
        GestureDestination.PlayPause,
        GestureDestination.NextTrack,
        GestureDestination.Nothing,
    )
    GestureKind.Recording -> buildList {
        add(GestureDestination.IndexAgent)
        add(GestureDestination.WebSearch)
        add(GestureDestination.WebhookOnly)
        if (hasSandboxGroups) add(GestureDestination.McpSandbox(null))
        add(GestureDestination.Nothing)
    }
}

/** True while the sheet still shows something to configure below the choice — the sandbox
 *  group list, or the webhook row. Otherwise picking a destination is the whole interaction
 *  and the sheet closes itself. Mirrors what [GestureDestinationSheet] renders. */
internal fun gestureSheetStaysOpenFor(
    gesture: RingGesture,
    destination: GestureDestination,
): Boolean = destination is GestureDestination.McpSandbox ||
    (gesture.kind == GestureKind.Recording && destination != GestureDestination.Nothing)

val GestureDestination.tileLabel: String
    get() = when (this) {
        GestureDestination.PlayPause -> "Play/Pause"
        GestureDestination.NextTrack -> "Next track"
        GestureDestination.IndexAgent -> "Index agent"
        GestureDestination.WebSearch -> "Web search"
        GestureDestination.WebhookOnly -> "Webhook only"
        is GestureDestination.McpSandbox -> "MCP sandbox"
        GestureDestination.Nothing -> "Nothing"
    }

private val GestureDestination.optionLabel: String
    get() = when (this) {
        GestureDestination.PlayPause -> "Play or pause music"
        GestureDestination.NextTrack -> "Skip to the next track"
        else -> tileLabel
    }

private val GestureDestination.optionSub: String?
    get() = when (this) {
        GestureDestination.IndexAgent -> "Transcribes and takes actions for you"
        GestureDestination.WebSearch -> "Answer lands in your feed"
        GestureDestination.WebhookOnly -> "Raw recording to your endpoint, nothing else"
        is GestureDestination.McpSandbox -> "Runs a chosen sandbox group's model and tools"
        else -> null
    }

private val GestureDestination.icon: ImageVector
    get() = when (this) {
        GestureDestination.PlayPause -> Icons.Default.PlayArrow
        GestureDestination.NextTrack -> Icons.Default.SkipNext
        GestureDestination.IndexAgent -> Icons.Default.GraphicEq
        GestureDestination.WebSearch -> Icons.Default.Search
        GestureDestination.WebhookOnly -> Icons.Default.Webhook
        is GestureDestination.McpSandbox -> Icons.Default.Extension
        GestureDestination.Nothing -> Icons.Default.Block
    }

/** Sandbox routes differ by group id, so option identity ignores it. */
private fun GestureDestination.isSameChoiceAs(other: GestureDestination): Boolean =
    if (this is GestureDestination.McpSandbox && other is GestureDestination.McpSandbox) true
    else this == other

@Composable
fun ButtonSwitchboard(
    routes: Map<RingGesture, GestureDestination>,
    sandboxGroups: List<McpSandboxGroupEntity>,
    musicEnabled: Boolean,
    webhookConfigFor: (RingGesture) -> IndexWebhookConfig,
    onSetRoute: (RingGesture, GestureDestination) -> Unit,
    onOpenWebhookSettings: (RingGesture) -> Unit,
    onDisableWebhook: (RingGesture) -> Unit,
) {
    var sheetGesture by remember { mutableStateOf<RingGesture?>(null) }
    val webhookCopies = { gesture: RingGesture -> webhookConfigFor(gesture).isActive }

    Column(
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RingGesture.entries.forEach { gesture ->
            val destination = routes[gesture] ?: GestureDestination.Nothing
            val enabled = musicEnabled || gesture.kind == GestureKind.Recording
            Column(modifier = Modifier.alpha(if (enabled) 1f else 0.38f)) {
                GestureRow(
                    gesture = gesture,
                    destination = destination,
                    enabled = enabled,
                    onEdit = { sheetGesture = gesture },
                )
                if (gesture.kind == GestureKind.Recording &&
                    destination != GestureDestination.WebhookOnly &&
                    destination != GestureDestination.Nothing &&
                    webhookCopies(gesture)
                ) {
                    WebhookCopyRow(onClick = { onOpenWebhookSettings(gesture) })
                }
            }
        }
    }

    sheetGesture?.let { gesture ->
        GestureDestinationSheet(
            gesture = gesture,
            current = routes[gesture] ?: GestureDestination.Nothing,
            sandboxGroups = sandboxGroups,
            webhookUrl = webhookConfigFor(gesture).takeIf { it.isActive }?.url,
            webhookCopyOn = webhookCopies(gesture),
            onSelect = {
                onSetRoute(gesture, it)
                if (!gestureSheetStaysOpenFor(gesture, it)) sheetGesture = null
            },
            onOpenWebhookSettings = {
                sheetGesture = null
                onOpenWebhookSettings(gesture)
            },
            onSetWebhookCopy = { on ->
                if (on) {
                    sheetGesture = null
                    onOpenWebhookSettings(gesture)
                } else {
                    onDisableWebhook(gesture)
                }
            },
            onDismiss = { sheetGesture = null },
        )
    }
}

@Composable
private fun GestureRow(
    gesture: RingGesture,
    destination: GestureDestination,
    enabled: Boolean,
    onEdit: () -> Unit,
) {
    val recording = gesture.kind == GestureKind.Recording
    val isIndexAgent = destination == GestureDestination.IndexAgent
    val colors = IndexTheme.colors
    val accent = colors.primary
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.width(LABEL_WIDTH),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            GestureGlyph(
                gesture = gesture,
                color = if (recording) accent else colors.onSurface,
            )
            Text(
                gesture.gestureLabel,
                fontSize = 12.sp,
                // The design's 12px label fits the 106dp column only without the
                // 0.5sp letter spacing bodyLarge would otherwise contribute.
                letterSpacing = 0.sp,
                color = colors.onSurfaceVariant,
            )
        }
        // The connector pairs with the tile rather than the whole row, so it meets the tile's
        // centre; the glyph column is taller and would otherwise pull the midpoint upwards.
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(2.dp)
                    .background(if (recording) accent else colors.outlineVariant)
            )
            Row(
                modifier = Modifier
                    .width(TILE_WIDTH)
                    .clip(TILE_SHAPE)
                    .background(if (isIndexAgent) colors.redSurface else Color.Transparent)
                    .border(
                        width = if (isIndexAgent) 1.5.dp else 1.dp,
                        color = if (isIndexAgent) accent else colors.outlineVariant,
                        shape = TILE_SHAPE,
                    )
                    .clickable(enabled = enabled, onClick = onEdit)
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    destination.icon,
                    contentDescription = null,
                    tint = if (isIndexAgent) accent else colors.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    destination.tileLabel,
                    fontSize = 13.sp,
                    fontWeight = if (isIndexAgent) FontWeight.Medium else FontWeight.Normal,
                    color = colors.onSurface,
                )
            }
        }
    }
}

@Composable
private fun GestureGlyph(gesture: RingGesture, color: Color, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        gesture.glyph.forEach { segment ->
            Box(
                modifier = Modifier
                    .size(width = segment, height = 9.dp)
                    .background(color, if (segment == DOT) CircleShape else RoundedCornerShape(5.dp))
            )
        }
    }
}

@Composable
private fun WebhookCopyRow(onClick: () -> Unit) {
    val colors = IndexTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.width(LABEL_WIDTH), contentAlignment = Alignment.CenterEnd) {
            Text(
                "also",
                fontSize = 11.sp,
                color = colors.outline,
                modifier = Modifier.padding(end = 8.dp),
            )
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .height(2.dp)
                .dashedLine(colors.outlineVariant)
        )
        Row(
            modifier = Modifier
                .width(TILE_WIDTH)
                .clip(TILE_SHAPE)
                .dashedBorder(colors.outline, 10.dp)
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                Icons.Default.Webhook,
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
            Text(
                "send to webhook",
                fontSize = 12.sp,
                color = colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun GestureDestinationSheet(
    gesture: RingGesture,
    current: GestureDestination,
    sandboxGroups: List<McpSandboxGroupEntity>,
    webhookUrl: String?,
    webhookCopyOn: Boolean,
    onSelect: (GestureDestination) -> Unit,
    onOpenWebhookSettings: () -> Unit,
    onSetWebhookCopy: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = IndexTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        containerColor = colors.sheetSurface,
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            GestureGlyph(
                gesture = gesture,
                color = if (gesture.kind == GestureKind.Recording) colors.primary
                else colors.onSurface,
            )
            Text(
                gesture.gestureLabel,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = colors.onSurface,
            )
        }
        destinationsFor(gesture.kind, sandboxGroups.isNotEmpty()).forEach { option ->
            val selected = option.isSameChoiceAs(current)
            DestinationOption(
                option = option,
                selected = selected,
                onClick = {
                    onSelect(
                        if (option is GestureDestination.McpSandbox) {
                            current as? GestureDestination.McpSandbox
                                ?: GestureDestination.McpSandbox(sandboxGroups.firstOrNull()?.id)
                        } else {
                            option
                        }
                    )
                },
            )
            if (selected && option is GestureDestination.McpSandbox) {
                sandboxGroups.forEach { group ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(GestureDestination.McpSandbox(group.id)) }
                            .padding(start = 48.dp, end = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = (current as? GestureDestination.McpSandbox)?.groupId == group.id,
                            onClick = { onSelect(GestureDestination.McpSandbox(group.id)) },
                        )
                        Text(group.title, fontSize = 15.sp)
                    }
                }
            }
        }
        if (gesture.kind == GestureKind.Recording &&
            current != GestureDestination.WebhookOnly &&
            current != GestureDestination.Nothing
        ) {
            HorizontalDivider(
                color = colors.outlineVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            )
            WebhookSheetRow(
                icon = Icons.Default.Webhook,
                title = "Also send to webhook",
                webhookUrl = webhookUrl,
                onClick = onOpenWebhookSettings,
            ) {
                Switch(checked = webhookCopyOn, onCheckedChange = onSetWebhookCopy)
            }
        }
        if (gesture.kind == GestureKind.Recording &&
            (current == GestureDestination.WebhookOnly || webhookCopyOn)
        ) {
            WebhookSheetRow(
                icon = Icons.Default.SettingsEthernet,
                title = "Webhook settings",
                webhookUrl = webhookUrl,
                onClick = onOpenWebhookSettings,
            ) {
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun DestinationOption(
    option: GestureDestination,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = IndexTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) colors.redSurface else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(
            option.icon,
            contentDescription = null,
            tint = if (selected) colors.primary
            else colors.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(option.optionLabel, fontSize = 15.sp, color = colors.onSurface)
            option.optionSub?.let {
                Text(it, fontSize = 12.sp, color = colors.onSurfaceVariant)
            }
        }
        if (selected) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun WebhookSheetRow(
    icon: ImageVector,
    title: String,
    webhookUrl: String?,
    onClick: () -> Unit,
    trailing: @Composable () -> Unit,
) {
    val colors = IndexTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = colors.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, color = colors.onSurface)
            Text(
                webhookUrl?.takeIf { it.isNotBlank() } ?: "Not configured yet. Tap to set up",
                fontSize = 12.sp,
                color = if (webhookUrl.isNullOrBlank()) colors.primary
                else colors.onSurfaceVariant,
            )
        }
        trailing()
    }
}

private fun dashes(density: androidx.compose.ui.unit.Density) = with(density) {
    PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))
}

private fun Modifier.dashedLine(color: Color) = drawBehind {
    drawLine(
        color = color,
        start = Offset(0f, size.height / 2f),
        end = Offset(size.width, size.height / 2f),
        strokeWidth = 2.dp.toPx(),
        pathEffect = dashes(this),
    )
}

private fun Modifier.dashedBorder(color: Color, radius: Dp) = drawBehind {
    drawRoundRect(
        color = color,
        cornerRadius = CornerRadius(radius.toPx()),
        style = Stroke(width = 1.dp.toPx(), pathEffect = dashes(this)),
    )
}
