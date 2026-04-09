package coredevices.ring.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import coredevices.ring.api.NotionApi
import coredevices.ring.data.NoteShortcutType
import coredevices.ring.database.MusicControlMode
import coredevices.ring.database.Preferences
import coredevices.ring.database.SecondaryMode
import coredevices.firestore.UsersDao
import coredevices.ring.service.RingSync
import coredevices.ui.ModelType
import coredevices.util.CommonBuildKonfig
import coredevices.util.Platform
import coredevices.util.emailOrNull
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val usersDao: UsersDao,
    private val platform: Platform,
    private val ringSync: RingSync,
    private val preferences: Preferences
): ViewModel() {
    val version = CommonBuildKonfig.GIT_HASH
    val username = Firebase.auth.authStateChanged
        .map { it?.emailOrNull }
        .stateIn(viewModelScope, SharingStarted.Lazily, Firebase.auth.currentUser?.email)
    private val _useCactusAgent = MutableStateFlow(false)
    val useCactusAgent = _useCactusAgent.asStateFlow()
    private val _showModelDownloadDialog = MutableStateFlow<ModelType?>(null)
    val showModelDownloadDialog = _showModelDownloadDialog.asStateFlow()
    private val _showMusicControlDialog = MutableStateFlow(false)
    val showMusicControlDialog = _showMusicControlDialog.asStateFlow()
    val musicControlMode = preferences.musicControlMode
    val debugDetailsEnabled = preferences.debugDetailsEnabled
    private val _showContactsDialog = MutableStateFlow(false)
    val showContactsDialog = _showContactsDialog.asStateFlow()
    private val _showSecondaryModeDialog = MutableStateFlow(false)
    val showSecondaryModeDialog = _showSecondaryModeDialog.asStateFlow()
    val secondaryMode = preferences.secondaryMode
    private val _showNoteShortcutDialog = MutableStateFlow(false)
    val showNoteShortcutDialog = _showNoteShortcutDialog.asStateFlow()
    val noteShortcut = preferences.noteShortcut
    private val currentRing = ringSync.lastRing
    val ringPaired = preferences.ringPaired
    val currentRingFirmware = currentRing.flatMapLatest { it?.state ?: emptyFlow() }
        .map { it?.firmwareVersion }
        .stateIn(
            viewModelScope,
            started = SharingStarted.Lazily,
            initialValue = null
        )

    val currentRingName = currentRing
        .map { it?.name }
        .stateIn(
            viewModelScope,
            started = SharingStarted.Lazily,
            initialValue = null
        )

    init {
        viewModelScope.launch {
            preferences.useCactusAgent.collectLatest { useCactus ->
                _useCactusAgent.value = useCactus
            }
        }
    }

    fun onModelDownloadDialogDismissed(success: Boolean) {
        val wasDownloading = _showModelDownloadDialog.value ?: return
        _showModelDownloadDialog.value = null
        viewModelScope.launch {
            when (wasDownloading) {
                is ModelType.Agent -> preferences.setUseCactusAgent(success)
                is ModelType.STT -> preferences.setUseCactusTranscription(success)
            }
        }
    }
    
    fun toggleCactusAgent() {
        viewModelScope.launch {
            if (!_useCactusAgent.value) {
                _showModelDownloadDialog.value = ModelType.Agent(CommonBuildKonfig.CACTUS_LM_MODEL_NAME)
            } else {
                preferences.setUseCactusAgent(false)
            }
        }
    }

    fun showMusicControlDialog() {
        _showMusicControlDialog.value = true
    }

    fun closeMusicControlDialog() {
        _showMusicControlDialog.value = false
    }

    fun setMusicControlMode(mode: MusicControlMode) {
        preferences.setMusicControlMode(mode)
    }

    fun showSecondaryModeDialog() {
        _showSecondaryModeDialog.value = true
    }

    fun closeSecondaryModeDialog() {
        _showSecondaryModeDialog.value = false
    }

    fun setSecondaryMode(mode: SecondaryMode) {
        preferences.setSecondaryMode(mode)
    }

    fun toggleDebugDetailsEnabled() {
        viewModelScope.launch {
            val newValue = !debugDetailsEnabled.value
            preferences.setDebugDetailsEnabled(newValue)
        }
    }

    fun showNoteShortcutDialog() {
        _showNoteShortcutDialog.value = true
    }

    fun closeNoteShortcutDialog() {
        _showNoteShortcutDialog.value = false
    }

    fun setNoteShortcut(shortcut: NoteShortcutType) {
        preferences.setNoteShortcut(shortcut)
    }

    fun showContactsDialog() {
        _showContactsDialog.value = true
    }
    
    fun closeContactsDialog() {
        _showContactsDialog.value = false
    }
}