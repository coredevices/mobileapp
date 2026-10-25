package io.rebble.libpebblecommon.plugin

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/** What plugins log, streamed to a developer connection beside PKJS's logs (`pebble logs`). */
class PluginLogs {
    private val _messages = MutableSharedFlow<String>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val messages: Flow<String> = _messages

    fun log(plugin: Plugin, message: String) {
        _messages.tryEmit("$MARKER${plugin.name}: $message")
    }

    companion object {
        /** The connection carries one kind of phone log; pebble-tool shows lines starting with this as `plugin>`. */
        const val MARKER = "[plugin] "
    }
}
