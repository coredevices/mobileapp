package coredevices.ring.agent.builtin_servlets.clock

import kotlin.time.Duration

actual suspend fun setTimer(duration: Duration, title: String?, skipUI: Boolean) {
    error("Setting timers is not supported in iOS.")
}