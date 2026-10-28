package coredevices.pebble.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import coredevices.pebble.plugin.ComplicationSlot
import coredevices.pebble.plugin.PreferencesPlugin
import coredevices.pebble.rememberLibPebble
import coredevices.pebble.ui.PluginCapabilities.Companion.humanize
import io.rebble.libpebblecommon.plugin.BooleanShape
import io.rebble.libpebblecommon.plugin.IconPixelSize
import io.rebble.libpebblecommon.plugin.IconShape
import io.rebble.libpebblecommon.plugin.ImageShape
import io.rebble.libpebblecommon.plugin.LongTextShape
import io.rebble.libpebblecommon.plugin.NumericValueShape
import io.rebble.libpebblecommon.plugin.PluginCaller
import io.rebble.libpebblecommon.plugin.PluginMetadata
import io.rebble.libpebblecommon.plugin.ShortTextShape
import io.rebble.libpebblecommon.plugin.SourceInstance
import io.rebble.libpebblecommon.plugin.SourceShapeNames
import io.rebble.libpebblecommon.plugin.SourceUpdate
import io.rebble.libpebblecommon.plugin.TimestampShape
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import org.koin.compose.koinInject
import kotlin.time.Instant
import kotlin.io.encoding.Base64

private data class SourceRef(val category: String, val item: String) {
    val label get() = "${humanize(category)}: ${humanize(item).lowercase()}"
}

private fun PluginMetadata.sourceRefs() = sources.flatMap { source -> source.items.map { SourceRef(source.category, it) } }

/** Every `category/item` some plugin serves, with the plugins serving it. */
private fun List<PluginMetadata>.byServedSource(): Map<SourceRef, List<PluginMetadata>> =
    flatMap { plugin -> plugin.sourceRefs().map { it to plugin } }
        .groupBy({ it.first }, { it.second })
        .entries.sortedWith(compareBy({ it.key.category }, { it.key.item }))
        .associate { it.key to it.value }

private fun ordinal(n: Int) = when {
    n % 100 in 11..13 -> "${n}th"
    n % 10 == 1 -> "${n}st"
    n % 10 == 2 -> "${n}nd"
    n % 10 == 3 -> "${n}rd"
    else -> "${n}th"
}

