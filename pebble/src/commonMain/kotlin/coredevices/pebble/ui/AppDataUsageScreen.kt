package coredevices.pebble.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coredevices.pebble.rememberLibPebble
import coredevices.util.storage.formatDataUsage
import io.rebble.libpebblecommon.permissions.AppDataUsageSummary
import io.rebble.libpebblecommon.permissions.PermissionDataUsage
import io.rebble.libpebblecommon.permissions.byApp
import io.rebble.libpebblecommon.permissions.domainOf
import io.rebble.libpebblecommon.plugin.PluginPermissions
import kotlinx.coroutines.flow.map
import org.koin.compose.koinInject

private const val USAGE_DAYS = 30

private const val LEGACY_INFO = "This app was made before apps had to declare which websites they use, so it can " +
    "reach any website and its data usage can't be split up by website."

private fun PermissionDataUsage.label(): String = domainOf(permission) ?: when (permission) {
    PluginPermissions.LOCAL_NETWORK -> "Local network"
    else -> "Any website"
}

@Composable
fun AppDataUsageScreen(topBarParams: TopBarParams) {
    LaunchedEffect(Unit) {
        topBarParams.searchAvailable(null)
        topBarParams.actions {}
        topBarParams.title("Data usage")
    }
    val libPebble = rememberLibPebble()
    val usage by remember { libPebble.appDataUsage(USAGE_DAYS).map { it.byApp() } }.collectAsState(null)
    val sharedViewModel: SharedLockerViewModel = koinInject()
    sharedViewModel.Init()
    val installed = allCollectionUuids()
    val apps = usage ?: return
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Text(
                if (apps.isEmpty()) {
                    "No watchapp or plugin has used the network in the last $USAGE_DAYS days."
                } else {
                    "What your watchapps and plugins sent and received over the last $USAGE_DAYS days."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        items(apps, key = { it.appUuid.toString() }) { app ->
            AppUsageRow(app, sharedViewModel, removed = installed != null && app.appUuid !in installed)
        }
    }
}

@Composable
private fun AppUsageRow(app: AppDataUsageSummary, sharedViewModel: SharedLockerViewModel, removed: Boolean) {
    val entry = loadLockerEntry(app.appUuid, sharedViewModel.watchType.value)
    var expanded by rememberSaveable(app.appUuid.toString()) { mutableStateOf(false) }
    Column(modifier = Modifier.clickable { expanded = !expanded }) {
        ListItem(
            leadingContent = {
                Box(modifier = Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                    entry?.let { AppImage(it, Modifier.clip(RoundedCornerShape(8.dp)), 56.dp) }
                }
            },
            headlineContent = { Text(entry?.title ?: if (removed) "Removed app" else "", fontWeight = FontWeight.Bold) },
            supportingContent = { Text(formatDataUsage(app.sentBytes, app.receivedBytes, app.mobileBytes, app.requests)) },
            trailingContent = {
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Hide domains" else "Show domains",
                )
            },
        )
        if (expanded) {
            Card(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 8.dp)) {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    UsageDetailRow("Total", app.sentBytes, app.receivedBytes, app.mobileBytes, app.requests)
                    app.byPermission.forEach {
                        val info = LEGACY_INFO.takeIf { _ -> it.permission == PluginPermissions.GLOBAL_INTERNET }
                        UsageDetailRow(it.label(), it.sentBytes, it.receivedBytes, it.mobileBytes, it.requests, info)
                    }
                }
            }
        }
    }
}

@Composable
private fun UsageDetailRow(
    label: String,
    sentBytes: Long,
    receivedBytes: Long,
    mobileBytes: Long,
    requests: Long,
    info: String? = null,
) {
    var showInfo by remember { mutableStateOf(false) }
    if (showInfo && info != null) {
        AlertDialog(
            onDismissRequest = { showInfo = false },
            title = { Text(label) },
            text = { Text(info) },
            confirmButton = { TextButton(onClick = { showInfo = false }) { Text("OK") } },
        )
    }
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            if (info != null) {
                IconButton(onClick = { showInfo = true }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Outlined.Info, contentDescription = "About $label", modifier = Modifier.size(18.dp))
                }
            }
        }
        Text(
            formatDataUsage(sentBytes, receivedBytes, mobileBytes, requests, split = true),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
