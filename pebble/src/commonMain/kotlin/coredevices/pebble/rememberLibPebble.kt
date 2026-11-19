package coredevices.pebble

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import io.rebble.libpebblecommon.connection.LibPebble
import io.rebble.libpebblecommon.connection.PebbleDevice
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.compose.koinInject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@Composable
fun rememberLibPebble(): LibPebble {
    val libPebble = koinInject<LibPebble>()
    return libPebble
}

/** Shared so screens get the current watches on their first frame. */
class UiWatches(libPebble: LibPebble) {
    val watches: StateFlow<List<PebbleDevice>?> =
        libPebble.watches.stateIn(GlobalScope, SharingStarted.Lazily, null)
}

/** On iOS watches don't load until onboarding is done, so don't wait forever. */
suspend fun LibPebble.watchesOrEmpty(timeout: Duration = 2.seconds): List<PebbleDevice> =
    withTimeoutOrNull(timeout) { watches.first() }.orEmpty()

/** Null until known watches have loaded. */
@Composable
fun rememberWatches(): List<PebbleDevice>? = koinInject<UiWatches>().watches.collectAsState().value
