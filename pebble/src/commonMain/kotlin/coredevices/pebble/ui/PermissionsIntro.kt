package coredevices.pebble.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.russhwolf.settings.Settings
import com.russhwolf.settings.set
import coredevices.pebble.rememberLibPebble
import coredevices.util.CoreConfigHolder
import io.rebble.libpebblecommon.permissions.PermissionPolicy
import org.koin.compose.koinInject

private const val HAS_SEEN_PERMISSIONS_INTRO_SETTINGS_KEY = "hasSeenPermissionsIntro"

fun Settings.hasSeenPermissionsIntro() = getBoolean(HAS_SEEN_PERMISSIONS_INTRO_SETTINGS_KEY, false)
fun Settings.setHasSeenPermissionsIntro(seen: Boolean) = set(HAS_SEEN_PERMISSIONS_INTRO_SETTINGS_KEY, seen)

fun PermissionPolicy.displayName(): String = when (this) {
    PermissionPolicy.AskIfExfiltrating -> "When needed"
    PermissionPolicy.AlwaysAsk -> "Always"
    PermissionPolicy.NeverAsk -> "Never"
}

fun PermissionPolicy.description(): String = when (this) {
    PermissionPolicy.AskIfExfiltrating ->
        "Apps ask before connecting to the internet or acting for you (e.g. controlling your music or ringing your phone)"
    PermissionPolicy.AlwaysAsk -> "Apps ask before using any permission."
    PermissionPolicy.NeverAsk -> "Apps can use any permission, unless you explicitly turn it off on the app's page"
}

private fun PermissionPolicy.details(): String = when (this) {
    PermissionPolicy.AskIfExfiltrating ->
        "Apps that can't send anything off your phone (no internet access and no phone companion app) can read your data, " +
            "like your location, calendar, notifications or music, without asking, because it can't go anywhere. " +
            "Apps that could send it off your phone ask first.\n\n" +
            "Every app asks before going online, and before acting for you: adding timeline pins, controlling " +
            "music, launching apps, changing watch settings, ringing your phone, or using a plugin's actions."
    PermissionPolicy.AlwaysAsk ->
        "Apps ask the first time they use any permission, including reading data they couldn't send anywhere."
    PermissionPolicy.NeverAsk ->
        "Apps get any permission they ask for without asking you, including going online. Anything you've " +
            "turned off on an app's page stays off. Not recommended!"
}

/** Every mode, for the setting's info popup. */
@Composable
fun PermissionPolicyDetails() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "Whichever you choose, you can review and change each app's permissions on its page. Apps built before " +
                    "permissions existed have their own setting, \"Ask before older apps use permissions\".",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PermissionPolicy.entries.forEach { policy ->
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    if (policy == PermissionPolicy.AskIfExfiltrating) "${policy.displayName()} (default)" else policy.displayName(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(policy.details(), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** What permissions are, and when apps ask; shown once, in a dialog or in onboarding. */
@Composable
fun PermissionsIntroContent(settings: SettingsItemsState?, modifier: Modifier = Modifier) {
    val libPebble = rememberLibPebble()
    val hasWatch = libPebble.watches.collectAsState().value.isNotEmpty()
    val indexEnabled = koinInject<CoreConfigHolder>().config.collectAsState().value.enableIndex
    val askers = when {
        hasWatch && indexEnabled ->
            "Watchfaces, watchapps and plugins (including the ones Index uses to save your notes and reminders)"
        indexEnabled -> "Plugins, including the ones Index uses to save your notes and reminders,"
        else -> "Watchfaces, watchapps and plugins"
    }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "$askers now ask before using your phone's features: going online, your location, " +
                "notifications, calendar, contacts, music, files, watch settings and timeline.",
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            "Change the permissions mode for all apps below (also in Settings/Apps), and configure " +
                    "each app's individual settings on its own screen",
            style = MaterialTheme.typography.bodySmall,
        )
        if (hasWatch) {
            Text(
                "Older apps which weren't built for the Permissions system are not affected, " +
                        "unless you choose to enforce permissions for them in Settings -> Apps.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        settings.Show(SettingsIds.PermissionPolicy)
    }
}

@Composable
fun PermissionsIntroDialog(onDismissRequest: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        // The settings row needs about the width of the settings screen.
        modifier = Modifier.padding(horizontal = 16.dp),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = { Text("App permissions") },
        text = {
            val settings = rememberSettingsItemsState(navBarNav = null, snackbarDisplay = {})
            PermissionsIntroContent(settings, Modifier.verticalScroll(rememberScrollState()))
        },
        confirmButton = { TextButton(onClick = onDismissRequest) { Text("OK") } },
    )
}
