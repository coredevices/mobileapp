package coredevices.pebble.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import co.touchlab.kermit.Logger
import coredevices.pebble.account.FirestoreLocker
import coredevices.pebble.rememberLibPebble
import coredevices.pebble.services.RealPebbleWebServices
import coredevices.ui.M3Dialog
import coredevices.util.CoreConfig
import kotlinx.coroutines.launch
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.koinInject

private val logger = Logger.withTag("LockerImportDialog")

@Composable
fun LockerImportDialog(
    onDismissRequest: () -> Unit,
    isRebble: Boolean,
    topBarParams: TopBarParams?,
    onEnabled: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(-1f) }
    val libPebble = rememberLibPebble()
    val firestoreLocker: FirestoreLocker = koinInject()
    val webServices = koinInject<RealPebbleWebServices>()
    M3Dialog(
        onDismissRequest = {
            if (!loading) {
                onDismissRequest()
            }
        },
        icon = { Icon(Icons.Default.Apps, contentDescription = null) },
        title = {
            if (loading) {
                Text("Import in progress...")
            } else {
                Text("Import Apps & Faces?")
            }
        },
        buttons = {
            TextButton(
                onClick = {
                    onDismissRequest()
                },
                enabled = !loading,
            ) {
                Text("Skip")
            }
            TextButton(
                onClick = {
                    scope.launch {
                        loading = true
                        try {
                            firestoreLocker.importPebbleLocker(webServices, "https://appstore-api.rebble.io/api").collect {
                                progress = it.first.toFloat() / it.second.toFloat()
                            }
                            progress = -1f
                            libPebble.requestLockerSync().await()
                            onEnabled()
                        } catch (e: Exception) {
                            logger.e(e) { "Error importing locker from pebble account: ${e.message}" }
                            topBarParams?.showSnackbar("Error importing locker")
                        }
                        onDismissRequest()
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
                            .height(75.dp)
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (progress < 0f) {
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                        } else {
                            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                        }
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
            isRebble = true,
            topBarParams = koinInject(),
            onEnabled = {},
        )
    }
}

@Preview
@Composable
fun LockerImportDialogNotRebblePreview() {
    PreviewWrapper {
        LockerImportDialog(
            onDismissRequest = {},
            isRebble = false,
            topBarParams = koinInject(),
            onEnabled = {},
        )
    }
}