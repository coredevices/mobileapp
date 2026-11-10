package coredevices.pebble.config

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.uuid.Uuid

/** The app whose config page is on screen: its permission requests are asked there, not by notification. */
class VisibleConfigPage {
    private val _app = MutableStateFlow<Uuid?>(null)
    val app: StateFlow<Uuid?> = _app.asStateFlow()

    fun shown(uuid: Uuid) {
        _app.value = uuid
    }

    fun hidden(uuid: Uuid) {
        _app.compareAndSet(uuid, null)
    }
}
