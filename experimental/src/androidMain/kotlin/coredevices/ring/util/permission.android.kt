package coredevices.ring.util

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable

@Composable
actual fun rememberPermissionRequestLauncher(onResult: (Map<String, Boolean>) -> Unit): (input: List<String>) -> Unit {
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            onResult(it)
        }
    return { permission ->
        launcher.launch(permission.toTypedArray())
    }
}