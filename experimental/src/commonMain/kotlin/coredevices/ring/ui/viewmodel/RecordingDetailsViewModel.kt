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
import coredevices.ring.database.room.repository.RecordingRepository
import coredevices.ring.storage.RecordingStorage
import coredevices.ring.util.AudioPlayer
import coredevices.ring.util.PlaybackState
import coredevices.util.AudioEncoding
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.io.files.Path

class RecordingDetailsViewModel(
    private val recordingId: Long,
    private val recordingRepo: RecordingRepository,
    private val conversationMessageDao: ConversationMessageDao,
    private val recordingStorage: RecordingStorage,
    private val audioPlayer: AudioPlayer,
    private val snackbarHostState: SnackbarHostState,
    private val uiContext: PlatformUiContext,
    private val prefs: Preferences
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

    val moreMenuExpanded = MutableStateFlow(false)
    val playbackState = MutableStateFlow<MessagePlaybackState>(MessagePlaybackState.Stopped)

    init {
        playbackState.drop(1).onEach {
            logger.d { "Playback state changed: $it" }
        }.launchIn(viewModelScope)
    }

    fun toggleMoreMenu() {
        moreMenuExpanded.value = !moreMenuExpanded.value
    }

    fun dismissMoreMenu() {
        moreMenuExpanded.value = false
    }

    private suspend fun playAudio(item: RecordingEntryEntity) {
        item.fileName?.let {
            playbackState.value = MessagePlaybackState.Buffering(item.userMessageId ?: -1)
            val (samples, info) = recordingStorage.openRecordingSource("$it-clean")
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

expect suspend fun writeToDownloads(uiContext: PlatformUiContext, path: Path)

sealed class MessagePlaybackState {
    data class Playing(val id: Long, val percentageComplete: Double): MessagePlaybackState()
    data class Buffering(val id: Long): MessagePlaybackState()
    data object Stopped: MessagePlaybackState()
}