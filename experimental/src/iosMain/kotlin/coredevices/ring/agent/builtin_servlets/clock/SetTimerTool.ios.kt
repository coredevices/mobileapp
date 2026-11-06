package coredevices.ring.agent.builtin_servlets.clock

import coredevices.ring.clock.LocalClock
import org.koin.mp.KoinPlatform
import kotlin.time.Duration

actual suspend fun setTimer(duration: Duration, title: String?, skipUI: Boolean) {
    KoinPlatform.getKoin().get<LocalClock>().setTimer(duration, title)
}
