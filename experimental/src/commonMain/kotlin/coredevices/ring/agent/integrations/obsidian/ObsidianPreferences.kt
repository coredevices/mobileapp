package coredevices.ring.agent.integrations.obsidian

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Persists the Obsidian vault selection and how notes are written into it.
 * The vault handle is an opaque platform token (SAF tree-URI string on Android,
 * base64 security-scoped bookmark on iOS); only [ObsidianVault] interprets it.
 */
class ObsidianPreferences(private val settings: Settings) {

    companion object {
        const val DEFAULT_SUBFOLDER = "Index Inbox"
        private const val HANDLE_KEY = "obsidian_vault_handle"
        private const val NAME_KEY = "obsidian_vault_name"
        private const val MODE_KEY = "obsidian_mode"
        private const val TARGET_NOTE_KEY = "obsidian_target_note"
        private const val SUBFOLDER_KEY = "obsidian_subfolder"
    }

    private val _vaultHandle = MutableStateFlow(settings.getStringOrNull(HANDLE_KEY))
    val vaultHandle = _vaultHandle.asStateFlow()

    private val _vaultName = MutableStateFlow(settings.getStringOrNull(NAME_KEY))
    val vaultName = _vaultName.asStateFlow()

    private val _mode = MutableStateFlow(
        ObsidianMode.fromId(settings.getInt(MODE_KEY, ObsidianMode.TIMESTAMPED_FILES.id))
    )
    val mode = _mode.asStateFlow()

    private val _targetNote = MutableStateFlow(settings.getStringOrNull(TARGET_NOTE_KEY) ?: "")
    val targetNote = _targetNote.asStateFlow()

    private val _subfolder = MutableStateFlow(settings.getStringOrNull(SUBFOLDER_KEY) ?: DEFAULT_SUBFOLDER)
    val subfolder = _subfolder.asStateFlow()

    fun setVault(handle: String, name: String) {
        settings.putString(HANDLE_KEY, handle)
        settings.putString(NAME_KEY, name)
        _vaultHandle.value = handle
        _vaultName.value = name
    }

    fun setMode(mode: ObsidianMode) {
        settings.putInt(MODE_KEY, mode.id)
        _mode.value = mode
    }

    fun setTargetNote(note: String) {
        settings.putString(TARGET_NOTE_KEY, note)
        _targetNote.value = note
    }

    fun setSubfolder(subfolder: String) {
        settings.putString(SUBFOLDER_KEY, subfolder)
        _subfolder.value = subfolder
    }

    fun clear() {
        settings.remove(HANDLE_KEY)
        settings.remove(NAME_KEY)
        settings.remove(MODE_KEY)
        settings.remove(TARGET_NOTE_KEY)
        settings.remove(SUBFOLDER_KEY)
        _vaultHandle.value = null
        _vaultName.value = null
        _mode.value = ObsidianMode.TIMESTAMPED_FILES
        _targetNote.value = ""
        _subfolder.value = DEFAULT_SUBFOLDER
    }
}
