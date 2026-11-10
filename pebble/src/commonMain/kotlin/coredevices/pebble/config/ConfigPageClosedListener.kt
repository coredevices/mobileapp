package coredevices.pebble.config

import kotlin.uuid.Uuid

/** Told whenever a config page closes, however it was opened. Bound by the app. */
fun interface ConfigPageClosedListener {
    fun onConfigPageClosed(uuid: Uuid)
}
