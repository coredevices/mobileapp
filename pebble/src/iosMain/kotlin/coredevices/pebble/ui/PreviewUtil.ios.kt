package coredevices.pebble.ui

import androidx.compose.runtime.Composable
import io.rebble.libpebblecommon.connection.AppContext

@Composable
actual fun fakeAppContext(): AppContext {
    return AppContext()
}