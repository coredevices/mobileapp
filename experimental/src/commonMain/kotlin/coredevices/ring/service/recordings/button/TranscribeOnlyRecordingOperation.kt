package coredevices.ring.service.recordings.button

import coredevices.ring.service.recordings.RecordingProcessingQueue
import coredevices.ring.service.recordings.RecordingProcessor
import coredevices.ring.storage.RecordingStorage
import coredevices.util.queue.RecoverableTaskException
import coredevices.util.transcription.TranscriptionException
import coredevices.util.transcription.TranscriptionSessionStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Transcribes a recording purely so the webhook payload can carry the transcript.
 * No recording entry is created and no agent runs.
 */
internal class TranscribeOnlyRecordingOperation(
    private val fileId: String,
) : TranscribingRecordingOperation, KoinComponent {

    override var onTranscriptionPersisted: (suspend (transcription: String) -> Unit)? = null

    private val recordingStorage: RecordingStorage by inject()
    private val recordingProcessor: RecordingProcessor by inject()

    override suspend fun run(handle: RecordingProcessingQueue.TaskHandle?) {
        val (source, meta) = recordingStorage.openRecordingSource(fileId)
        val transcription = try {
            withTimeoutOrNull(RecordingProcessor.TRANSCRIPTION_TIMEOUT) {
                recordingProcessor.transcribe(
                    audioSource = source,
                    sampleRate = meta.cachedMetadata.sampleRate,
                ).flowOn(Dispatchers.IO)
                    .first { it is TranscriptionSessionStatus.Transcription } as TranscriptionSessionStatus.Transcription
            } ?: throw TranscriptionException.TranscriptionServiceError(
                "Transcription timed out after ${RecordingProcessor.TRANSCRIPTION_TIMEOUT}"
            )
        } catch (e: TranscriptionException.TranscriptionNetworkError) {
            throw RecoverableTaskException("Network error during transcription", e)
        } catch (e: TranscriptionException.TranscriptionInProgress) {
            throw RecoverableTaskException("Transcription model busy", e)
        } finally {
            source.close()
        }
        onTranscriptionPersisted?.invoke(transcription.text)
    }
}
