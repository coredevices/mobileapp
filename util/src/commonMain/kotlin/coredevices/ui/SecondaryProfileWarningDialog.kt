package coredevices.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwitchAccount
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.russhwolf.settings.Settings
import coredevices.util.CoreConfig
import coredevices.util.CoreConfigHolder
import coredevices.util.SecondaryProfileWarning
import kotlinx.serialization.json.Json
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.KoinApplicationPreview
import org.koin.compose.koinInject
import org.koin.dsl.module
import coreapp.util.generated.resources.Res
import coreapp.util.generated.resources.misc_secondary_profile_body
import coreapp.util.generated.resources.misc_secondary_profile_body_bold
import coreapp.util.generated.resources.misc_secondary_profile_disable_sync
import coreapp.util.generated.resources.misc_secondary_profile_keep_sync
import coreapp.util.generated.resources.misc_secondary_profile_title
import org.jetbrains.compose.resources.stringResource

/** One-time prompt shown when the app runs in a secondary profile. Marks the warning seen on any
 *  dismissal, and turns on [coredevices.util.CoreConfig.disableRingBluetoothSync] if the user asks. */
@Composable
fun SecondaryProfileWarningDialog(onDone: () -> Unit) {
    val warning = koinInject<SecondaryProfileWarning>()
    val coreConfigHolder = koinInject<CoreConfigHolder>()
    fun dismiss(disableSync: Boolean) {
        if (disableSync) {
            coreConfigHolder.update(coreConfigHolder.config.value.copy(disableRingBluetoothSync = true))
        }
        warning.markSeen()
        onDone()
    }
    M3Dialog(
        properties = DialogProperties(dismissOnClickOutside = false, dismissOnBackPress = false),
        onDismissRequest = { dismiss(disableSync = false) },
        icon = { Icon(Icons.Default.SwitchAccount, null) },
        title = { Text(stringResource(Res.string.misc_secondary_profile_title)) },
        verticalButtons = {
            Button(onClick = { dismiss(disableSync = true) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(Res.string.misc_secondary_profile_disable_sync))
            }
            OutlinedButton(onClick = { dismiss(disableSync = false) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(Res.string.misc_secondary_profile_keep_sync))
            }
        },
    ) {
        val bold = stringResource(Res.string.misc_secondary_profile_body_bold)
        val body = stringResource(Res.string.misc_secondary_profile_body, bold)
        Text(
            buildAnnotatedString {
                append(body)
                val start = body.indexOf(bold)
                if (start >= 0) addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, start + bold.length)
            }
        )
    }
}