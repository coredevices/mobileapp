package coredevices.pebble.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coredevices.ui.M3Dialog
import kotlinx.coroutines.launch
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
fun LockerImportDialog(
    onDismissRequest: () -> Unit,
    onImportFromPebbleAccount: suspend () -> Unit,
    onStartFresh: () -> Unit,
    isRebble: Boolean
) {
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(false) }
    M3Dialog(
        onDismissRequest = onDismissRequest,
        icon = { Icon(Icons.Default.Apps, contentDescription = null) },
        title = { Text("Import Apps & Faces?") },
        buttons = {
            TextButton(
                onClick = {
                    onStartFresh()
                },
                enabled = !loading,
            ) {
                Text("Skip")
            }
            TextButton(
                onClick = {
                    scope.launch {
                        loading = true
                        onImportFromPebbleAccount()
                    }
                },
                enabled = !loading,
            ) {
                Text("Import")
            }
        },
        contents = {
            AnimatedContent(
                loading
            ) {
                if (loading) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(75.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    Text(
                        "Would you like to import your existing apps & faces from your " +
                                "${if (isRebble) "Rebble" else "Web Services"} account?",
                    )
                }
            }
        }
    )
}

@Composable
private fun LockerImportOption(
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                onClick()
            }
            .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.medium)
            .padding(8.dp)
    ) {
        content()
    }
}

@Preview
@Composable
fun LockerImportDialogPreview() {
    PreviewWrapper {
        LockerImportDialog(
            onDismissRequest = {},
            onImportFromPebbleAccount = {},
            onStartFresh = {},
            isRebble = true,
        )
    }
}

@Preview
@Composable
fun LockerImportDialogNotRebblePreview() {
    PreviewWrapper {
        LockerImportDialog(
            onDismissRequest = {},
            onImportFromPebbleAccount = {},
            onStartFresh = {},
            isRebble = false,
        )
    }
}