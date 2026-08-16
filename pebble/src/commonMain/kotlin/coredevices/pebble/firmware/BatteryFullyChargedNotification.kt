package coredevices.pebble.firmware

import co.touchlab.kermit.Logger
import coredevices.util.CoreConfigFlow
import io.rebble.libpebblecommon.connection.AppContext
import io.rebble.libpebblecommon.connection.PebbleIdentifier

interface BatteryChargedNotifier {
    fun onBatteryLevel(identifier: PebbleIdentifier, watchName: String, level: Int?)
}

class RealBatteryChargedNotifier(
    private val coreConfigFlow: CoreConfigFlow,
    private val notify: (watchName: String) -> Unit,
) : BatteryChargedNotifier {
    private val logger = Logger.withTag("BatteryChargedNotifier")
    private val lastLevels = mutableMapOf<PebbleIdentifier, Int?>()
    private val notified = mutableSetOf<PebbleIdentifier>()

    override fun onBatteryLevel(identifier: PebbleIdentifier, watchName: String, level: Int?) {
        if (!coreConfigFlow.value.notifyWatchFullyCharged) return
        val previous = lastLevels.put(identifier, level)
        if (level == null) return
        if (level <= RENOTIFY_LEVEL) {
            notified.remove(identifier)
        }
        // A null previous level means we've only just started watching this device - stay quiet
        // rather than notifying for a watch that was already full when we connected.
        if (previous == null || previous >= FULL_LEVEL || level < FULL_LEVEL) return
        if (!notified.add(identifier)) return
        logger.d { "$watchName reached full charge" }
        notify(watchName)
    }

    companion object {
        private const val FULL_LEVEL = 100

        // Latch stays set between these two, so topping up doesn't re-notify on every blip
        // back to 100%.
        private const val RENOTIFY_LEVEL = 97
    }
}

expect fun postWatchFullyChargedNotification(appContext: AppContext, watchName: String)
