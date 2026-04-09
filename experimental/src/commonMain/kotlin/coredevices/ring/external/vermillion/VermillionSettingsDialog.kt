package coredevices.ring.external.vermillion

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun VermillionSettingsDialog(
    viewModel: VermillionSettingsViewModel = koinViewModel()
) {
    val tokenInput by viewModel.tokenInput.collectAsState()
    val token by viewModel.token.collectAsState()
    val uriHandler = LocalUriHandler.current

    BasicAlertDialog(
        onDismissRequest = viewModel::closeDialog,
    ) {
        Surface(
            modifier = Modifier.wrapContentWidth().wrapContentHeight(),
            shape = MaterialTheme.shapes.large,
            tonalElevation = AlertDialogDefaults.TonalElevation
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Vermillion Widget Token",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Enter your widget token to sync recordings to Vermillion",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Get your token",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.primary,
                        textDecoration = TextDecoration.Underline
                    ),
                    modifier = Modifier.clickable {
                        uriHandler.openUri("https://vermillion.ai/debug?view=widget-token")
                    }
                )
                Spacer(modifier = Modifier.height(16.dp))
                TextField(
                    value = tokenInput,
                    onValueChange = viewModel::updateTokenInput,
                    minLines = 3,
                    maxLines = 5,
                    label = { Text("Widget Token") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(24.dp))
                Row(modifier = Modifier.align(Alignment.End)) {
                    if (token != null) {
                        TextButton(onClick = viewModel::clearToken) {
                            Text("Unlink")
                        }
                        Spacer(modifier = Modifier.weight(1f))
                    }
                    TextButton(onClick = viewModel::closeDialog) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = viewModel::saveToken) {
                        Text("Save")
                    }
                }
            }
        }
    }
}
