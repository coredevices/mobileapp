package coredevices.pebble.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coredevices.pebble.rememberLibPebble
import coredevices.util.relativeTime
import io.rebble.libpebblecommon.permissions.AppPermissionStatus
import io.rebble.libpebblecommon.permissions.PermissionStatus
import io.rebble.libpebblecommon.permissions.PluginDefinedPermission
import io.rebble.libpebblecommon.permissions.domainOf
import io.rebble.libpebblecommon.permissions.fileScopeOf
import io.rebble.libpebblecommon.permissions.packageOf
import io.rebble.libpebblecommon.permissions.permissionOf
import io.rebble.libpebblecommon.plugin.PluginPermissions
import kotlinx.coroutines.flow.map
import org.koin.compose.koinInject
import kotlin.time.Instant

/** The statuses of apps that can use each permission, by permission name (domains, packages and files folded in). */
internal fun holdersByPermission(all: List<AppPermissionStatus>?): Map<String, List<AppPermissionStatus>> =
    all.orEmpty()
        .filter { it.status == PermissionStatus.Granted || it.status == PermissionStatus.AllowedAutomatically }
        .groupBy { permissionOf(it.permission) }

/** What the domain, package or file versions of [name] add up to. */
private fun permissionGroupLabel(name: String, defined: Map<String, PluginDefinedPermission>) = when (name) {
    PluginPermissions.INTERNET -> "Connect to websites"
    PluginPermissions.NOTIFICATIONS -> "Read your notifications"
    PluginPermissions.PEBBLEKIT_2 -> "Exchange messages with phone apps"
    else -> permissionLabel(name, defined)
}

/** Which domain, package or file [key] is for; null for a permission that has none. */
private fun parameterLabel(key: String): String? =
    domainOf(key) ?: packageOf(key) ?: fileScopeOf(key)?.let(::fileScopeLabel) ?: when (key) {
        PluginPermissions.INTERNET -> "Any website"
        PluginPermissions.NOTIFICATIONS -> "All apps"
        else -> null
    }

@Composable
fun PermissionUsageScreen(nav: NavBarNav, topBarParams: TopBarParams) {
    LaunchedEffect(Unit) {
        topBarParams.searchAvailable(null)
        topBarParams.actions {}
        topBarParams.title("Permissions in use")
    }
    val libPebble = rememberLibPebble()
    val defined = rememberPluginPermissions()
    val holders by remember { libPebble.appPermissionStatuses(null).map(::holdersByPermission) }.collectAsState(null)
    val names = holders?.keys?.sortedBy { permissionGroupLabel(it, defined) }?.byKind(defined) ?: return
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        if (names.isEmpty()) {
            item {
                Text(
                    "No app can use any permissions yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
        items(names, key = { it }) { name ->
            val count = holders?.get(name).orEmpty().map { it.appUuid }.distinct().size
            Box(
                modifier = Modifier.fillMaxWidth()
                    .clickable { nav.navigateTo(PebbleNavBarRoutes.PermissionHoldersRoute(name)) }
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                PermissionRow(name, defined, if (count == 1) "1 app" else "$count apps", permissionGroupLabel(name, defined)) {}
            }
        }
    }
}

@Composable
fun PermissionHoldersScreen(nav: NavBarNav, topBarParams: TopBarParams, permission: String) {
    val libPebble = rememberLibPebble()
    val defined = rememberPluginPermissions()
    val title = permissionGroupLabel(permission, defined)
    LaunchedEffect(title) {
        topBarParams.searchAvailable(null)
        topBarParams.actions {}
        topBarParams.title(title)
    }
    // Each app's statuses under this permission, the most recently used app first.
    val holders by remember(permission) {
        libPebble.appPermissionStatuses(null).map { all ->
            holdersByPermission(all)[permission].orEmpty()
                .groupBy { it.appUuid }
                .entries.sortedByDescending { (_, statuses) -> statuses.maxOf { it.lastUsedAtMs ?: 0 } }
        }
    }.collectAsState(emptyList())
    val sharedViewModel: SharedLockerViewModel = koinInject()
    sharedViewModel.Init()
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(holders, key = { (appUuid, _) -> appUuid.toString() }) { (appUuid, statuses) ->
            val lastUsedAtMs = statuses.mapNotNull { it.lastUsedAtMs }.maxOrNull()
            val used = if (lastUsedAtMs == null) "Not used yet" else "Used ${relativeTime(Instant.fromEpochMilliseconds(lastUsedAtMs))}"
            val usage = if (statuses.all { it.status == PermissionStatus.AllowedAutomatically }) "Allowed automatically · $used" else used
            val parameters = statuses.mapNotNull { parameterLabel(it.permission) }.distinct()
            val entry = loadLockerEntry(appUuid, sharedViewModel.watchType.value)
            ListItem(
                leadingContent = {
                    Box(modifier = Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                        entry?.let { AppImage(it, Modifier.clip(RoundedCornerShape(8.dp)), 56.dp) }
                    }
                },
                headlineContent = { Text(entry?.title ?: appUuid.toString(), fontWeight = FontWeight.Bold) },
                supportingContent = {
                    Column {
                        if (parameters.isNotEmpty()) Text(parameters.joinToString())
                        Text(usage)
                    }
                },
                modifier = Modifier.clickable {
                    nav.navigateTo(PebbleNavBarRoutes.LockerAppRoute(uuid = appUuid.toString(), storedId = null, storeSource = null))
                },
            )
        }
    }
}
