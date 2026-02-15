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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coredevices.ui.PebbleElevatedButton
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
                    .clickable { selectedColor = color }
            )
        }
    }
    selectedColor?.let { color ->
        Spacer(modifier = Modifier.size(2.dp))
        Box(modifier = Modifier.padding(8.dp)) {
            Box(
                modifier = Modifier.fillMaxWidth()
                    .background(color.color, shape = RoundedCornerShape(8.dp))
                    .clickable {
                        onColorSelected(color.timelineColor)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = color.timelineColor.displayName,
                    modifier = Modifier.padding(16.dp),
                    color = color.textColor,
                    fontSize = 22.sp,
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
