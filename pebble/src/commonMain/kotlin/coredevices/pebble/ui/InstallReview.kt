package coredevices.pebble.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PhonelinkRing
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.ViewTimeline
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import coredevices.pebble.rememberLibPebble
import io.rebble.libpebblecommon.permissions.PluginDefinedPermission
import io.rebble.libpebblecommon.permissions.RequestedPermissions
import io.rebble.libpebblecommon.permissions.permissionKind
import coredevices.util.relativeTime
import io.rebble.libpebblecommon.permissions.AppPermissionStatus
import io.rebble.libpebblecommon.permissions.InstallReview
import io.rebble.libpebblecommon.permissions.PermissionKind
import io.rebble.libpebblecommon.permissions.PermissionStatus
import io.ktor.http.decodeURLPart
import io.rebble.libpebblecommon.permissions.domainOf
import io.rebble.libpebblecommon.permissions.fileScopeOf
import io.rebble.libpebblecommon.permissions.packageOf
import io.rebble.libpebblecommon.permissions.permissionOf
import io.rebble.libpebblecommon.plugin.PluginPermissions
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** [onAnswer] gets null if the install was cancelled. */
@Composable
fun InstallReviewDialog(review: InstallReview, onAnswer: (Map<String, Boolean>?) -> Unit) {
    val defined = rememberPluginPermissions()
    val decisions = remember(review) { mutableStateMapOf<String, Boolean>() }
    // Allowing WriteFiles includes reading, so ReadFiles follows it.
    fun decisionOf(key: String): Boolean? =
        if (key == PluginPermissions.READ_FILES && decisions[PluginPermissions.WRITE_FILES] == true) true else decisions[key]
    AlertDialog(
        onDismissRequest = { onAnswer(null) },
        title = { Text("Install ${review.appName}?") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("It wants to:", style = MaterialTheme.typography.bodyMedium)
                review.toReview.forEach { key ->
                    PermissionRow(key, defined) {
                        val decision = decisionOf(key)
                        when {
                            decision == null -> UndecidedChoice { decisions[key] = it }
                            decision != decisions[key] -> Switch(checked = true, onCheckedChange = {}, enabled = false)
                            else -> Switch(checked = decision, onCheckedChange = { decisions[key] = it })
                        }
                    }
                }
                // At the end of the list, so it's only reached after scrolling past every permission.
                TextButton(
                    onClick = { review.toReview.forEach { decisions[it] = true } },
                    modifier = Modifier.align(Alignment.End),
                ) { Text("Allow all") }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAnswer(review.toReview.associateWith { decisionOf(it)!! }) },
                enabled = review.toReview.all { decisionOf(it) != null },
            ) { Text("Install") }
        },
        dismissButton = { TextButton(onClick = { onAnswer(null) }) { Text("Cancel") } },
    )
}

@Composable
private fun PermissionRow(
    key: String,
    defined: Map<String, PluginDefinedPermission>,
    status: String? = null,
    control: @Composable () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Icon(
            permissionIcon(key, defined),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(permissionLabel(key, defined), style = MaterialTheme.typography.bodyMedium)
            status?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (permissionKind(key, defined) == PermissionKind.Exfiltrating) ExfiltratingInfo()
        control()
    }
}

@Composable
private fun ExfiltratingInfo() {
    var explaining by remember { mutableStateOf(false) }
    Icon(
        Icons.Outlined.Info,
        contentDescription = "Can send data off your phone",
        tint = MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(end = 8.dp).size(18.dp).clickable { explaining = true },
    )
    if (explaining) {
        AlertDialog(
            onDismissRequest = { explaining = false },
            title = { Text("Sends data off your phone") },
            text = {
                Text("This allows the app to send data off your device - limited to the listed location")
            },
            confirmButton = { TextButton(onClick = { explaining = false }) { Text("OK") } },
        )
    }
}

