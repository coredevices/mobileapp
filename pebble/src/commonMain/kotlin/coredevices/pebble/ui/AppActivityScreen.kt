package coredevices.pebble.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coredevices.pebble.rememberLibPebble
import coredevices.util.relativeTime
import io.rebble.libpebblecommon.PLUGIN_ACTION_LOG_KEPT_DAYS
import io.rebble.libpebblecommon.database.dao.WatchPreference
import io.rebble.libpebblecommon.database.entity.PluginActionLogEntity
import io.rebble.libpebblecommon.database.entity.QuicklaunchWatchPref
import io.rebble.libpebblecommon.plugin.ActionDeclaration.Companion.PARAM_INSTANCE_ID
import io.rebble.libpebblecommon.plugin.PluginMetadata
import io.rebble.libpebblecommon.plugin.WatchSettingsPlugin
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.koin.compose.koinInject
import kotlin.time.Instant
import kotlin.uuid.Uuid

private const val PREVIEW_ACTIONS = 15

private enum class ActivityGrouping(val label: String) { ByApp("By app"), ByPlugin("By plugin") }

@Composable
fun AppActivityScreen(nav: NavBarNav, topBarParams: TopBarParams) {
    LaunchedEffect(Unit) {
        topBarParams.searchAvailable(null)
        topBarParams.actions {}
        topBarParams.title("App activity")
    }
    val libPebble = rememberLibPebble()
    val log by remember { libPebble.pluginActionLog() }.collectAsState(null)
    val sharedViewModel: SharedLockerViewModel = koinInject()
    sharedViewModel.Init()
    val describer = rememberActionDescriber()
    var grouping by rememberSaveable { mutableStateOf(ActivityGrouping.ByApp) }
    val icons = rememberActionLogIcons(log.orEmpty().flatMap { listOf(it.appUuid, it.pluginUuid) }.toSet())
    val entries = log ?: return
    val groups = remember(entries, grouping) {
        entries.groupBy { if (grouping == ActivityGrouping.ByApp) it.appUuid else it.pluginUuid }.toList()
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Text(
                if (entries.isEmpty()) {
                    "None of your apps have taken actions using plugins in the last $PLUGIN_ACTION_LOG_KEPT_DAYS days."
                } else {
                    "What actions your apps took using plugins over the last $PLUGIN_ACTION_LOG_KEPT_DAYS days."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        if (entries.isNotEmpty()) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(horizontal = 16.dp)) {
                    ActivityGrouping.entries.forEach {
                        FilterChip(selected = grouping == it, onClick = { grouping = it }, label = { Text(it.label) })
                    }
                }
            }
        }
        items(groups, key = { "${grouping.name}/${it.first}" }) { (uuid, groupEntries) ->
            val byApp = grouping == ActivityGrouping.ByApp
            val title = if (byApp) describer.appName(uuid) else describer.pluginName(uuid)
            ActivityGroupRow(
                uuid = uuid,
                title = title,
                entries = groupEntries,
                describer = describer,
                showCaller = !byApp,
                icons = icons,
                nav = nav,
                showAll = {
                    nav.navigateTo(
                        if (byApp) PebbleNavBarRoutes.ActionLogRoute(callerUuid = uuid.toString(), pluginUuid = null)
                        else PebbleNavBarRoutes.ActionLogRoute(callerUuid = null, pluginUuid = uuid.toString())
                    )
                },
            )
        }
    }
}

@Composable
private fun ActivityGroupRow(
    uuid: Uuid,
    title: String,
    entries: List<PluginActionLogEntity>,
    describer: ActionDescriber,
    showCaller: Boolean,
    icons: ActionLogIcons,
    nav: NavBarNav,
    showAll: () -> Unit,
) {
    var expanded by rememberSaveable(uuid.toString(), showCaller) { mutableStateOf(false) }
    Column(modifier = Modifier.clickable { expanded = !expanded }) {
        ListItem(
            leadingContent = { AppOrPluginIcon(uuid, 56.dp, icons, nav) },
            headlineContent = { Text(title, fontWeight = FontWeight.Bold) },
            supportingContent = {
                Text("${entries.size} actions · last ${relativeTime(Instant.fromEpochMilliseconds(entries.first().atMs))}")
            },
            trailingContent = {
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Hide actions" else "Show actions",
                )
            },
        )
        if (expanded) {
            Card(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 8.dp)) {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    ActionLogPreview(entries, describer, showCaller, icons, nav, showAll)
                }
            }
        }
    }
}

