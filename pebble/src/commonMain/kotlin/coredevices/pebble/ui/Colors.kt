package coredevices.pebble.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Arrangement.Center
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coredevices.ui.PebbleElevatedButton
import io.rebble.libpebblecommon.database.entity.RgbColorPreset
import io.rebble.libpebblecommon.timeline.TimelineColor
import io.rebble.libpebblecommon.timeline.argbColor
import org.jetbrains.compose.ui.tooling.preview.Preview

enum class ColorTab(val icon: ImageVector) {
    Grid(Icons.Default.GridOn),
    List(Icons.AutoMirrored.Filled.ListAlt),
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun ColorPickerDialog(
    onColorSelected: (TimelineColor?) -> Unit,
    onDismissWithoutResult: () -> Unit,
    selectedColorName: String?,
    availableColors: List<TimelineColor>? = null,
    defaultToListTab: Boolean = false,
) {
    val sortedColors = remember {
        TimelineColor.entries.filter {
            availableColors == null || availableColors.contains(it)
        }.map { it.toHsvColor() }
            .sortedWith(
                compareBy(
                    {
                        it.hue
                    },
                    {
                        it.saturation
                    },
                    {
                        it.value
                    },
                )
            )
    }
    val selectedHsvColor = remember(selectedColorName) {
        sortedColors.find { it.timelineColor.name == selectedColorName }
    }
    val defaultTab = if (defaultToListTab) ColorTab.List else ColorTab.Grid
    val selectedTab = remember { mutableStateOf(defaultTab) }
    Dialog(
        onDismissRequest = { onDismissWithoutResult() }
    ) {
        Card(modifier = Modifier.padding(15.dp)) {
            Column {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier.padding(vertical = 4.dp, horizontal = 8.dp),
                    ) {
                        ColorTab.entries.forEachIndexed { index, tab ->
                            SegmentedButton(
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = ColorTab.entries.size,
                                ),
                                onClick = { selectedTab.value = tab },
                                selected = selectedTab.value == tab,
                                icon = { },
                                label = { Icon(tab.icon, contentDescription = tab.name) },
                            )
                        }
                    }
                }
                when (selectedTab.value) {
                    ColorTab.List -> ColorList(selectedHsvColor, sortedColors, onColorSelected)
                    ColorTab.Grid -> ColorGrid(selectedHsvColor, sortedColors, onColorSelected)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Center,
                ) {
                    TextButton(
                        onClick = {
                            onDismissWithoutResult()
                        },
                        content = {
                            Text("Cancel")
                        },
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = {
                            onColorSelected(null)
                        },
                        content = {
                            Text("None")
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.ColorGrid(
    selectedColor: HsvColor?,
    sortedColors: List<HsvColor>,
    onColorSelected: (TimelineColor?) -> Unit,
) {
    var selectedColor by remember { mutableStateOf<HsvColor?>(selectedColor) }
    LazyVerticalGrid(
        columns = GridCells.Fixed(8),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.padding(8.dp).weight(1f, fill = false),
    ) {
        items(sortedColors) { color ->
            val borderColor = MaterialTheme.colorScheme.onSurface
            val tooltipState = remember { TooltipState(isPersistent = false) }
            TooltipBox(
                positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                tooltip = {
                    PlainTooltip {
                        Text(color.timelineColor.displayName)
                    }
                },
                state = tooltipState
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(color.color)
                        .border(
                            2.dp,
                            if (color == selectedColor) borderColor else Color.Transparent,
                            RoundedCornerShape(4.dp)
                        )
                        .clickable { onColorSelected(color.timelineColor) }
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.ColorList(
    selectedColor: HsvColor?,
    sortedColors: List<HsvColor>,
    onColorSelected: (TimelineColor) -> Unit,
) {
    val borderColor = MaterialTheme.colorScheme.onSurface
    LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
        items(sortedColors) { color ->
            ListItem(
                headlineContent = {
                    Text(color.timelineColor.displayName, color = color.textColor)
                },
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        onColorSelected(color.timelineColor)
                    }
                    .border(
                        2.dp,
                        if (color == selectedColor) borderColor else Color.Transparent,
                        RoundedCornerShape(4.dp)
                    ),
                colors = ListItemDefaults.colors(
                    containerColor = color.color
                )
            )
        }
    }
}

@Composable
fun SelectColorOrNone(
    currentColorName: String?,
    onChangeColor: (TimelineColor?) -> Unit,
    availableColors: List<TimelineColor>? = null,
    defaultToListTab: Boolean = false,
) {
    var showColorChooser by remember { mutableStateOf(false) }
    if (showColorChooser) {
        ColorPickerDialog(
            onColorSelected = { pattern ->
                onChangeColor(pattern)
                showColorChooser = false
            },
            onDismissWithoutResult = {
                showColorChooser = false
            },
            selectedColorName = currentColorName,
            availableColors = availableColors,
            defaultToListTab = defaultToListTab,
        )
    }
    ListItem(
        headlineContent = {
            Text("Color")
        },
        supportingContent = {
            val surfaceColor = MaterialTheme.colorScheme.surface
            val onSurfaceColor = MaterialTheme.colorScheme.onSurface
            val color = remember(currentColorName) { TimelineColor.findByName(currentColorName) }
            val bgColor = remember(color, surfaceColor) {
                color?.argbColor()?.let { Color(it) } ?: surfaceColor
            }
            val textColor =
                remember(color, onSurfaceColor) { color?.toHsvColor()?.textColor ?: onSurfaceColor }
            Box(modifier = Modifier.padding(4.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(bgColor, shape = RoundedCornerShape(8.dp))
                ) {
                    Text(
                        text = color?.displayName ?: "Default",
                        color = textColor,
                        modifier = Modifier.padding(vertical = 6.dp, horizontal = 8.dp),
                    )
                }
            }
        },
        trailingContent = {
            PebbleElevatedButton(
                text = "Select",
                onClick = {
                    showColorChooser = true
                },
                icon = Icons.Default.ColorLens,
                contentDescription = "Select color",
                primaryColor = true,
                modifier = Modifier.padding(8.dp),
            )
        },
    )
}

@Preview
@Composable
fun ColorPickerPreview() {
    ColorPickerDialog(onColorSelected = {}, onDismissWithoutResult = {}, null)
}

private const val PRESETS_PER_ROW = 4

@Composable
fun RgbColorPickerDialog(
    initialRgb: UInt,
    defaultRgb: UInt,
    presets: List<RgbColorPreset>,
    onColorSelected: (UInt) -> Unit,
    onDismissWithoutResult: () -> Unit,
) {
    var red by remember { mutableStateOf(((initialRgb shr 16) and 0xFFu).toInt()) }
    var green by remember { mutableStateOf(((initialRgb shr 8) and 0xFFu).toInt()) }
    var blue by remember { mutableStateOf((initialRgb and 0xFFu).toInt()) }
    var hexInput by remember {
        mutableStateOf(initialRgb.and(0x00FFFFFFu).toString(16).padStart(6, '0').uppercase())
    }
    val rgb = ((red shl 16) or (green shl 8) or blue).toUInt() and 0x00FFFFFFu
    val previewColor = Color(0xFF000000u.toInt() or rgb.toInt())

    fun setRgb(value: UInt) {
        red = ((value shr 16) and 0xFFu).toInt()
        green = ((value shr 8) and 0xFFu).toInt()
        blue = (value and 0xFFu).toInt()
        hexInput = value.and(0x00FFFFFFu).toString(16).padStart(6, '0').uppercase()
    }

    fun applyHex(text: String) {
        val sanitized = text.removePrefix("#").trim()
        sanitized.toUIntOrNull(16)?.takeIf { it <= 0xFFFFFFu }?.let { parsed ->
            red = ((parsed shr 16) and 0xFFu).toInt()
            green = ((parsed shr 8) and 0xFFu).toInt()
            blue = (parsed and 0xFFu).toInt()
        }
    }

    val focusManager = LocalFocusManager.current
    Dialog(
        onDismissRequest = onDismissWithoutResult,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier.fillMaxSize().imePadding(),
            contentAlignment = Alignment.Center,
        ) {
            Card(modifier = Modifier.padding(15.dp)) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(previewColor)
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "#" + rgb.toString(16).padStart(6, '0').uppercase(),
                            color = if (previewColor.luminance() > 0.55f) Color.Black else Color.White,
                        )
                    }
                    if (presets.isNotEmpty()) {
                        Text("Presets", modifier = Modifier.padding(bottom = 4.dp))
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            presets.chunked(PRESETS_PER_ROW).forEach { rowPresets ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    rowPresets.forEach { preset ->
                                        PresetChip(
                                            preset = preset,
                                            isSelected = preset.rgb == rgb,
                                            onClick = { setRgb(preset.rgb) },
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                    // Pad the last row so chips stay aligned with rows above.
                                    repeat(PRESETS_PER_ROW - rowPresets.size) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                    ChannelSlider("R", red, Color.Red) {
                        red = it
                        hexInput = rgb.toString(16).padStart(6, '0').uppercase()
                    }
                    ChannelSlider("G", green, Color.Green) {
                        green = it
                        hexInput = rgb.toString(16).padStart(6, '0').uppercase()
                    }
                    ChannelSlider("B", blue, Color.Blue) {
                        blue = it
                        hexInput = rgb.toString(16).padStart(6, '0').uppercase()
                    }
                    OutlinedTextField(
                        value = hexInput,
                        onValueChange = {
                            hexInput = it.uppercase().take(6)
                            applyHex(hexInput)
                        },
                        label = { Text("Hex") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    )
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        TextButton(
                            onClick = onDismissWithoutResult,
                            modifier = Modifier.weight(1f),
                        ) { Text("Cancel") }
                        TextButton(
                            onClick = { setRgb(defaultRgb) },
                            modifier = Modifier.weight(1f),
                            enabled = rgb != (defaultRgb and 0x00FFFFFFu),
                        ) { Text("Reset") }
                        TextButton(
                            onClick = { onColorSelected(rgb) },
                            modifier = Modifier.weight(1f),
                        ) { Text("OK") }
                    }
                }
            }
        }
    }
}

@Composable
private fun PresetChip(
    preset: RgbColorPreset,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val color = Color(0xFF000000u.toInt() or preset.rgb.toInt())
    val tooltipState = remember { TooltipState(isPersistent = false) }
    Box(modifier = modifier) {
        TooltipBox(
            positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
            tooltip = { PlainTooltip { Text(preset.displayName) } },
            state = tooltipState,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(color)
                    .border(
                        2.dp,
                        if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                        RoundedCornerShape(6.dp),
                    )
                    .clickable(onClick = onClick),
            )
        }
    }
}

@Composable
private fun ChannelSlider(
    label: String,
    value: Int,
    accent: Color,
    onValueChange: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.padding(end = 8.dp))
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt().coerceIn(0, 255)) },
            valueRange = 0f..255f,
            colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent),
            modifier = Modifier.weight(1f),
        )
        Text(
            value.toString(),
            modifier = Modifier.padding(start = 8.dp).width(32.dp),
        )
    }
}

