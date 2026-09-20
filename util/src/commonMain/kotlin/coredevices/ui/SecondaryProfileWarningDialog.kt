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
import androidx.compose.ui.text.withStyle
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
        title = { Text("Secondary Profile") },
        verticalButtons = {
            Button(onClick = { dismiss(disableSync = true) }, modifier = Modifier.fillMaxWidth()) {
                Text("Disable sync")
            }
            OutlinedButton(onClick = { dismiss(disableSync = false) }, modifier = Modifier.fillMaxWidth()) {
                Text("Keep sync on")
            }
        },
    ) {
        Text(
            buildAnnotatedString {
                append("This app is running in a secondary profile on this phone, such as a work profile, " +
                        "Secure Folder, or Private Space. If you already have the app set up in another profile, ")
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append("this will break communication with your Index 01.")
                }
                append("\n\nDo you want to disable Bluetooth sync for this profile to avoid conflicts?")
            }
        )
    }
}