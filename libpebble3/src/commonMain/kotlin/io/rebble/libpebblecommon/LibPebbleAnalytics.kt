package io.rebble.libpebblecommon

import io.rebble.libpebblecommon.connection.AnalyticsEvent
import io.rebble.libpebblecommon.connection.AnalyticsEvents
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

interface LibPebbleAnalytics {
    fun logEvent(name: String, parameters: Map<String, String> = emptyMap())
    fun incrementHeartbeatCounter(name: String)
}

class RealLibPebbleAnalytics : LibPebbleAnalytics, AnalyticsEvents {
    private val _events = MutableSharedFlow<AnalyticsEvent>(extraBufferCapacity = 10)

    override fun logEvent(
        name: String,
        parameters: Map<String, String>
    ) {
        _events.tryEmit(AnalyticsEvent(name, parameters))
    }

    override val analyticsEvents: Flow<AnalyticsEvent> = _events.asSharedFlow()

    private val _heartbeatCounterIncrements = MutableSharedFlow<String>(extraBufferCapacity = 64)

    override fun incrementHeartbeatCounter(name: String) {
        _heartbeatCounterIncrements.tryEmit(name)
    }

    override val heartbeatCounterIncrements: Flow<String> = _heartbeatCounterIncrements.asSharedFlow()
}