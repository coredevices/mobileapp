package coredevices.ring.service

import co.touchlab.kermit.Logger
import coredevices.ring.external.indexwebhook.IndexWebhookApi
import coredevices.ring.service.button.GestureDestination
import coredevices.ring.service.button.GestureRoutingPreferences
import coredevices.ring.service.button.RingGesture
import coredevices.ring.service.recordings.RecordingProcessingQueue
import kotlinx.coroutines.CancellationException
import kotlin.time.Clock

class IndexButtonActionHandler(
    private val gestureRouting: GestureRoutingPreferences,
    sequenceRecorder: IndexButtonSequenceRecorder,
    private val webhookApi: IndexWebhookApi,
    private val recordingQueue: RecordingProcessingQueue,
) {
    companion object {
        private val logger = Logger.withTag("IndexButtonActionHandler")
    }
    private val sequenceEvents = sequenceRecorder.sequenceEvents()

    suspend fun handleButtonActions() {
        sequenceEvents.collect { handleSequence(it) }
    }

    private suspend fun handleSequence(buttonPresses: List<ButtonPress>) {
        val action = gestureRouting.buttonActionFor(buttonPresses) ?: return
        try {
            when (action) {
                ButtonAction.PlayPause -> onPlayPause()
                ButtonAction.NextTrack -> onNextTrack()
                ButtonAction.PreviousTrack -> onPreviousTrack()
                is ButtonAction.Webhook -> webhookApi.sendButtonEventIfEnabled(action.gesture, Clock.System.now())
                is ButtonAction.AgentCommand -> recordingQueue.queueTextProcessing(
                    action.command,
                    buttonSequence = action.buttonSequence,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.e(e) { "Failed to handle button action" }
            return
        }
        logger.i("Handled button action for sequence: ${buttonPresses.joinToString(", ") { it.name }}")
    }
}

sealed interface ButtonAction {
    data object PlayPause : ButtonAction
    data object NextTrack : ButtonAction
    data object PreviousTrack : ButtonAction
    data class Webhook(val gesture: RingGesture) : ButtonAction
    data class AgentCommand(val command: String, val buttonSequence: String) : ButtonAction
}

private val routingLogger = Logger.withTag("IndexButtonActionHandler")

/** What a click sequence should do under the current routes, or null when nothing. */
internal fun GestureRoutingPreferences.buttonActionFor(buttonPresses: List<ButtonPress>): ButtonAction? {
    val gesture = RingGesture.forSequence(buttonPresses)?.takeIf { it.isClick } ?: return null
    return when (destinationFor(gesture)) {
        GestureDestination.PlayPause -> ButtonAction.PlayPause
        GestureDestination.NextTrack -> ButtonAction.NextTrack
        GestureDestination.PreviousTrack -> ButtonAction.PreviousTrack
        GestureDestination.Webhook -> ButtonAction.Webhook(gesture)
        GestureDestination.AgentCommand -> {
            val command = commandFor(gesture).trim()
            if (command.isEmpty()) {
                routingLogger.w { "$gesture is routed to an agent command but none is set" }
                return null
            }
            ButtonAction.AgentCommand(
                command = command,
                buttonSequence = buttonPresses.joinToString(" ") { it.name.lowercase() },
            )
        }
        else -> null
    }
}
