package coredevices.pebble.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
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
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.ViewTimeline
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import coredevices.pebble.rememberLibPebble
import io.rebble.libpebblecommon.permissions.PluginDefinedPermission
import io.rebble.libpebblecommon.permissions.RequestedPermissions
import io.rebble.libpebblecommon.permissions.permissionKind
import coredevices.util.relativeTime
import io.rebble.libpebblecommon.permissions.AppPermissionStatus
import io.rebble.libpebblecommon.permissions.InstallReview
import io.rebble.libpebblecommon.permissions.PermissionKind
import io.rebble.libpebblecommon.permissions.PermissionPolicy
import io.rebble.libpebblecommon.permissions.PermissionStatus
import io.ktor.http.decodeURLPart
import io.rebble.libpebblecommon.permissions.domainOf
import io.rebble.libpebblecommon.permissions.declarationOf
import io.rebble.libpebblecommon.permissions.describesRequired
import io.rebble.libpebblecommon.permissions.fileScopeOf
import io.rebble.libpebblecommon.permissions.isRequired
import io.rebble.libpebblecommon.permissions.packageOf
import io.rebble.libpebblecommon.permissions.permissionOf
import io.rebble.libpebblecommon.plugin.PluginPermissions
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Composable
fun ReplaceAppDialog(replaced: ReplacedApp, onAnswer: (replace: Boolean) -> Unit) {
    AlertDialog(
        onDismissRequest = { onAnswer(false) },
        title = { Text("Replace ${replaced.title}?") },
        text = {
            Text(
                "${replaced.title} is installed from ${replaced.installedFrom}. This copy is from " +
                    "${replaced.newFrom}, and may not be from the same developer.\n\n" +
                    "Replacing it erases everything the installed app has stored, including its settings, " +
                    "sign-ins and permissions, on this phone and on connected watches."
            )
        },
        confirmButton = { TextButton(onClick = { onAnswer(true) }) { Text("Replace") } },
        dismissButton = { TextButton(onClick = { onAnswer(false) }) { Text("Cancel") } },
    )
}