private fun permissionIcon(key: String, defined: Map<String, PluginDefinedPermission>): ImageVector = when (permissionOf(key)) {
    PluginPermissions.INTERNET, PluginPermissions.GLOBAL_INTERNET -> Icons.Filled.Public
    PluginPermissions.LOCAL_NETWORK -> Icons.Filled.Router
    PluginPermissions.LOCATION -> Icons.Filled.LocationOn
    PluginPermissions.WATCH_MICROPHONE -> Icons.Filled.Mic
    PluginPermissions.NOTIFICATIONS -> Icons.Filled.Notifications
    PluginPermissions.CALENDAR -> Icons.Filled.CalendarMonth
    PluginPermissions.CONTACTS -> Icons.Filled.Contacts
    PluginPermissions.TIMELINE -> Icons.Filled.ViewTimeline
    PluginPermissions.READ_WATCH_SETTINGS, PluginPermissions.WRITE_WATCH_SETTINGS -> Icons.Filled.Watch
    PluginPermissions.READ_MUSIC, PluginPermissions.CONTROL_MUSIC -> Icons.Filled.MusicNote
    PluginPermissions.LAUNCH_APPS -> Icons.Filled.Apps
    PluginPermissions.FIND_MY_PHONE -> Icons.Filled.PhonelinkRing
    PluginPermissions.READ_FILES, PluginPermissions.WRITE_FILES -> Icons.Filled.Folder
    PluginPermissions.LEGACY_PEBBLEKIT -> Icons.Filled.Campaign
    PluginPermissions.PEBBLEKIT_2 -> Icons.Filled.SwapHoriz
    else -> if (permissionKind(key, defined) == PermissionKind.Exfiltrating) Icons.Filled.CloudUpload else Icons.Filled.Extension
}

/** Completes "<app> wants to". */
fun permissionLabel(key: String, defined: Map<String, PluginDefinedPermission>): String = when (permissionOf(key)) {
    PluginPermissions.INTERNET -> domainOf(key)?.let { "Connect to $it" } ?: "Connect to any website"
    PluginPermissions.LOCAL_NETWORK -> "Connect to devices on your local network"
    PluginPermissions.GLOBAL_INTERNET -> "Connect to any website or local device"
    PluginPermissions.LOCATION -> "Use your location"
    PluginPermissions.WATCH_MICROPHONE -> "Use your watch microphone"
    PluginPermissions.NOTIFICATIONS -> "Read your notifications"
    PluginPermissions.CALENDAR -> "Read your calendar"
    PluginPermissions.CONTACTS -> "Read your contacts"
    PluginPermissions.TIMELINE -> "Add pins to your timeline"
    PluginPermissions.READ_WATCH_SETTINGS -> "Read your watch settings"
    PluginPermissions.WRITE_WATCH_SETTINGS -> "Change your watch settings"
    PluginPermissions.READ_MUSIC -> "See what music is playing"
    PluginPermissions.CONTROL_MUSIC -> "Control music playback"
    PluginPermissions.LAUNCH_APPS -> "Launch apps on your watch"
    PluginPermissions.FIND_MY_PHONE -> "Ring your phone to find it"
    PluginPermissions.READ_FILES -> fileScopeOf(key)?.let { "Read '${fileScopeLabel(it)}' in device storage" } ?: "Read files from device storage you permit"
    PluginPermissions.WRITE_FILES -> fileScopeOf(key)?.let { "Read & write to '${fileScopeLabel(it)}' in device storage" } ?: "Read & write to files in device storage you permit"
    PluginPermissions.LEGACY_PEBBLEKIT -> "Broadcast messages to every app on your phone (legacy PebbleKit)"
    PluginPermissions.PEBBLEKIT_2 -> "Exchange messages with ${packageOf(key)}"
    else -> defined[key]?.let { "${it.label} (via ${it.pluginName})" } ?: key
}

/** The tail of a picked entry: `Documents/Notes` out of a SAF uri or an iOS path. */
internal fun fileScopeLabel(scope: String): String {
    val decoded = scope.decodeURLPart()
    return decoded.substringAfterLast(':').trimEnd('/').split('/').filter { it.isNotEmpty() }
        .takeLast(2).joinToString("/").ifEmpty { decoded }
}

