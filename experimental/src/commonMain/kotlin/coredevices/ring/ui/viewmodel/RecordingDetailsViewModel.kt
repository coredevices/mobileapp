package coredevices.ring.ui.viewmodel

import PlatformUiContext
import androidx.compose.material3.SnackbarHostState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import coredevices.indexai.data.entity.ConversationMessageEntity
import coredevices.indexai.data.entity.LocalRecording
import coredevices.indexai.data.entity.RecordingEntryEntity
import coredevices.indexai.database.dao.ConversationMessageDao
import coredevices.ring.database.Preferences
import coredevices.libindex.database.dao.RingTransferDao
import coredevices.ring.data.entity.room.indexfeed.CachedItem
import coredevices.ring.data.entity.room.indexfeed.CachedList
import coredevices.ring.database.room.repository.ItemRepository
import coredevices.ring.database.room.repository.ListRepository
import coredevices.ring.database.room.repository.RecordingRepository
import coredevices.ring.service.recordings.RecordingProcessingQueue
import coredevices.ring.storage.RecordingStorage
import coredevices.ring.util.AudioPlayer
import coredevices.ring.util.PlaybackState
import coredevices.util.AudioEncoding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.io.files.Path

class RecordingDetailsViewModel(
    private val recordingId: Long,
    private val recordingRepo: RecordingRepository,
    private val conversationMessageDao: ConversationMessageDao,
    private val recordingStorage: RecordingStorage,
    private val audioPlayer: AudioPlayer,
    private val snackbarHostState: SnackbarHostState,
    private val uiContext: PlatformUiContext,
    private val prefs: Preferences,
    private val recordingProcessingQueue: RecordingProcessingQueue,
    private val ringTransferDao: RingTransferDao,
    private val itemRepo: ItemRepository,
    private val listRepo: ListRepository,
): ViewModel() {
    companion object {
        private val logger = Logger.withTag(RecordingDetailsViewModel::class.simpleName!!)
    }
    sealed class ItemState {
        data object Loading: ItemState()
        data object Error: ItemState()
        data class Loaded(val recording: LocalRecording, val entries: List<RecordingEntryEntity>, val messages: List<ConversationMessageEntity>): ItemState()
    }
    val itemState = combine(
        recordingRepo.getRecordingFlow(recordingId),
        recordingRepo.getRecordingEntriesFlow(recordingId),
        conversationMessageDao.getMessagesForRecording(recordingId)
    ) { records ->
        val recording = records[0] as LocalRecording?
        val entries = records[1] as List<RecordingEntryEntity>
        val messages = records[2] as List<ConversationMessageEntity>
        if (recording != null) {
            ItemState.Loaded(
                recording,
                entries,
                messages
            )
        } else {
            ItemState.Error
        }
    }.stateIn(
        viewModelScope,
        started = kotlinx.coroutines.flow.SharingStarted.Lazily,
        initialValue = ItemState.Loading
    )

    val showDebugDetails = prefs.debugDetailsEnabled

    /** All extracted items that point at this recording (by Firestore id or
     *  `local:<roomId>` fallback). Drives the action-chips row in the
     *  reskinned RecordingDetail. */
    val linkedItems: StateFlow<List<CachedItem>> = combine(
        recordingRepo.getRecordingFlow(recordingId),
        itemRepo.getAllFlow(),
    ) { rec, items ->
        if (rec == null) emptyList()
        else {
            val keys = listOfNotNull(rec.firestoreId?.takeIf { it.isNotBlank() }, "local:${rec.id}")
            items.filter { !it.deleted && it.sourceRecordingId in keys }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** All non-deleted lists, used to resolve note-item parent names for
     *  the chip labels. */
    val allLists: StateFlow<List<CachedList>> = listRepo.getAllFlow()
        .map { ls -> ls.filter { !it.deleted } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Audio duration in seconds, computed lazily from the recording's
     *  first entry's file. `null` until loaded; stays `null` if the file
     *  isn't on disk. */
    val durationSeconds = MutableStateFlow<Float?>(null)

    val moreMenuExpanded = MutableStateFlow(false)
    val playbackState = MutableStateFlow<MessagePlaybackState>(MessagePlaybackState.Stopped)
    val showTraceTimeline = MutableStateFlow(false)

    fun toggleTraceTimeline() {
        showTraceTimeline.value = !showTraceTimeline.value
    }

    init {
        playbackState.drop(1).onEach {
            logger.d { "Playback state changed: $it" }
        }.launchIn(viewModelScope)

        // Load audio duration once we know the file id. Reading the audio
        // header is cheap; we just need samples / sampleRate.
        recordingRepo.getRecordingEntriesFlow(recordingId).onEach { entries ->
            if (durationSeconds.value != null) return@onEach
            val fileName = entries.firstOrNull()?.fileName ?: return@onEach
            try {
                withContext(Dispatchers.IO) {
                    val (_, info) = recordingStorage.openRecordingSource("$fileName-clean")
                    val rate = info.cachedMetadata.sampleRate.toFloat()
                    if (rate > 0f) durationSeconds.value = info.size.toFloat() / rate
                }
            } catch (e: Throwable) {
                logger.w(e) { "duration load failed for $fileName" }
            }
        }.launchIn(viewModelScope)
    }

    fun toggleMoreMenu() {
        moreMenuExpanded.value = !moreMenuExpanded.value
    }

    fun dismissMoreMenu() {
        moreMenuExpanded.value = false
    }

    /** Hard-delete this recording (entries cascade via FK) plus any items
     *  that were extracted from it. Calls [onAfter] after the snackbar so
     *  the screen can pop. */
    fun deleteRecording(onAfter: () -> Unit) {
        viewModelScope.launch {
            try {
                val state = itemState.value as? ItemState.Loaded
                val firestoreId = state?.recording?.firestoreId
                // Soft-delete any items linked back to this recording so the
                // home feed doesn't show orphaned chips.
                val recId = firestoreId?.takeIf { it.isNotBlank() } ?: "local:$recordingId"
                val linked = itemRepo.getByRecording(recId)
                linked.forEach { itemRepo.softDelete(it.firestoreId) }
                recordingRepo.deleteRecording(recordingId)
                snackbarHostState.showSnackbar(
                    "Deleted recording" + if (linked.isNotEmpty()) " + ${linked.size} item(s)" else "",
                )
                onAfter()
            } catch (e: Throwable) {
                logger.e(e) { "deleteRecording failed" }
                snackbarHostState.showSnackbar("Couldn't delete: ${e.message ?: e}")
            }
        }
    }

    private suspend fun playAudio(item: RecordingEntryEntity) {
        val fileName = item.fileName ?: return
        playbackState.value = MessagePlaybackState.Buffering(item.userMessageId ?: -1)
        // Two cases:
        //   1. Local recording the device made itself — both `<id>` and
        //      `<id>-clean` exist (preprocessor wrote the clean variant
        //      before upload). `-clean` is preferred because it's the
        //      noise-suppressed/normalised version.
        //   2. Cloud-synced recording downloaded from another device or
        //      from an older app version that never produced a `-clean`.
        //      In that case `<id>-clean` may not exist in Firebase
        //      Storage at all and `openRecordingSource` will throw on
        //      the metadata fetch. Fall back to the base file — the user
        //      asked for "play whatever we have", which is exactly that.
        val (samples, info) = try {
            recordingStorage.openRecordingSource("$fileName-clean")
        } catch (e: Exception) {
            logger.w(e) { "No `-clean` audio for $fileName, falling back to base file" }
            try {
                recordingStorage.openRecordingSource(fileName)
            } catch (e2: Exception) {
                logger.e(e2) { "No playable audio for $fileName" }
                playbackState.value = MessagePlaybackState.Stopped
                snackbarHostState.showSnackbar("Could not play recording — audio unavailable")
                return
            }
        }
        withContext(Dispatchers.IO) {
            audioPlayer.playRaw(samples, info.cachedMetadata.sampleRate.toLong(), AudioEncoding.PCM_16BIT, info.size)
        }
    }

    private fun stopAudio() {
        audioPlayer.stop()
    }

    init {
        addCloseable(audioPlayer)
        audioPlayer.playbackState.onEach {
            when (it) {
                is PlaybackState.Playing -> if (playbackState.value is MessagePlaybackState.Buffering) {
                    playbackState.value = MessagePlaybackState.Playing((playbackState.value as MessagePlaybackState.Buffering).id, it.percentageComplete)
                } else if (playbackState.value is MessagePlaybackState.Playing) {
                    playbackState.value = MessagePlaybackState.Playing((playbackState.value as MessagePlaybackState.Playing).id, it.percentageComplete)
                }
                is PlaybackState.Stopped -> playbackState.value = MessagePlaybackState.Stopped
            }
        }.launchIn(viewModelScope)
    }

    fun exportRecording() {
        viewModelScope.launch {
            val item = itemState.value as? ItemState.Loaded ?: return@launch
            item.entries.firstOrNull()?.fileName?.let {
                for (id in listOf(it, "$it-clean")) {
                    val path = recordingStorage.exportRecording(id)
                    writeToDownloads(uiContext, path)
                }
            } ?: run {
                logger.e { "Can't export, no recording file to export" }
                snackbarHostState.showSnackbar(
                    message = "No recording file to export"
                )
            }
        }
    }

    fun retryRecording() {
        viewModelScope.launch {
            val state = itemState.value as? ItemState.Loaded ?: return@launch
            val entry = state.entries.firstOrNull() ?: return@launch
            val transfers = withContext(Dispatchers.IO) { ringTransferDao.getByRecordingId(recordingId) }
            val transfer = transfers.firstOrNull()
            if (transfer != null) {
                recordingProcessingQueue.retryRecording(
                    transferId = transfer.id,
                    buttonSequence = null,
                    recordingId = recordingId,
                    recordingEntryId = entry.id,
                )
            } else {
                val fileId = entry.fileName ?: return@launch
                recordingProcessingQueue.retryLocalRecording(
                    fileId = fileId,
                    buttonSequence = null,
                    recordingId = recordingId,
                    recordingEntryId = entry.id,
                )
            }
            snackbarHostState.showSnackbar("Retrying...")
        }
    }

    fun togglePlayback(recordingEntry: RecordingEntryEntity) {
        viewModelScope.launch {
            when (val currentState = playbackState.value) {
                is MessagePlaybackState.Playing if currentState.id == (recordingEntry.userMessageId ?: -1) -> {
                    stopAudio()
                }
                is MessagePlaybackState.Buffering if currentState.id == (recordingEntry.userMessageId ?: -1) -> {
                    stopAudio()
                }
                else -> {
                    playAudio(recordingEntry)
                }
            }
        }
    }

    fun beginRecordingReply() {

    }
}

expect suspend fun writeToDownloads(uiContext: PlatformUiContext, path: Path, mimeType: String = "audio/wav")

sealed class MessagePlaybackState {
    data class Playing(val id: Long, val percentageComplete: Double): MessagePlaybackState()
    data class Buffering(val id: Long): MessagePlaybackState()
    data object Stopped: MessagePlaybackState()
}
