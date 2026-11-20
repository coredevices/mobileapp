package io.rebble.libpebblecommon.io.rebble.libpebblecommon.util

import io.rebble.libpebblecommon.connection.OtherPebbleApp
import io.rebble.libpebblecommon.connection.OtherPebbleApps
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

internal class OtherPebbleIosApps : OtherPebbleApps {
    override fun otherPebbleCompanionAppsInstalled(): StateFlow<List<OtherPebbleApp>> =
        MutableStateFlow(emptyList())
}