package coredevices.ring.service.recordings.button

import coredevices.ring.service.PlatformIndexNotificationManager
import coredevices.ring.service.copiedToClipboardNotification
import coredevices.ring.service.recordings.RecordingProcessingQueue
import coredevices.util.SystemClipboard
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Decorator that puts the transcript of [transcribing] on the system clipboard.
 * A blank transcript leaves the clipboard as it was.
 * [onCopied] runs after each copy; by default it posts a notification saying so.
 */
internal class CopyToClipboardRecordingOperation(
    private val transcribing: TranscribingRecordingOperation,
    private val clipboard: SystemClipboard,
    onCopied: ((transcription: String) -> Unit)? = null,
) : TranscribingRecordingOperation, KoinComponent {

    override var onTranscriptionPersisted: (suspend (transcription: String) -> Unit)? = null

    private val notifications: PlatformIndexNotificationManager by inject()
    private val onCopied = onCopied ?: { notifications.notify(copiedToClipboardNotification(it)) }

    override suspend fun run(handle: RecordingProcessingQueue.TaskHandle?) {
        transcribing.onTranscriptionPersisted = { transcription ->
            if (transcription.isNotBlank()) {
                clipboard.setText(transcription)
                onCopied(transcription)
            }
            onTranscriptionPersisted?.invoke(transcription)
        }
        transcribing.run(handle)
    }
}
