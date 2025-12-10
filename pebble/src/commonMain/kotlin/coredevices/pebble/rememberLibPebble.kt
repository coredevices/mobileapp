package coredevices.pebble

import androidx.compose.runtime.Composable
import io.rebble.libpebblecommon.connection.LibPebble
import org.koin.compose.koinInject

@Composable
fun rememberLibPebble(): LibPebble {
    val libPebble = koinInject<LibPebble>()
    return libPebble
}
