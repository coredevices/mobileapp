package coredevices.ring.service

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * Restarts scan if we get side-effect info that the ring should be detected,
 * e.g. CDM presence
 */
class RingScanWatchdog(
    private val advertisements: Flow<*>,
    private val hadRecentConnection: () -> Boolean,
    private val restartScan: () -> Unit,
    private val timeSource: TimeSource = TimeSource.Monotonic,
) {
    private val logger = Logger.withTag("RingScanWatchdog")
    private var scope: CoroutineScope? = null
    private var lastAdvertisement: TimeMark? = null
    private var check: Job? = null

    fun start(scope: CoroutineScope) {
        this.scope = scope
        advertisements.onEach { lastAdvertisement = timeSource.markNow() }.launchIn(scope)
    }

    /**
     * Returns the check started for this event, which completes once the scan is known to be
     * alive or a restart has been confirmed or given up on; null if not started or one is already
     * running.
     */
    fun onRingPresent(): Job? {
        val scope = scope ?: return null
        if (check?.isActive == true) return null
        return scope.launch { checkScanAlive() }.also { check = it }
    }

    private suspend fun checkScanAlive() {
        if (advertisedRecently()) return
        withTimeoutOrNull(ADVERTISEMENT_WAIT) { advertisements.first() }
        if (advertisedRecently()) return
        if (hadRecentConnection()) {
            logger.v { "Ring present but scan silent; recent connection, leaving scan alone" }
            return
        }
        logger.w { "Ring present but scan silent for ${lastAdvertisement?.elapsedNow() ?: "ever"}; restarting scan" }
        val restartedAt = timeSource.markNow()
        restartScan()
        val confirmed = withTimeoutOrNull(RESTART_CONFIRMATION_WAIT) { advertisements.first() } != null
        if (confirmed) {
            logger.i { "Scan restarted; first advertisement after ${restartedAt.elapsedNow()}" }
        } else {
            logger.w { "Scan restarted; no advertisement within $RESTART_CONFIRMATION_WAIT" }
        }
    }

    private fun advertisedRecently(): Boolean =
        lastAdvertisement?.let { it.elapsedNow() < RECENT_ADVERTISEMENT_WINDOW } == true

    private companion object {
        val RECENT_ADVERTISEMENT_WINDOW = 3.seconds
        val ADVERTISEMENT_WAIT = 1.seconds
        val RESTART_CONFIRMATION_WAIT = 5.seconds
    }
}
