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
import coreapp.pebble.generated.resources.Res
import coreapp.pebble.generated.resources.app_data_usage_about
import coreapp.pebble.generated.resources.app_data_usage_any_website
import coreapp.pebble.generated.resources.app_data_usage_empty
import coreapp.pebble.generated.resources.app_data_usage_hide_domains
import coreapp.pebble.generated.resources.app_data_usage_intro
import coreapp.pebble.generated.resources.app_data_usage_last_days
import coreapp.pebble.generated.resources.app_data_usage_legacy_info
import coreapp.pebble.generated.resources.app_data_usage_local_network
import coreapp.pebble.generated.resources.app_data_usage_removed_app
import coreapp.pebble.generated.resources.app_data_usage_show_domains
import coreapp.pebble.generated.resources.app_data_usage_title
import coreapp.pebble.generated.resources.app_data_usage_total
import coreapp.util.generated.resources.ok
import coredevices.pebble.rememberLibPebble
import coredevices.util.storage.formatDataUsage
import io.rebble.libpebblecommon.DATA_USAGE_KEPT_DAYS
import io.rebble.libpebblecommon.permissions.AppDataUsageSummary
import io.rebble.libpebblecommon.permissions.PermissionDataUsage
import io.rebble.libpebblecommon.permissions.byApp
import io.rebble.libpebblecommon.permissions.domainOf
import io.rebble.libpebblecommon.plugin.PluginPermissions
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.map
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import coreapp.util.generated.resources.Res as UtilRes

@Composable
private fun PermissionDataUsage.label(): String = domainOf(permission) ?: when (permission) {
    PluginPermissions.LOCAL_NETWORK -> stringResource(Res.string.app_data_usage_local_network)
    else -> stringResource(Res.string.app_data_usage_any_website)
}

@Composable
fun AppDataUsageScreen(nav: NavBarNav, topBarParams: TopBarParams) {
    LaunchedEffect(Unit) {
        topBarParams.searchAvailable(null)
        topBarParams.actions {}
        topBarParams.title(getString(Res.string.app_data_usage_title))
    }
    val libPebble = rememberLibPebble()
    val usage by remember { libPebble.appDataUsage(DATA_USAGE_KEPT_DAYS).map { it.byApp() } }.collectAsState(null)
    val sharedViewModel: SharedLockerViewModel = koinInject()
    sharedViewModel.Init()
    val installed = allCollectionUuids()
    val apps = usage ?: return
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Text(
                if (apps.isEmpty()) {
                    stringResource(Res.string.app_data_usage_empty, DATA_USAGE_KEPT_DAYS)
                } else {
                    stringResource(Res.string.app_data_usage_intro, DATA_USAGE_KEPT_DAYS)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        items(apps, key = { it.appUuid.toString() }) { app ->
            AppUsageRow(app, sharedViewModel, removed = installed != null && app.appUuid !in installed) {
                nav.navigateTo(PebbleNavBarRoutes.LockerAppRoute(uuid = app.appUuid.toString(), storedId = null, storeSource = null))
            }
        }
    }
}

@Composable
private fun AppUsageRow(
    app: AppDataUsageSummary,
    sharedViewModel: SharedLockerViewModel,
    removed: Boolean,
    openApp: () -> Unit,
) {
    val entry = loadLockerEntry(app.appUuid, sharedViewModel.watchType.value)
    var expanded by rememberSaveable(app.appUuid.toString()) { mutableStateOf(false) }
    Column(modifier = Modifier.clickable { expanded = !expanded }) {
        ListItem(
            leadingContent = {
                Box(
                    modifier = Modifier.size(56.dp).clickable(enabled = entry != null && !removed, onClick = openApp),
                    contentAlignment = Alignment.Center,
                ) {
                    entry?.let { AppImage(it, Modifier.clip(RoundedCornerShape(8.dp)), 56.dp) }
                }
            },
            headlineContent = { Text(entry?.title ?: if (removed) stringResource(Res.string.app_data_usage_removed_app) else "", fontWeight = FontWeight.Bold) },
            supportingContent = { Text(formatDataUsage(app.sentBytes, app.receivedBytes, app.mobileBytes, app.requests)) },
            trailingContent = {
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = stringResource(if (expanded) Res.string.app_data_usage_hide_domains else Res.string.app_data_usage_show_domains),
                )
            },
        )
        if (expanded) {
            Card(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 8.dp)) {
                AppUsageDetails(app, Modifier.padding(vertical = 8.dp))
            }
        }
    }
}

@Composable
private fun AppUsageDetails(app: AppDataUsageSummary, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        UsageDetailRow(stringResource(Res.string.app_data_usage_total), app.sentBytes, app.receivedBytes, app.mobileBytes, app.requests)
        val legacyInfo = stringResource(Res.string.app_data_usage_legacy_info)
        app.byPermission.forEach {
            val info = legacyInfo.takeIf { _ -> it.permission == PluginPermissions.GLOBAL_INTERNET }
            UsageDetailRow(it.label(), it.sentBytes, it.receivedBytes, it.mobileBytes, it.requests, info)
        }
    }
}

/** Shown on an app's page; nothing if it hasn't used the network. */
@Composable
fun AppDataUsageSection(appUuid: Uuid) {
    val libPebble = rememberLibPebble()
    val usage by remember(appUuid) {
        libPebble.appDataUsage(DATA_USAGE_KEPT_DAYS).map { rows -> rows.filter { it.appUuid == appUuid }.byApp().firstOrNull() }
    }.collectAsState(null)
    val app = usage ?: return
    var open by rememberSaveable(appUuid.toString()) { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            SectionHeader(stringResource(Res.string.app_data_usage_title), open) { open = !open }
            if (!open) {
                Text(
                    stringResource(Res.string.app_data_usage_last_days, DATA_USAGE_KEPT_DAYS, formatDataUsage(app.sentBytes, app.receivedBytes, app.mobileBytes, app.requests)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (open) AppUsageDetails(app)
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
            confirmButton = { TextButton(onClick = { showInfo = false }) { Text(stringResource(UtilRes.string.ok)) } },
        )
    }
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            if (info != null) {
                IconButton(onClick = { showInfo = true }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Outlined.Info, contentDescription = stringResource(Res.string.app_data_usage_about, label), modifier = Modifier.size(18.dp))
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
