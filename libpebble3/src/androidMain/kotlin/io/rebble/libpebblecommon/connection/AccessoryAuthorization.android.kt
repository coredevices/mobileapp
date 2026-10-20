package io.rebble.libpebblecommon.connection

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

private val noAccessoryAuthorizations = MutableStateFlow<Set<PebbleBleIdentifier>>(emptySet())
actual fun accessoryAuthorizationFlow(): StateFlow<Set<PebbleBleIdentifier>> = noAccessoryAuthorizations

actual fun accessoryAuthorizationRequired(): Boolean = false