/** [onAnswer] gets null if the install was cancelled. */
@Composable
fun InstallReviewDialog(review: InstallReview, onAnswer: (Map<String, Boolean>?) -> Unit) {
    val defined = rememberPluginPermissions()
    val decisions = remember(review) { mutableStateMapOf<String, Boolean>() }
    val policy = rememberLibPebble().config.collectAsState().value.watchConfig.permissionPolicy
    // Allowing WriteFiles includes reading, so ReadFiles follows it.
    fun decisionOf(key: String): Boolean? =
        if (key == PluginPermissions.READ_FILES && decisions[PluginPermissions.WRITE_FILES] == true) true else decisions[key]
    AlertDialog(
        onDismissRequest = { onAnswer(null) },
        // The default width leaves a permission row too little room.
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.padding(horizontal = 16.dp),
        title = { Text("Install ${review.appName}?") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                // The dialog is surfaceContainerHigh, a group's usual colour.
                val cardColor = MaterialTheme.colorScheme.surfaceContainerLowest
                @Composable
                fun Group(title: String?, keys: List<String>, automatic: Boolean, subtitle: String? = null) {
                    CardGroup(
                        title,
                        keys,
                        subtitle = subtitle,
                        containerColor = cardColor,
                        dividerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) { key ->
                        PermissionRow(
                            key,
                            defined,
                            reason = review.reasonFor(key),
                            required = automatic && review.isRequired(key),
                            modifier = Modifier.padding(horizontal = 12.dp),
                        ) {
                            val decision = decisionOf(key)
                            DecisionChoice(decision, enabled = decision == decisions[key], automatic = automatic) { decisions[key] = it }
                        }
                    }
                }
                // At the end of what it allows, so it's only reached after scrolling past every permission.
                @Composable
                fun AllowAll(label: String, keys: List<String>) {
                    TextButton(
                        onClick = { keys.forEach { decisions[it] = true } },
                        modifier = Modifier.align(Alignment.End),
                    ) { Text(label) }
                }
                val toReview = review.toReview.byKind(defined)
                if (review.describesRequired) {
                    val (required, optional) = toReview.partition(review::isRequired)
                    if (required.isNotEmpty()) {
                        Group("Required", required, automatic = false)
                        if (optional.isNotEmpty()) AllowAll("Allow all required", required)
                    }
                    if (optional.isNotEmpty()) {
                        Group("Optional", optional, automatic = false, subtitle = "Decide now, or when ${review.appName} first uses them.")
                    }
                } else if (toReview.isNotEmpty()) {
                    Group("It wants to", toReview, automatic = false)
                }
                if (toReview.isNotEmpty()) AllowAll("Allow all", toReview)
                if (review.automatic.isNotEmpty()) {
                    var expanded by remember(review) { mutableStateOf(false) }
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(start = 4.dp),
                        ) {
                            Text(
                                "Allowed automatically (${review.automatic.size})",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(
                                if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                                contentDescription = if (expanded) "Collapse" else "Expand",
                            )
                        }
                        if (expanded) {
                            Group(
                                null,
                                review.automatic.requiredFirst(defined, { it }, review::isRequired),
                                automatic = true,
                                subtitle = automaticExplanation(review.appName, policy),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    // Optional ones left undecided are asked about when the app uses them.
                    onAnswer((review.toReview + review.automatic).mapNotNull { key -> decisionOf(key)?.let { key to it } }.toMap())
                },
                enabled = review.toReview.filter(review::needsAnswer).all { decisionOf(it) != null },
            ) { Text("Install") }
        },
        dismissButton = { TextButton(onClick = { onAnswer(null) }) { Text("Cancel") } },
    )
}

private fun automaticExplanation(appName: String, policy: PermissionPolicy) = when (policy) {
    PermissionPolicy.NeverAsk ->
        "You don't need to do anything here: your settings allow these. Deny any you'd rather $appName didn't use."
    else ->
        "You don't need to do anything here. $appName can't send data off your phone, so these are allowed under your current settings. Deny any you'd rather it didn't use."
}

/** The label shares its line with the control; the reason and [status] get the width below it. */
@Composable
internal fun PermissionRow(
    key: String,
    defined: Map<String, PluginDefinedPermission>,
    status: String? = null,
    label: String = permissionLabel(key, defined),
    reason: String? = null,
    required: Boolean = false,
    modifier: Modifier = Modifier,
    control: @Composable () -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                permissionIcon(key, defined),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (required) RequiredTag()
                when (permissionKind(key, defined)) {
                    PermissionKind.Exfiltrating ->
                        KindInfo(Icons.Outlined.Info, "Sends data off your phone", exfiltratingExplanation(key))
                    PermissionKind.Dangerous ->
                        KindInfo(Icons.Filled.Warning, "Sensitive", "Sensitive permissions always need your approval, whatever your settings.")
                    else -> {}
                }
            }
            control()
        }
        if (reason != null || status != null) {
            // Lined up with the label, past the icon.
            Column(modifier = Modifier.padding(start = 32.dp)) {
                reason?.let { ReasonText(label, it) }
                status?.let {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(end = 4.dp).size(12.dp),
                        )
                        Text(
                            it,
                            style = MaterialTheme.typography.labelSmall,
                            fontStyle = FontStyle.Italic,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RequiredTag() {
    Text(
        "Required",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 6.dp)
            .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
            .padding(horizontal = 4.dp, vertical = 1.dp),
    )
}

/** A developer's reason can be any length: two lines here, tapped for the rest. */
@Composable
internal fun ReasonText(title: String, reason: String) {
    var truncated by remember(reason) { mutableStateOf(false) }
    var showing by remember(reason) { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = if (truncated) Modifier.clickable { showing = true } else Modifier,
    ) {
        Text(
            reason,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { truncated = it.hasVisualOverflow },
            modifier = Modifier.weight(1f, fill = false),
        )
        if (truncated) ReasonIcon(title, Modifier.padding(start = 4.dp).size(16.dp))
    }
    if (showing) ReasonDialog(title, reason) { showing = false }
}

/** Shows [reason] in full when tapped. */
@Composable
internal fun ReasonInfoButton(title: String, reason: String, modifier: Modifier = Modifier) {
    var showing by remember(reason) { mutableStateOf(false) }
    ReasonIcon(title, modifier.clickable { showing = true })
    if (showing) ReasonDialog(title, reason) { showing = false }
}

/** "?", so it isn't confused with the kind's info icon. */
@Composable
private fun ReasonIcon(title: String, modifier: Modifier) {
    Icon(
        Icons.AutoMirrored.Outlined.HelpOutline,
        contentDescription = "Why $title",
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

@Composable
private fun ReasonDialog(title: String, reason: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(reason, modifier = Modifier.verticalScroll(rememberScrollState())) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
    )
}

@Composable
private fun KindInfo(icon: ImageVector, title: String, explanation: String) {
    var explaining by remember { mutableStateOf(false) }
    Icon(
        icon,
        contentDescription = title,
        tint = MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(start = 6.dp).size(18.dp).clickable { explaining = true },
    )
    if (explaining) {
        AlertDialog(
            onDismissRequest = { explaining = false },
            title = { Text(title) },
            text = { Text(explanation) },
            confirmButton = { TextButton(onClick = { explaining = false }) { Text("OK") } },
        )
    }
}

internal fun kindRank(key: String, defined: Map<String, PluginDefinedPermission>) = when (permissionKind(key, defined)) {
    PermissionKind.Dangerous -> 0
    PermissionKind.Exfiltrating -> 1
    PermissionKind.Write -> 2
    PermissionKind.Read -> 3
    null -> 4
}

internal fun List<String>.byKind(defined: Map<String, PluginDefinedPermission>) = sortedBy { kindRank(it, defined) }

/** Required ones first: the user can't usefully skip them. */
internal fun <T> List<T>.requiredFirst(
    defined: Map<String, PluginDefinedPermission>,
    key: (T) -> String,
    required: (T) -> Boolean,
) = sortedWith(compareByDescending<T> { required(it) }.thenBy { kindRank(key(it), defined) })

private fun exfiltratingExplanation(key: String) = when (permissionOf(key)) {
    PluginPermissions.GLOBAL_INTERNET ->
        "Allow this app to connect to any site on the internet, because it was built before the permissions system was created."
    PluginPermissions.LEGACY_PEBBLEKIT, PluginPermissions.PEBBLEKIT_2 ->
        "This allows the app to send data to its phone app, which can send it anywhere."
    else -> "This allows the app to send data off your device - limited to the specified location"
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
    PluginPermissions.SHOW_NOTIFICATIONS -> Icons.Filled.NotificationsActive
    PluginPermissions.READ_WATCH_SETTINGS, PluginPermissions.WRITE_WATCH_SETTINGS -> Icons.Filled.Watch
    PluginPermissions.READ_MUSIC, PluginPermissions.CONTROL_MUSIC -> Icons.Filled.MusicNote
    PluginPermissions.READ_APPS, PluginPermissions.LAUNCH_APPS -> Icons.Filled.Apps
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
    PluginPermissions.NOTIFICATIONS -> packageOf(key)?.let { "Read your notifications from $it" } ?: "Read all your notifications"
    PluginPermissions.CALENDAR -> "Read your calendar"
    PluginPermissions.CONTACTS -> "Read your contacts"
    PluginPermissions.TIMELINE -> "Add pins to your timeline"
    PluginPermissions.SHOW_NOTIFICATIONS -> "Show notifications on your watch"
    PluginPermissions.READ_WATCH_SETTINGS -> "Read your watch settings"
    PluginPermissions.WRITE_WATCH_SETTINGS -> "Change your watch settings"
    PluginPermissions.READ_MUSIC -> "See what music is playing"
    PluginPermissions.CONTROL_MUSIC -> "Control music playback"
    PluginPermissions.READ_APPS -> "See which apps and watchfaces you have"
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
internal fun rememberPluginPermissions(): Map<String, PluginDefinedPermission> {
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
            val reason by remember(appUuid, keys) {
                libPebble.appPermissionStatuses(appUuid).map { statuses ->
                    keys.singleOrNull()?.let { key -> statuses?.firstOrNull { it.permission == key }?.reason }
                }
            }.collectAsState(null)
            Card(onClick = { onReview(appUuid) }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp)) {
                    Text(
                        "${appName ?: "An app"} wants to ${requestSummary(keys, defined)}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    reason?.let { ReasonText(appName ?: "Why", it) }
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
    // Null while loading.
    val loaded by remember(appUuid) {
        libPebble.appPermissionStatuses(appUuid).map { all -> LoadedStatuses(all?.sortedBy { it.status != PermissionStatus.Waiting }) }
    }.collectAsState(null)
    // Leaving the page stops asking about anything still waiting.
    val current by rememberUpdatedState(loaded?.statuses)
    DisposableEffect(appUuid) {
        onDispose {
            val unanswered = current.orEmpty().filter { it.status == PermissionStatus.Waiting }.map { it.permission }
            if (unanswered.isNotEmpty()) {
                GlobalScope.launch { libPebble.markPermissionRequestsSeen(appUuid, unanswered) }
            }
        }
    }
    val legacy by remember(appUuid) {
        libPebble.getLockerApp(appUuid).map { it?.properties?.legacy == true }
    }.collectAsState(false)
    // Null until the user toggles it: open, except for a legacy app's.
    var expandedByUser by remember(appUuid) { mutableStateOf<Boolean?>(null) }
    val statuses = (loaded ?: return).statuses
    if (statuses.isNullOrEmpty()) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text("Permissions", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 4.dp))
            Text(
                if (statuses == null) "Shown once the app has been installed on your watch."
                else "This app doesn't use any permissions.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    val scope = rememberCoroutineScope()
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        // A legacy app's are collapsed, and never hidden while a request waits for an answer.
        val open = (legacy && statuses.any { it.status == PermissionStatus.Waiting }) || (expandedByUser ?: !legacy)
        SectionHeader(if (legacy) "Permissions (Legacy App)" else "Permissions", open) { expandedByUser = !open }
        if (!open) return@Column
        if (legacy) {
            Text(
                "Apps built before permissions existed can use these by default. To be asked first, turn on " +
                    "\"Ask before older apps use permissions\" in Settings.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        @Composable
        fun StatusRow(row: AppPermissionStatus, inGroups: Boolean) {
            val lastUsedAtMs = row.lastUsedAtMs
            val used = if (lastUsedAtMs == null) "Not used yet" else "Used ${relativeTime(Instant.fromEpochMilliseconds(lastUsedAtMs))}"
            val automatic = row.status == PermissionStatus.AllowedAutomatically
            // Grouped, "Allowed automatically" is the group's title.
            val note = if (automatic && !inGroups) "Allowed automatically · $used" else used
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
            PermissionRow(
                row.permission,
                defined,
                note,
                reason = row.reason,
                required = automatic && inGroups && row.required == true,
                modifier = Modifier.padding(horizontal = 12.dp),
            ) {
                when {
                    impliedByWrite -> DecisionChoice(true, enabled = false) {}
                    bareDenied -> DecisionChoice(false, enabled = false) {}
                    else -> DecisionChoice(
                        when (row.status) {
                            PermissionStatus.Granted -> true
                            PermissionStatus.Denied -> false
                            else -> null
                        },
                        automatic = automatic,
                        onDecide = decide,
                    )
                }
            }
        }
        // Grouped only once the app says which are required.
        if (statuses.any { it.required != null }) {
            val (automatic, asked) = statuses.partition { it.status == PermissionStatus.AllowedAutomatically }
            val (required, optional) = asked.partition { it.required == true }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (required.isNotEmpty()) {
                    CardGroup("Required", required.sortedBy { kindRank(it.permission, defined) }) { StatusRow(it, inGroups = true) }
                }
                if (optional.isNotEmpty()) {
                    CardGroup("Optional", optional.sortedBy { kindRank(it.permission, defined) }) { StatusRow(it, inGroups = true) }
                }
                if (automatic.isNotEmpty()) {
                    CardGroup(
                        "Allowed automatically",
                        automatic.requiredFirst(defined, { it.permission }, { it.required == true }),
                    ) { StatusRow(it, inGroups = true) }
                }
            }
        } else {
            CardGroup(null, statuses.sortedBy { kindRank(it.permission, defined) }) { StatusRow(it, inGroups = false) }
        }
    }
}

/** A section's title, which collapses and expands it. */
@Composable
private fun SectionHeader(title: String, open: Boolean, onToggle: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).padding(bottom = 4.dp))
        Icon(
            if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
            contentDescription = if (open) "Collapse" else "Expand",
        )
    }
}

/** [statuses] is null until the app's permissions are known. */
private class LoadedStatuses(val statuses: List<AppPermissionStatus>?)

/** Whether the bare file permission reads as denied, with `WriteFiles` standing in for a `ReadFiles` the app never declared. */
private fun List<AppPermissionStatus>.deniesBareFilePermission(bare: String): Boolean {
    val own = firstOrNull { it.permission == bare }
    if (own != null) return own.status == PermissionStatus.Denied
    return bare == PluginPermissions.READ_FILES &&
        any { it.permission == PluginPermissions.WRITE_FILES && it.status == PermissionStatus.Denied }
}

/** Nothing is selected while [granted] is null, except that an [automatic] grant shows Allow faded. */
@Composable
private fun DecisionChoice(granted: Boolean?, enabled: Boolean = true, automatic: Boolean = false, onDecide: (Boolean) -> Unit) {
    val options = listOf(Triple(false, Icons.Filled.Close, "Deny"), Triple(true, Icons.Filled.Check, "Allow"))
    val implied = granted == null && automatic
    val impliedColors = SegmentedButtonDefaults.colors(
        activeContainerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
        activeContentColor = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f),
    )
    // Short, so the reason below sits close to the label: without the 48dp touch target's padding.
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
        SingleChoiceSegmentedButtonRow {
            options.forEachIndexed { index, (value, icon, label) ->
                val impliedAllow = implied && value
                SegmentedButton(
                    selected = granted == value || impliedAllow,
                    onClick = { onDecide(value) },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                    enabled = enabled,
                    colors = if (impliedAllow) impliedColors else SegmentedButtonDefaults.colors(),
                    icon = {},
                    modifier = Modifier.width(40.dp).height(32.dp),
                ) {
                    Icon(icon, contentDescription = label, modifier = Modifier.size(18.dp))
                }
            }
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
    var open by remember(requested) { mutableStateOf(true) }
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        SectionHeader("Permissions", open) { open = !open }
        if (!open) return@Column
        if (requested.declared == null) {
            val access = keys.map(::legacyAccessName).distinct().joinToString()
            val text = if (config.watchConfig.askLegacyAppsForPermissions) {
                "Built before permissions: asks before using $access"
            } else {
                "Built before permissions: has access to $access"
            }
            Text(text, style = MaterialTheme.typography.bodyMedium)
        } else {
            val declared = requested.declared.orEmpty()
            val sorted = keys.byKind(defined)
            // Reasons are behind an info button here: this is an overview, not where the user decides.
            @Composable
            fun ListingRow(key: String) {
                val label = permissionLabel(key, defined)
                PermissionRow(key, defined, label = label, modifier = Modifier.padding(horizontal = 12.dp)) {
                    declared.declarationOf(key)?.reason?.let { ReasonInfoButton(label, it, Modifier.padding(start = 8.dp).size(20.dp)) }
                }
            }
            if (declared.describesRequired()) {
                val (required, optional) = sorted.partition(declared::isRequired)
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (required.isNotEmpty()) CardGroup("Required", required) { ListingRow(it) }
                    if (optional.isNotEmpty()) CardGroup("Optional", optional) { ListingRow(it) }
                }
            } else {
                CardGroup(null, sorted) { ListingRow(it) }
            }
        }
    }
}

private fun legacyAccessName(key: String) = when (permissionOf(key)) {
    PluginPermissions.GLOBAL_INTERNET -> "the internet"
    PluginPermissions.LOCATION -> "location"
    PluginPermissions.TIMELINE -> "timeline pins"
    PluginPermissions.SHOW_NOTIFICATIONS -> "watch notifications"
    else -> "phone apps"
}

/** A dot for anything waiting for an answer; "!" if something the app needs is among it. */
@Composable
fun PermissionRequestBadge(appUuid: Uuid, modifier: Modifier = Modifier) {
    val libPebble = rememberLibPebble()
    val pending by libPebble.appsWithPendingPermissionRequests.collectAsState()
    if (appUuid !in pending) return
    val requiredWaiting by remember(appUuid) {
        libPebble.appPermissionStatuses(appUuid).map { statuses ->
            statuses.orEmpty().any { it.required == true && it.status == PermissionStatus.Waiting }
        }
    }.collectAsState(false)
    if (requiredWaiting) {
        Badge(modifier = modifier) { Text("!") }
    } else {
        Badge(modifier = modifier)
    }
}