@Composable
private fun rememberPluginPermissions(): Map<String, PluginDefinedPermission> {
    val libPebble = rememberLibPebble()
    return remember { libPebble.pluginPermissions() }.collectAsState(emptyMap()).value
}

fun requestSummary(keys: List<String>, defined: Map<String, PluginDefinedPermission>): String {
    val labels = keys.map { permissionLabel(it, defined).replaceFirstChar { c -> c.lowercase() } }
    return if (labels.size <= 1) labels.joinToString() else labels.dropLast(1).joinToString() + " and " + labels.last()
}

@Composable
fun PendingPermissionRequests(onReview: (Uuid) -> Unit) {
    val libPebble = rememberLibPebble()
    val defined = rememberPluginPermissions()
    val active by remember {
        libPebble.activePermissionRequests().map { requests -> requests.filter { it.seenAtMs == null } }
    }.collectAsState(emptyList())
    val scope = rememberCoroutineScope()
    Column(modifier = Modifier.padding(horizontal = 12.dp)) {
        active.groupBy { it.appUuid }.forEach { (appUuid, requests) ->
            val keys = requests.map { it.permission }
            val appName by remember(appUuid) {
                libPebble.getLockerApp(appUuid).map { it?.properties?.title }
            }.collectAsState(null)
            Card(onClick = { onReview(appUuid) }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp)) {
                    Text(
                        "${appName ?: "An app"} wants to ${requestSummary(keys, defined)}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Row(modifier = Modifier.align(Alignment.End)) {
                        if (keys.size == 1) {
                            fun answer(granted: Boolean) = scope.launch {
                                libPebble.setPermissionDecisions(appUuid, mapOf(keys.single() to granted))
                            }
                            TextButton(onClick = { answer(false) }) { Text("Deny") }
                            TextButton(onClick = { answer(true) }) { Text("Allow") }
                        } else {
                            TextButton(onClick = { onReview(appUuid) }) { Text("Review") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AppPermissionsSection(appUuid: Uuid) {
    val libPebble = rememberLibPebble()
    val config by libPebble.config.collectAsState()
    if (!config.watchConfig.enablePlugins) return
    val defined = rememberPluginPermissions()
    val statuses by remember(appUuid) {
        libPebble.appPermissionStatuses(appUuid).map { all -> all.sortedBy { it.status != PermissionStatus.Waiting } }
    }.collectAsState(emptyList())
    // Leaving the page stops asking about anything still waiting.
    val current by rememberUpdatedState(statuses)
    DisposableEffect(appUuid) {
        onDispose {
            val unanswered = current.filter { it.status == PermissionStatus.Waiting }.map { it.permission }
            if (unanswered.isNotEmpty()) {
                GlobalScope.launch { libPebble.markPermissionRequestsSeen(appUuid, unanswered) }
            }
        }
    }
    val legacy by remember(appUuid) {
        libPebble.getLockerApp(appUuid).map { it?.properties?.legacy == true }
    }.collectAsState(false)
    var expanded by remember(appUuid) { mutableStateOf(false) }
    if (statuses.isEmpty()) return
    val scope = rememberCoroutineScope()
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        if (legacy) {
            // Never hide a request still waiting for an answer.
            val open = expanded || statuses.any { it.status == PermissionStatus.Waiting }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clickable { expanded = !open },
            ) {
                Text(
                    "Permissions (Legacy App)",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f).padding(bottom = 4.dp),
                )
                Icon(
                    if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (open) "Collapse" else "Expand",
                )
            }
            if (!open) return@Column
            Text(
                "Apps built before permissions existed can use these by default. To be asked first, turn on " +
                    "\"Ask before older apps use permissions\" in Settings.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        } else {
            Text("Permissions", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 4.dp))
        }
        statuses.forEach { row ->
            val lastUsedAtMs = row.lastUsedAtMs
            val used = if (lastUsedAtMs == null) "Not used yet" else "Used ${relativeTime(Instant.fromEpochMilliseconds(lastUsedAtMs))}"
            val note = if (row.status == PermissionStatus.AllowedAutomatically) "Allowed automatically · $used" else used
            val decide = { granted: Boolean ->
                val decisions = buildMap {
                    put(row.permission, granted)
                    // Allowing WriteFiles includes reading, so ReadFiles follows it.
                    if (granted && row.permission == PluginPermissions.WRITE_FILES &&
                        statuses.any { it.permission == PluginPermissions.READ_FILES }
                    ) put(PluginPermissions.READ_FILES, true)
                }
                scope.launch { libPebble.setPermissionDecisions(appUuid, decisions) }
                Unit
            }
            val impliedByWrite = row.permission == PluginPermissions.READ_FILES &&
                statuses.any { it.permission == PluginPermissions.WRITE_FILES && it.status == PermissionStatus.Granted }
            // A picked entry follows its bare permission: off while that is denied, back when it's allowed again.
            val bareDenied = fileScopeOf(row.permission) != null && statuses.deniesBareFilePermission(permissionOf(row.permission))
            PermissionRow(row.permission, defined, note) {
                when {
                    impliedByWrite -> Switch(checked = true, onCheckedChange = {}, enabled = false)
                    bareDenied -> Switch(checked = false, onCheckedChange = {}, enabled = false)
                    row.status == PermissionStatus.Granted || row.status == PermissionStatus.Denied ->
                        Switch(checked = row.status == PermissionStatus.Granted, onCheckedChange = decide)
                    else -> UndecidedChoice(decide)
                }
            }
        }
    }
}

/** Whether the bare file permission reads as denied, with `WriteFiles` standing in for a `ReadFiles` the app never declared. */
private fun List<AppPermissionStatus>.deniesBareFilePermission(bare: String): Boolean {
    val own = firstOrNull { it.permission == bare }
    if (own != null) return own.status == PermissionStatus.Denied
    return bare == PluginPermissions.READ_FILES &&
        any { it.permission == PluginPermissions.WRITE_FILES && it.status == PermissionStatus.Denied }
}

@Composable
private fun UndecidedChoice(onDecide: (Boolean) -> Unit) {
    Row {
        OutlinedIconButton(onClick = { onDecide(false) }) {
            Icon(Icons.Filled.Close, contentDescription = "Deny", tint = MaterialTheme.colorScheme.error)
        }
        OutlinedIconButton(onClick = { onDecide(true) }) {
            Icon(Icons.Filled.Check, contentDescription = "Allow", tint = MaterialTheme.colorScheme.primary)
        }
    }
}

/** What a store app asks for, before it's in the locker. */
@Composable
fun StorePermissionsSection(requested: RequestedPermissions) {
    val config by rememberLibPebble().config.collectAsState()
    val defined = rememberPluginPermissions()
    val keys = requested.all
    if (keys.isEmpty()) return
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("Permissions", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 4.dp))
        if (requested.declared == null) {
            val access = keys.map(::legacyAccessName).distinct().joinToString()
            val text = if (config.watchConfig.askLegacyAppsForPermissions) {
                "Built before permissions: asks before using $access"
            } else {
                "Built before permissions: has access to $access"
            }
            Text(text, style = MaterialTheme.typography.bodyMedium)
        } else {
            keys.forEach { PermissionRow(it, defined) {} }
        }
    }
}

private fun legacyAccessName(key: String) = when (permissionOf(key)) {
    PluginPermissions.GLOBAL_INTERNET -> "the internet"
    PluginPermissions.LOCATION -> "location"
    PluginPermissions.TIMELINE -> "timeline pins"
    else -> "phone apps"
}

@Composable
fun PermissionRequestBadge(appUuid: Uuid, modifier: Modifier = Modifier) {
    val libPebble = rememberLibPebble()
    val pending by libPebble.appsWithPendingPermissionRequests.collectAsState()
    if (appUuid in pending) Badge(modifier = modifier)
}
