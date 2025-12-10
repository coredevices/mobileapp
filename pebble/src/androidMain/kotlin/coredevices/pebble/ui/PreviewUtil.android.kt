package coredevices.pebble.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import io.rebble.libpebblecommon.connection.AppContext

@Composable
actual fun fakeAppContext(): AppContext {
    val anroidContext = LocalContext.current
    val context = remember { AppContext(anroidContext.applicationContext) }
    return context
}
