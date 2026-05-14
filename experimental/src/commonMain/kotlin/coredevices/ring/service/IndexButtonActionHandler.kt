package coredevices.ring.service

import co.touchlab.kermit.Logger
import coredevices.ring.database.MusicControlMode
import coredevices.ring.database.Preferences

class IndexButtonActionHandler(prefs: Preferences, sequenceRecorder: IndexButtonSequenceRecorder) {
    companion object {
        private val logger = Logger.withTag("IndexButtonActionHandler")
    }
    private val sequenceEvents = sequenceRecorder.sequenceEvents()

    private val actions = mapOf<List<ButtonPress>, suspend () -> Unit>(
        listOf(ButtonPress.Short) to {
            if (prefs.musicControlMode.value == MusicControlMode.SingleClick) {
                onPlayPause()
            }
        },
        listOf(ButtonPress.Short, ButtonPress.Short) to {
            when (prefs.musicControlMode.value) {
                MusicControlMode.DoubleClick -> onPlayPause()
                MusicControlMode.SingleClick -> onNextTrack()
                else -> {}
            }
        },
        listOf(ButtonPress.Short, ButtonPress.Short, ButtonPress.Short) to {
            if (prefs.musicControlMode.value == MusicControlMode.DoubleClick) {
                onNextTrack()
            }
        },
    )

    suspend fun handleButtonActions() {
        sequenceEvents.collect { buttonPresses ->
            val action = actions[buttonPresses]
            action?.invoke()?.let {
                logger.i("Handled button action for sequence: ${buttonPresses.joinToString(", ") { it.name }}")
            }
        }
    }
}