package coredevices.ring.util

import androidx.compose.runtime.Composable

@Composable
actual fun rememberPermissionRequestLauncher(onResult: (Map<String, Boolean>) -> Unit): (input: List<String>) -> Unit {
    return {
        onResult(it.associateWith { true })
    }
}