package coredevices.ring.agent.builtin_servlets.clock

import coredevices.ring.clock.LocalClock
import org.koin.mp.KoinPlatform

actual suspend fun setAlarm(hours: Int, minutes: Int, label: String?) {
    KoinPlatform.getKoin().get<LocalClock>().setAlarm(hours, minutes, label)
}
