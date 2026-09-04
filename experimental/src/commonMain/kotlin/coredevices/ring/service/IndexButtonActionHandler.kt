package coredevices.ring.service

import co.touchlab.kermit.Logger
import coredevices.ring.service.button.GestureDestination
import coredevices.ring.service.button.GestureKind
import coredevices.ring.service.button.GestureRoutingPreferences
import coredevices.ring.service.button.RingGesture
import coredevices.ring.service.button.repeatedDoubleClickCount
import kotlinx.coroutines.delay

class IndexButtonActionHandler(
    private val gestureRouting: GestureRoutingPreferences,
    sequenceRecorder: IndexButtonSequenceRecorder,
) {
    companion object {
        private val logger = Logger.withTag("IndexButtonActionHandler")
        private const val REPEAT_DISPATCH_SPACING_MS = 200L
    }
    private val sequenceEvents = sequenceRecorder.sequenceEvents()

    suspend fun handleButtonActions() {
        sequenceEvents.collect { buttonPresses ->
            val gesture = RingGesture.forSequence(buttonPresses)
                ?.takeIf { it.kind == GestureKind.Music }
            if (gesture == null) {
                handleRepeatedDoubleClicks(buttonPresses)
                return@collect
            }
            when (gestureRouting.destinationFor(gesture)) {
                GestureDestination.PlayPause -> onPlayPause()
                GestureDestination.NextTrack -> onNextTrack()
                GestureDestination.PreviousTrack -> onPreviousTrack()
                else -> return@collect
            }
            logger.i("Handled button action for sequence: ${buttonPresses.joinToString(", ") { it.name }}")
        }
    }

    // Only directional skips repeat safely; play/pause would toggle.
    private suspend fun handleRepeatedDoubleClicks(buttonPresses: List<ButtonPress>) {
        val repeats = repeatedDoubleClickCount(buttonPresses)
        if (repeats == 0) return
        val action = when (gestureRouting.destinationFor(RingGesture.DoubleClick)) {
            GestureDestination.NextTrack -> ::onNextTrack
            GestureDestination.PreviousTrack -> ::onPreviousTrack
            else -> return
        }
        repeat(repeats) {
            action()
            delay(REPEAT_DISPATCH_SPACING_MS)
        }
        logger.i("Handled $repeats stacked double clicks from ${buttonPresses.size} presses")
    }
}