@Composable
private fun ActionLogPreview(
    entries: List<PluginActionLogEntity>,
    describer: ActionDescriber,
    showCaller: Boolean,
    icons: ActionLogIcons,
    nav: NavBarNav,
    showAll: () -> Unit,
) {
    entries.take(PREVIEW_ACTIONS).forEach { ActionLogRow(it, describer, showCaller, icons, nav) }
    if (entries.size > PREVIEW_ACTIONS) {
        TextButton(onClick = showAll, modifier = Modifier.padding(horizontal = 4.dp)) {
            Text("Show all ${entries.size}")
        }
    }
}

/** Shown on an app's page; nothing if it hasn't run any actions. */
@Composable
fun AppActivitySection(appUuid: Uuid, nav: NavBarNav) {
    val libPebble = rememberLibPebble()
    val entries by remember(appUuid) { libPebble.pluginActionLog(callerUuid = appUuid) }.collectAsState(emptyList())
    val describer = rememberActionDescriber()
    val icons = rememberActionLogIcons(entries.map { it.pluginUuid }.toSet())
    if (entries.isEmpty()) return
    var open by rememberSaveable(appUuid.toString()) { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            SectionHeader("Recent activity", open) { open = !open }
            if (!open) {
                Text(
                    "${entries.size} actions in the last $PLUGIN_ACTION_LOG_KEPT_DAYS days",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (open) {
            ActionLogPreview(entries, describer, showCaller = false, icons, nav) {
                nav.navigateTo(PebbleNavBarRoutes.ActionLogRoute(callerUuid = appUuid.toString(), pluginUuid = null))
            }
        }
    }
}

/** Every logged action asked for by [callerUuid], or run by [pluginUuid]. */
@Composable
fun ActionLogScreen(nav: NavBarNav, topBarParams: TopBarParams, callerUuid: Uuid?, pluginUuid: Uuid?) {
    val libPebble = rememberLibPebble()
    val entries by remember(callerUuid, pluginUuid) { libPebble.pluginActionLog(pluginUuid, callerUuid) }
        .collectAsState(emptyList())
    koinInject<SharedLockerViewModel>().Init()
    val describer = rememberActionDescriber()
    val icons = rememberActionLogIcons(entries.map { if (callerUuid == null) it.appUuid else it.pluginUuid }.toSet())
    val title = callerUuid?.let { describer.appName(it) }
        ?: pluginUuid?.let { describer.pluginName(it) }
        ?: "App activity"
    LaunchedEffect(title) {
        topBarParams.searchAvailable(null)
        topBarParams.actions {}
        topBarParams.title(title)
    }
    LazyColumn(modifier = Modifier.fillMaxSize().padding(vertical = 8.dp)) {
        items(entries, key = { it.id }) { ActionLogRow(it, describer, showCaller = callerUuid == null, icons, nav) }
    }
}

@Composable
private fun ActionLogRow(
    entry: PluginActionLogEntity,
    describer: ActionDescriber,
    showCaller: Boolean,
    icons: ActionLogIcons,
    nav: NavBarNav,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        // The other side of the action: what the row's group or page isn't already about.
        AppOrPluginIcon(if (showCaller) entry.appUuid else entry.pluginUuid, 36.dp, icons, nav)
        Column {
            Text(describer.describe(entry), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            val caller = if (showCaller) "${describer.appName(entry.appUuid)} · " else ""
            val error = entry.error?.let { " · Failed: $it" }.orEmpty()
            Text(
                caller + relativeTime(Instant.fromEpochMilliseconds(entry.atMs)) + error,
                style = MaterialTheme.typography.bodySmall,
                color = if (entry.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** What the icons on a screen show, looked up once for all its rows. [apps] is null while loading. */
private class ActionLogIcons(val builtIns: Map<Uuid, PluginMetadata>, val apps: Map<Uuid, CommonApp>?)

@Composable
private fun rememberActionLogIcons(uuids: Set<Uuid>): ActionLogIcons {
    val libPebble = rememberLibPebble()
    val builtIns by remember { libPebble.nativePlugins() }.collectAsState(emptyList())
    val watchType = koinInject<SharedLockerViewModel>().watchType.value
    val apps by remember(uuids) {
        if (uuids.isEmpty()) flowOf(emptyList()) else combine(uuids.map { libPebble.getLockerApp(it) }) { it.filterNotNull() }
    }.collectAsState(null)
    return remember(builtIns, apps, watchType) {
        // Only for icons, so without the store details a full load adds.
        ActionLogIcons(
            builtIns.associateBy { it.pluginUuid },
            apps?.associate { it.properties.id to it.asCommonApp(watchType, appstoreSource = null, categories = null) },
        )
    }
}

/** An installed app's or a built-in plugin's icon, opening its page. */
@Composable
private fun AppOrPluginIcon(uuid: Uuid, size: Dp, icons: ActionLogIcons, nav: NavBarNav) {
    val builtIn = icons.builtIns[uuid]
    val app = if (builtIn == null) icons.apps?.get(uuid) else null
    val open: (() -> Unit)? = when {
        builtIn != null -> { { nav.navigateTo(PebbleNavBarRoutes.NativePluginRoute(uuid.toString())) } }
        app != null -> { { nav.navigateTo(PebbleNavBarRoutes.LockerAppRoute(uuid = uuid.toString(), storedId = null, storeSource = null)) } }
        else -> null
    }
    Box(
        modifier = Modifier.size(size).clickable(enabled = open != null) { open?.invoke() },
        contentAlignment = Alignment.Center,
    ) {
        when {
            builtIn != null -> BuiltInPluginImage(builtIn, cornerRadius = size / 7, modifier = Modifier.size(size))
            app != null -> AppImage(app, Modifier.clip(RoundedCornerShape(size / 7)), size)
            icons.apps == null -> Unit
            else -> Icon(Icons.Default.Extension, contentDescription = null)
        }
    }
}

@Composable
private fun rememberActionDescriber(): ActionDescriber {
    val libPebble = rememberLibPebble()
    val prefs by libPebble.watchPrefs.collectAsState(emptyList())
    val titles by remember { libPebble.getAllLockerBasicInfo().map { apps -> apps.associate { it.id to it.title } } }
        .collectAsState(null)
    val builtIns by remember { libPebble.nativePlugins() }.collectAsState(emptyList())
    // Pbw plugins register as the locker loads, so the list is read again whenever it changes.
    return remember(prefs, titles, builtIns) {
        ActionDescriber((libPebble.getAllPlugins() + builtIns).distinctBy { it.pluginUuid }, prefs, titles)
    }
}

internal class ActionDescriber(
    private val plugins: List<PluginMetadata>,
    private val watchPrefs: List<WatchPreference<*>>,
    /** Null while loading. */
    private val appTitles: Map<Uuid, String>?,
) {
    /** Blank until the titles load. */
    fun appName(appUuid: Uuid): String = if (appTitles == null) "" else appTitles[appUuid] ?: "Removed app"

    fun pluginName(pluginUuid: Uuid): String = plugins.firstOrNull { it.pluginUuid == pluginUuid }?.name
        ?: if (appTitles == null) "" else appTitles[pluginUuid] ?: "Removed plugin"

    fun describe(entry: PluginActionLogEntity): String {
        if (entry.pluginUuid == WatchSettingsPlugin.BUILT_IN_WATCH_SETTINGS_UUID) {
            watchSetting(entry.action, entry.args)?.let { return it }
        }
        val plugin = plugins.firstOrNull { it.pluginUuid == entry.pluginUuid }
        val action = plugin?.action(entry.action)?.description?.takeIf { it.isNotBlank() } ?: entry.action
        val name = pluginName(entry.pluginUuid)
        return if (name.isEmpty()) action else "$name: $action"
    }

    private fun watchSetting(action: String, json: String): String? {
        val args = try {
            Json.parseToJsonElement(json).jsonObject
        } catch (e: Exception) {
            return null
        }
        val id = args[PARAM_INSTANCE_ID]?.jsonPrimitive?.contentOrNull ?: return null
        val pref = watchPrefs.firstOrNull { it.pref.id == id }?.pref
        val name = pref?.displayName ?: id
        return when (action) {
            WatchSettingsPlugin.ACTION_SET_ON -> args["on"]?.jsonPrimitive?.booleanOrNull?.let {
                "Turned watch setting $name ${if (it) "on" else "off"}"
            }
            WatchSettingsPlugin.ACTION_SET_VALUE -> args["value"]?.jsonPrimitive?.contentOrNull?.let { value ->
                val shown = if (pref is QuicklaunchWatchPref) quickLaunchApp(value) else value
                "Set watch setting $name to $shown"
            }
            else -> null
        }
    }

    private fun quickLaunchApp(value: String): String {
        if (value.isBlank()) return "nothing"
        val uuid = try {
            Uuid.parse(value)
        } catch (e: IllegalArgumentException) {
            return value
        }
        return appTitles?.get(uuid) ?: value
    }
}