@Composable
fun SelectRgbColor(
    currentRgb: UInt,
    defaultRgb: UInt,
    presets: List<RgbColorPreset>,
    onChangeColor: (UInt) -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }
    if (showPicker) {
        RgbColorPickerDialog(
            initialRgb = currentRgb,
            defaultRgb = defaultRgb,
            presets = presets,
            onColorSelected = {
                onChangeColor(it)
                showPicker = false
            },
            onDismissWithoutResult = { showPicker = false },
        )
    }
    val swatchColor = Color(0xFF000000u.toInt() or (currentRgb and 0x00FFFFFFu).toInt())
    val matchedPreset = presets.firstOrNull { it.rgb == currentRgb }
    ListItem(
        headlineContent = { Text("Color") },
        supportingContent = {
            Box(modifier = Modifier.padding(4.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(swatchColor, shape = RoundedCornerShape(8.dp))
                ) {
                    Text(
                        text = matchedPreset?.displayName ?: ("#" + currentRgb.toString(16).padStart(6, '0').uppercase()),
                        color = if (swatchColor.luminance() > 0.55f) Color.Black else Color.White,
                        modifier = Modifier.padding(vertical = 6.dp, horizontal = 8.dp),
                    )
                }
            }
        },
        trailingContent = {
            PebbleElevatedButton(
                text = "Select",
                onClick = { showPicker = true },
                icon = Icons.Default.ColorLens,
                contentDescription = "Select color",
                primaryColor = true,
                modifier = Modifier.padding(8.dp),
            )
        },
    )
}

data class HsvColor(
    val hue: Float,
    val saturation: Float,
    val value: Float,
    val color: Color,
    val timelineColor: TimelineColor,
    val textColor: Color,
)

fun TimelineColor.toHsvColor(): HsvColor {
    val color = Color(this.argbColor())
    val r = color.red / 255f
    val g = color.green / 255f
    val b = color.blue / 255f

    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val delta = max - min

    val hue = when {
        delta == 0f -> 0f // achromatic (grey)
        max == r -> 60 * (((g - b) / delta) % 6)
        max == g -> 60 * (((b - r) / delta) + 2)
        max == b -> 60 * (((r - g) / delta) + 4)
        else -> 0f
    }.let { if (it < 0) it + 360 else it } / 360f // Normalize to [0, 1]

    val saturation = if (max == 0f) 0f else delta / max
    val value = max

    val luminance = (0.299 * color.red + 0.587 * color.green + 0.114 * color.blue)
    val textColor = if (luminance > 0.55) Color.Black else Color.White

    return HsvColor(hue, saturation, value, color, this, textColor)
}
