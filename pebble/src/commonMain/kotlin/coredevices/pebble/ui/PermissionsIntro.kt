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
import coreapp.pebble.generated.resources.Res
import coreapp.pebble.generated.resources.permissions_intro_body_index
import coreapp.pebble.generated.resources.permissions_intro_body_watch
import coreapp.pebble.generated.resources.permissions_intro_body_watch_and_index
import coreapp.pebble.generated.resources.permissions_intro_change_mode
import coreapp.pebble.generated.resources.permissions_intro_older_apps
import coreapp.pebble.generated.resources.permissions_intro_title
import coreapp.pebble.generated.resources.permissions_policy_always_ask
import coreapp.pebble.generated.resources.permissions_policy_always_ask_description
import coreapp.pebble.generated.resources.permissions_policy_always_ask_details
import coreapp.pebble.generated.resources.permissions_policy_ask_if_exfiltrating
import coreapp.pebble.generated.resources.permissions_policy_ask_if_exfiltrating_description
import coreapp.pebble.generated.resources.permissions_policy_ask_if_exfiltrating_details
import coreapp.pebble.generated.resources.permissions_policy_default
import coreapp.pebble.generated.resources.permissions_policy_details_intro
import coreapp.pebble.generated.resources.permissions_policy_never_ask
import coreapp.pebble.generated.resources.permissions_policy_never_ask_description
import coreapp.pebble.generated.resources.permissions_policy_never_ask_details
import coreapp.util.generated.resources.ok
import coredevices.pebble.rememberWatches
import coredevices.util.CoreConfigHolder
import io.rebble.libpebblecommon.permissions.PermissionPolicy
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import coreapp.util.generated.resources.Res as UtilRes

private const val HAS_SEEN_PERMISSIONS_INTRO_SETTINGS_KEY = "hasSeenPermissionsIntro"

fun Settings.hasSeenPermissionsIntro() = getBoolean(HAS_SEEN_PERMISSIONS_INTRO_SETTINGS_KEY, false)
fun Settings.setHasSeenPermissionsIntro(seen: Boolean) = set(HAS_SEEN_PERMISSIONS_INTRO_SETTINGS_KEY, seen)

@Composable
fun PermissionPolicy.displayName(): String = stringResource(
    when (this) {
        PermissionPolicy.AskIfExfiltrating -> Res.string.permissions_policy_ask_if_exfiltrating
        PermissionPolicy.AlwaysAsk -> Res.string.permissions_policy_always_ask
        PermissionPolicy.NeverAsk -> Res.string.permissions_policy_never_ask
    }
)

fun PermissionPolicy.description(): StringResource = when (this) {
    PermissionPolicy.AskIfExfiltrating -> Res.string.permissions_policy_ask_if_exfiltrating_description
    PermissionPolicy.AlwaysAsk -> Res.string.permissions_policy_always_ask_description
    PermissionPolicy.NeverAsk -> Res.string.permissions_policy_never_ask_description
}

@Composable
private fun PermissionPolicy.details(): String = stringResource(
    when (this) {
        PermissionPolicy.AskIfExfiltrating -> Res.string.permissions_policy_ask_if_exfiltrating_details
        PermissionPolicy.AlwaysAsk -> Res.string.permissions_policy_always_ask_details
        PermissionPolicy.NeverAsk -> Res.string.permissions_policy_never_ask_details
    }
)

/** Every mode, for the setting's info popup. */
@Composable
fun PermissionPolicyDetails() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            stringResource(Res.string.permissions_policy_details_intro),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PermissionPolicy.entries.forEach { policy ->
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    if (policy == PermissionPolicy.AskIfExfiltrating) stringResource(Res.string.permissions_policy_default, policy.displayName()) else policy.displayName(),
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
    val hasWatch = !rememberWatches().isNullOrEmpty()
    val indexEnabled = koinInject<CoreConfigHolder>().config.collectAsState().value.enableIndex
    val body = when {
        hasWatch && indexEnabled -> Res.string.permissions_intro_body_watch_and_index
        indexEnabled -> Res.string.permissions_intro_body_index
        else -> Res.string.permissions_intro_body_watch
    }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(body),
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            stringResource(Res.string.permissions_intro_change_mode),
            style = MaterialTheme.typography.bodySmall,
        )
        if (hasWatch) {
            Text(
                stringResource(Res.string.permissions_intro_older_apps),
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
        title = { Text(stringResource(Res.string.permissions_intro_title)) },
        text = {
            val settings = rememberSettingsItemsState(navBarNav = null, snackbarDisplay = {})
            PermissionsIntroContent(settings, Modifier.verticalScroll(rememberScrollState()))
        },
        confirmButton = { TextButton(onClick = onDismissRequest) { Text(stringResource(UtilRes.string.ok)) } },
    )
}
