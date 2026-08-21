package coredevices.ring.service

import co.touchlab.kermit.Logger
import coredevices.ring.service.button.GestureDestination
import coredevices.ring.service.button.GestureKind
import coredevices.ring.service.button.GestureRoutingPreferences
import coredevices.ring.service.button.RingGesture

class IndexButtonActionHandler(
    private val gestureRouting: GestureRoutingPreferences,
    sequenceRecorder: IndexButtonSequenceRecorder,
) {
    companion object {
        private val logger = Logger.withTag("IndexButtonActionHandler")
    }
    private val sequenceEvents = sequenceRecorder.sequenceEvents()

    suspend fun handleButtonActions() {
        sequenceEvents.collect { buttonPresses ->
            val gesture = RingGesture.forSequence(buttonPresses)
                ?.takeIf { it.kind == GestureKind.Music }
                ?: return@collect
            when (gestureRouting.destinationFor(gesture)) {
                GestureDestination.PlayPause -> onPlayPause()
                GestureDestination.NextTrack -> onNextTrack()
                else -> return@collect
            }
            logger.i("Handled button action for sequence: ${buttonPresses.joinToString(", ") { it.name }}")
        }
    }
}
