package coredevices.ring.storage

import coredevices.util.sync.SyncHandler

class RecordingUploadSyncHandler(
    private val recordingStorage: RecordingStorage,
) : SyncHandler {
    companion object {
        const val TYPE = "recording_upload"
    }

    override val type: String = TYPE

    override suspend fun sync(payload: String) {
        recordingStorage.uploadStagedRecording(StagedRecordingUpload.decode(payload))
    }

    override suspend fun onFailed(payload: String) {
        recordingStorage.deleteStagedRecording(StagedRecordingUpload.decode(payload).id)
    }
}
