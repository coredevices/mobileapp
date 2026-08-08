package coredevices.ring.service

import co.touchlab.kermit.Logger
import coredevices.ring.data.entity.room.ClickAction
import coredevices.ring.data.entity.room.ClickActionBinding
import coredevices.ring.service.recordings.RecordingProcessingQueue

/** Runs the action bound to a custom click gesture. */
class ClickActionExecutor(
    private val recordingProcessingQueue: RecordingProcessingQueue,
) {
    companion object {
        private val logger = Logger.withTag("ClickActionExecutor")
    }

    suspend fun execute(binding: ClickActionBinding) {
        when (val action = binding.action) {
            is ClickAction.AgentText -> recordingProcessingQueue.queueTextProcessing(action.text)
            ClickAction.Unsupported ->
                logger.w { "${binding.clickCount} clicks is bound to an unreadable action; ignoring" }
        }
    }
}