@Composable
fun ComplicationsScreen(nav: NavBarNav, topBarParams: TopBarParams) {
    LaunchedEffect(Unit) {
        topBarParams.searchAvailable(null)
        topBarParams.actions {}
        topBarParams.title("Complications")
    }
    val preferences: PreferencesPlugin = koinInject()
    val slots by preferences.complications.collectAsState()
    var editing by remember { mutableStateOf<Int?>(null) }
    editing?.let { position ->
        ComplicationEditor(
            slot = slots[position],
            onSave = {
                preferences.setComplication(position, it)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Text(
                "Choose which plugin entries you would like watchfaces to display in complication slots, in order",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        itemsIndexed(slots) { position, slot ->
            ListItem(
                leadingContent = { Text("${position + 1}", style = MaterialTheme.typography.titleMedium) },
                headlineContent = { Text(slot?.let { humanize(it.property) } ?: "Empty") },
                supportingContent = slot?.let {
                    { Text("${SourceRef(it.category, it.item).label} · ${ordinal(it.index + 1)}") }
                },
                modifier = Modifier.clickable { editing = position },
            )
        }
    }
}

/** Live readings from the plugin that answers [source], for previewing values. */
@Composable
private fun rememberReadings(source: SourceRef?): List<SourceInstance> {
    val libPebble = rememberLibPebble()
    val readings by remember(source) {
        val plugin = source?.let { libPebble.sourcePlugin(it.category, it.item) }
        if (source == null || plugin == null) {
            flowOf(source to emptyList())
        } else {
            libPebble.observe(PluginCaller.Host(probe = true), plugin, source.category, source.item, iconPixelSize = PREVIEW_ICON_SIZE)
                .filterIsInstance<SourceUpdate.Data>()
                .map { source to it.envelope.instances }
        }
    }.collectAsState(source to emptyList())
    // The previous source's readings stay until the new one's first arrive.
    return if (readings.first == source) readings.second else emptyList()
}

@Composable
private fun ComplicationEditor(slot: ComplicationSlot?, onSave: (ComplicationSlot?) -> Unit, onDismiss: () -> Unit) {
    val libPebble = rememberLibPebble()
    val served = remember {
        libPebble.getAllPlugins().byServedSource().keys.filter { it.category != PreferencesPlugin.CATEGORY }
    }
    var source by remember { mutableStateOf(slot?.let { SourceRef(it.category, it.item) }) }
    var index by remember { mutableStateOf(slot?.index ?: 0) }
    var property by remember { mutableStateOf(slot?.property) }
    val declaration = remember(source) {
        source?.let { ref ->
            libPebble.sourcePlugin(ref.category, ref.item)?.sources?.firstOrNull { it.serves(ref.category, ref.item) }
        }
    }
    val properties = declaration?.properties?.keys?.toList().orEmpty()
    val chosenProperty = property ?: properties.firstOrNull()
    val readings = rememberReadings(source)
    val shapes = chosenProperty?.let { readings.getOrNull(index)?.properties?.get(it) }.orEmpty()

    fun choose(ref: SourceRef?) {
        source = ref
        index = 0
        property = null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Complication") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                Picker(
                    label = "Category",
                    options = listOf<String?>(null) + served.map { it.category }.distinct(),
                    selected = source?.category,
                    text = { it?.let(::humanize) ?: "Nothing" },
                    onSelect = { category -> choose(served.firstOrNull { it.category == category }) },
                )
                Picker(
                    label = "Item",
                    options = served.filter { it.category == source?.category }.ifEmpty { listOf(null) },
                    selected = source,
                    text = { it?.let { ref -> humanize(ref.item) } ?: "—" },
                    onSelect = ::choose,
                    enabled = source != null,
                )
                Picker(
                    label = "Which",
                    options = readings.indices.toList().ifEmpty { listOf(index) },
                    selected = index,
                    text = { ordinal(it + 1) },
                    onSelect = { index = it },
                    enabled = source != null,
                )
                Picker(
                    label = "Reading",
                    options = properties.ifEmpty { listOf(null) },
                    selected = chosenProperty,
                    text = { it?.let(::humanize) ?: "—" },
                    onSelect = { property = it },
                    enabled = source != null,
                )
                if (shapes.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Now", style = MaterialTheme.typography.labelLarge)
                        shapes.forEach { (name, value) ->
                            val bitmap = when (name) {
                                SourceShapeNames.ICON -> Json.decodeFromJsonElement<IconShape>(value)
                                    .let { PaletteBitmap(it.pixels, it.palette, it.width, it.height) }
                                SourceShapeNames.IMAGE -> Json.decodeFromJsonElement<ImageShape>(value)
                                    .let { PaletteBitmap(it.pixels, it.palette, it.width, it.height) }
                                else -> null
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    if (bitmap == null) "$name: ${shapeSummary(name, value)}" else "$name: ",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                bitmap?.let { PaletteBitmapView(it) }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(source?.let { ref -> chosenProperty?.let { ComplicationSlot(ref.category, ref.item, index, it) } })
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private val PREVIEW_ICON_SIZE = IconPixelSize(24, 24)

/** An `icon` or `image` shape: 4-bpp indices, two per byte with even x in the high nibble, into GColor8s. */
private class PaletteBitmap(pixels: String, palette: String, val width: Int, val height: Int) {
    private val indices = Base64.decode(pixels)
    private val colors = Base64.decode(palette).map { gColor8(it.toInt() and 0xFF) }

    fun colorAt(x: Int, y: Int): Color {
        val byte = indices.getOrNull(y * ((width + 1) / 2) + x / 2)?.toInt() ?: return Color.Transparent
        val index = if (x % 2 == 0) (byte shr 4) and 0x0F else byte and 0x0F
        return colors.getOrNull(index) ?: Color.Transparent
    }

    private fun gColor8(c: Int): Color {
        fun channel(shift: Int) = ((c shr shift) and 3) * 85
        return Color(channel(4), channel(2), channel(0), channel(6))
    }
}

@Composable
private fun PaletteBitmapView(bitmap: PaletteBitmap) {
    Canvas(Modifier.size((bitmap.width * 2).dp, (bitmap.height * 2).dp)) {
        val cell = Size(size.width / bitmap.width, size.height / bitmap.height)
        for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
            drawRect(bitmap.colorAt(x, y), Offset(x * cell.width, y * cell.height), cell)
        }
    }
}

private fun shapeSummary(name: String, value: JsonElement): String = when (name) {
    SourceShapeNames.SHORT_TEXT -> Json.decodeFromJsonElement<ShortTextShape>(value).text
    SourceShapeNames.LONG_TEXT -> Json.decodeFromJsonElement<LongTextShape>(value).text
    SourceShapeNames.NUMERIC_VALUE -> Json.decodeFromJsonElement<NumericValueShape>(value).let { n ->
        val range = if (n.min != null && n.max != null) " (${n.min}–${n.max})" else ""
        "${n.value}${n.unit.orEmpty()}$range"
    }
    SourceShapeNames.BOOLEAN -> Json.decodeFromJsonElement<BooleanShape>(value).value.toString()
    SourceShapeNames.TIMESTAMP -> Instant.fromEpochSeconds(Json.decodeFromJsonElement<TimestampShape>(value).value).toString()
    else -> value.toString().let { if (it.length > 60) it.take(60) + "…" else it }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> Picker(
    label: String,
    options: List<T>,
    selected: T,
    text: (T) -> String,
    onSelect: (T) -> Unit,
    enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = enabled && it }) {
        OutlinedTextField(
            value = text(selected),
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(text(option)) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
fun PreferredSourcesScreen(nav: NavBarNav, topBarParams: TopBarParams) {
    LaunchedEffect(Unit) {
        topBarParams.searchAvailable(null)
        topBarParams.actions {}
        topBarParams.title("Preferred sources")
    }
    val libPebble = rememberLibPebble()
    val preferred by libPebble.preferredSourcePlugins.collectAsState()
    val shared = remember { sharedSources(libPebble.getAllPlugins()) }
    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        item {
            Text(
                "When more than one plugin offers the same thing, apps that don't ask for a particular one get the plugin chosen here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        items(shared.entries.toList(), key = { (ref, _) -> "${ref.category}/${ref.item}" }) { (ref, plugins) ->
            val chosen = plugins.firstOrNull { it.pluginUuid == preferred["${ref.category}/${ref.item}"] }
            var expanded by remember { mutableStateOf(false) }
            ListItem(
                headlineContent = { Text(ref.label) },
                trailingContent = {
                    Box {
                        TextButton(onClick = { expanded = true }) {
                            // With no choice made, the plugin that answers is the automatic one.
                            Text(chosen?.name ?: "Automatic (${remember(preferred) { libPebble.sourcePlugin(ref.category, ref.item)?.name }})")
                        }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            DropdownMenuItem(
                                text = { Text("Automatic") },
                                onClick = {
                                    libPebble.setPreferredSourcePlugin(ref.category, ref.item, null)
                                    expanded = false
                                },
                            )
                            plugins.forEach { plugin ->
                                DropdownMenuItem(
                                    text = { Text(plugin.name) },
                                    onClick = {
                                        libPebble.setPreferredSourcePlugin(ref.category, ref.item, plugin.pluginUuid)
                                        expanded = false
                                    },
                                )
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Sources more than one installed plugin serves: the only ones with a choice to make. */
private fun sharedSources(plugins: List<PluginMetadata>) = plugins.byServedSource().filterValues { it.size > 1 }

fun hasSharedSources(plugins: List<PluginMetadata>) = sharedSources(plugins).isNotEmpty()
