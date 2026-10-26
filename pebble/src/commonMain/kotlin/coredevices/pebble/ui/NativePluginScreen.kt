package coredevices.pebble.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coredevices.pebble.rememberLibPebble
import coredevices.pebble.weather.WeatherPlugin
import coredevices.ui.PebbleElevatedButton
import io.rebble.libpebblecommon.locker.AppType
import io.rebble.libpebblecommon.plugin.CalendarPlugin
import io.rebble.libpebblecommon.plugin.MusicPlugin
import io.rebble.libpebblecommon.plugin.PhoneStatePlugin
import io.rebble.libpebblecommon.plugin.PlatformPlugins
import io.rebble.libpebblecommon.plugin.PluginMetadata
import io.rebble.libpebblecommon.plugin.WatchSettingsPlugin
import io.rebble.libpebblecommon.plugin.WatchStatePlugin
import kotlin.uuid.Uuid

// Built-in plugins have no pbw, so the UI maps each to an icon rather than reading one from a bundle.
fun PluginMetadata.builtInIcon(): ImageVector = when (pluginUuid) {
    MusicPlugin.BUILT_IN_MUSIC_UUID -> Icons.Filled.MusicNote
    CalendarPlugin.BUILT_IN_CALENDAR_UUID -> Icons.Filled.CalendarMonth
    WeatherPlugin.BUILT_IN_WEATHER_UUID -> Icons.Filled.Cloud
    PhoneStatePlugin.BUILT_IN_PHONE_STATE_UUID -> Icons.Filled.Smartphone
    WatchSettingsPlugin.BUILT_IN_WATCH_SETTINGS_UUID -> Icons.Filled.Watch
    WatchStatePlugin.BUILT_IN_WATCH_STATE_UUID -> Icons.Filled.Apps
    PlatformPlugins.BUILT_IN_NOTIFICATIONS_UUID -> Icons.Filled.Notifications
    else -> AppType.Plugin.icon()
}

fun PluginMetadata.settingsRoute(): NavBarRoute? = when (pluginUuid) {
    WeatherPlugin.BUILT_IN_WEATHER_UUID -> PebbleNavBarRoutes.WeatherRoute
    CalendarPlugin.BUILT_IN_CALENDAR_UUID -> PebbleNavBarRoutes.CalendarsRoute
    else -> null
}

private const val BUILT_IN_DEVELOPER_NAME = "Built-in"

private fun NavBarNav.openNativePlugin(plugin: PluginMetadata) =
    navigateTo(PebbleNavBarRoutes.NativePluginRoute(plugin.pluginUuid.toString()))

/** Built-in plugins for display, disabled ones last. */
@Composable
fun rememberNativePlugins(): List<PluginMetadata> {
    val libPebble = rememberLibPebble()
    val plugins by remember { libPebble.nativePlugins() }.collectAsState(emptyList())
    val disabled by libPebble.disabledBuiltInPlugins.collectAsState()
    return remember(plugins, disabled) { plugins.sortedBy { it.pluginUuid in disabled } }
}

@Composable
private fun PluginMetadata.isDisabled(): Boolean {
    val disabled by rememberLibPebble().disabledBuiltInPlugins.collectAsState()
    return pluginUuid in disabled
}

private fun Modifier.greyedOut(disabled: Boolean) = if (disabled) alpha(0.4f) else this

/** Stands in for a pbw plugin's square icon, at the same size and shape. */
@Composable
private fun BuiltInPluginImage(plugin: PluginMetadata, cornerRadius: Dp, modifier: Modifier) {
    BoxWithConstraints(
        modifier = modifier.aspectRatio(1f)
            .clip(RoundedCornerShape(cornerRadius))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Icon(plugin.builtInIcon(), contentDescription = null, modifier = Modifier.size(maxWidth * 0.55f))
    }
}

