package coredevices.ring.util

import androidx.compose.runtime.Composable

@Composable
expect fun rememberPermissionRequestLauncher(onResult: (Map<String, Boolean>) -> Unit): (input: List<String>) -> Unit