@Composable
fun NativePluginCard(plugin: PluginMetadata, navBarNav: NavBarNav) {
    Card(
        modifier = Modifier.padding(3.dp).width(100.dp)
            .greyedOut(plugin.isDisabled())
            .clickable { navBarNav.openNativePlugin(plugin) }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(6.dp).height(NATIVE_SCREENSHOT_HEIGHT),
                contentAlignment = Alignment.Center,
            ) {
                BuiltInPluginImage(plugin, cornerRadius = 9.dp, modifier = Modifier.fillMaxWidth())
            }
            Text(
                plugin.name,
                fontSize = 12.sp,
                lineHeight = 12.sp,
                maxLines = 1,
                fontWeight = FontWeight.Bold,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 5.dp, end = 5.dp).heightIn(min = 18.dp),
            )
            Text(
                BUILT_IN_DEVELOPER_NAME,
                color = Color.Gray,
                fontSize = 10.sp,
                lineHeight = 10.sp,
                maxLines = 1,
                modifier = Modifier.padding(start = 5.dp, end = 5.dp, bottom = 7.dp),
            )
        }
    }
}

@Composable
fun NativePluginListItem(plugin: PluginMetadata, navBarNav: NavBarNav) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .greyedOut(plugin.isDisabled())
            .clickable { navBarNav.openNativePlugin(plugin) }
            .padding(vertical = 8.dp, horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.width(62.dp).height(74.dp), contentAlignment = Alignment.Center) {
            BuiltInPluginImage(plugin, cornerRadius = 8.dp, modifier = Modifier.fillMaxWidth())
        }
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(plugin.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1)
            Text(
                plugin.description,
                color = Color.Gray,
                fontSize = 11.sp,
                maxLines = 3,
                lineHeight = 11.sp,
            )
        }
    }
}

@Composable
fun NativePluginScreen(navBarNav: NavBarNav, topBarParams: TopBarParams, uuid: Uuid) {
    val libPebble = rememberLibPebble()
    val plugins by remember { libPebble.nativePlugins() }.collectAsState(null)
    val plugin = plugins?.firstOrNull { it.pluginUuid == uuid }
    LaunchedEffect(plugin?.name) {
        topBarParams.searchAvailable(null)
        topBarParams.actions {}
        topBarParams.title(plugin?.name ?: "")
    }
    if (plugin == null) return
    val settingsRoute = plugin.settingsRoute()
    val disabled = plugin.isDisabled()
    Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 5.dp)) {
        Row {
            BuiltInPluginImage(
                plugin,
                cornerRadius = 12.dp,
                modifier = Modifier.padding(10.dp).size(140.dp).greyedOut(disabled),
            )
            Column(modifier = Modifier.padding(horizontal = 5.dp, vertical = 10.dp)) {
                Text(plugin.name, fontSize = 20.sp)
                Text(
                    if (disabled) "$BUILT_IN_DEVELOPER_NAME · Disabled" else BUILT_IN_DEVELOPER_NAME,
                    fontSize = 12.sp,
                    color = Color.Gray,
                )
                PebbleElevatedButton(
                    text = "Settings",
                    onClick = { settingsRoute?.let { navBarNav.navigateTo(it) } },
                    enabled = settingsRoute != null,
                    icon = Icons.Default.Settings,
                    contentDescription = "Settings",
                    primaryColor = false,
                    modifier = Modifier.padding(5.dp),
                )
                PebbleElevatedButton(
                    text = if (disabled) "Enable" else "Disable",
                    onClick = { libPebble.setBuiltInPluginEnabled(plugin.pluginUuid, disabled) },
                    icon = if (disabled) Icons.Default.PlayArrow else Icons.Default.Block,
                    contentDescription = if (disabled) "Enable" else "Disable",
                    primaryColor = false,
                    modifier = Modifier.padding(5.dp),
                )
            }
        }
        Text(plugin.description, modifier = Modifier.padding(10.dp))
        PluginCapabilitiesSection(plugin)
    }
}
