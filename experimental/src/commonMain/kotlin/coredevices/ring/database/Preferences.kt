package coredevices.ring.database

import com.russhwolf.settings.Settings
import coredevices.libindex.database.BasePreferences
import coredevices.ring.agent.builtin_servlets.messaging.ApprovedBeeperContact
import coredevices.ring.agent.builtin_servlets.notes.NoteProvider
import coredevices.ring.agent.builtin_servlets.reminders.ReminderProvider
import coredevices.ring.data.NoteShortcutType
import coredevices.util.models.CactusSTTMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

interface Preferences: BasePreferences {
    val useCactusAgent: StateFlow<Boolean>
    val useCactusTranscription: StateFlow<Boolean>
    val cactusMode: CactusSTTMode
    val ringPairedOld: StateFlow<Boolean>
    val musicControlMode: StateFlow<MusicControlMode>
    val debugDetailsEnabled: StateFlow<Boolean>
    val approvedBeeperContacts: StateFlow<List<ApprovedBeeperContact>>
    val secondaryMode: StateFlow<SecondaryMode>
    val primaryMode: StateFlow<PrimaryMode>
    val shareWithIndexAgent: StateFlow<Boolean>
    val reminderProvider: StateFlow<ReminderProvider>
    val noteProvider: StateFlow<NoteProvider>
    val noteShortcut: StateFlow<NoteShortcutType>
    val backupEnabled: StateFlow<Boolean>
    val useEncryption: StateFlow<Boolean>
    val encryptionKeyFingerprint: StateFlow<String?>

    suspend fun setUseCactusAgent(useCactus: Boolean)
    suspend fun setUseCactusTranscription(useCactus: Boolean)
    fun setCactusMode(mode: CactusSTTMode)
    fun setMusicControlMode(mode: MusicControlMode)
    fun setDebugDetailsEnabled(enabled: Boolean)
    suspend fun setApprovedBeeperContacts(contacts: List<ApprovedBeeperContact>?)
    fun setSecondaryMode(mode: SecondaryMode)
    fun setPrimaryMode(mode: PrimaryMode)
    fun setShareWithIndexAgent(value: Boolean)
    fun setReminderProvider(provider: ReminderProvider)
    fun setNoteProvider(provider: NoteProvider)
    fun setNoteShortcut(shortcut: NoteShortcutType)
    fun setBackupEnabled(enabled: Boolean)
    fun setUseEncryption(enabled: Boolean)
    fun setEncryptionKeyFingerprint(fingerprint: String?)
}

class PreferencesImpl(private val settings: Settings): Preferences {

    private val _useCactusAgent = MutableStateFlow(settings.getBoolean("use_cactus_agent", false))
    override val useCactusAgent = _useCactusAgent.asStateFlow()
    private val _useCactusTranscription = MutableStateFlow(settings.getBoolean("use_cactus_transcription", true))
    override val useCactusTranscription = _useCactusTranscription.asStateFlow()
    override val cactusMode get() = CactusSTTMode.fromId(settings.getInt("cactus_mode", 0))
    private val _ringPaired = MutableStateFlow(
        try {
            settings.getStringOrNull("ring_paired")
        } catch (e: Exception) {
            null
        }
    )
    override val ringPaired = _ringPaired.asStateFlow()
    override val ringPairedOld = MutableStateFlow(
        try {
            settings.getBoolean("ring_paired", false)
        } catch (e: Exception) {
            false
        }
    ).asStateFlow()
    private val _musicControlMode = MutableStateFlow(MusicControlMode.fromId(settings.getInt("music_control_mode", MusicControlMode.DoubleClick.id)))
    override val musicControlMode = _musicControlMode.asStateFlow()
    private val _lastSyncIndex = MutableStateFlow(
        settings.getIntOrNull("last_sync_index")
    )
    override val lastSyncIndex = _lastSyncIndex.asStateFlow()
    private val _debugDetailsEnabled = MutableStateFlow(settings.getBoolean("debug_details_enabled", false))
    override val debugDetailsEnabled = _debugDetailsEnabled.asStateFlow()
    private val _approvedBeeperContacts = MutableStateFlow(
        settings.getStringOrNull("approved_beeper_contacts")?.let { raw ->
            try {
                Json.decodeFromString<List<ApprovedBeeperContact>>(raw)
            } catch (_: Exception) {
                // Migrate from old format (plain list of roomId strings)
                try {
                    Json.decodeFromString<List<String>>(raw).map {
                        ApprovedBeeperContact(roomId = it, name = "")
                    }
                } catch (_: Exception) { emptyList() }
            }
        } ?: emptyList()
    )
    override val approvedBeeperContacts = _approvedBeeperContacts.asStateFlow()
    private val _secondaryMode = MutableStateFlow(
        SecondaryMode.fromId(settings.getInt("ring_secondary_mode", SecondaryMode.Search.id))
    )
    override val secondaryMode = _secondaryMode.asStateFlow()

    private val _primaryMode = MutableStateFlow(
        PrimaryMode.fromId(settings.getInt("ring_primary_mode", PrimaryMode.IndexAgent.id))
    )
    override val primaryMode = _primaryMode.asStateFlow()

    private val _shareWithIndexAgent = MutableStateFlow(
        settings.getBoolean("ring_share_with_index_agent", false)
    )
    override val shareWithIndexAgent = _shareWithIndexAgent.asStateFlow()

    private val _reminderProvider = MutableStateFlow(
        settings.getInt("reminder_provider", ReminderProvider.Native.id)
            .let { ReminderProvider.fromId(it)!! }
    )
    override val reminderProvider = _reminderProvider.asStateFlow()
    private val _noteProvider = MutableStateFlow(
        settings.getInt("note_provider", NoteProvider.Builtin.id)
            .let { NoteProvider.fromId(it)!! }
    )
    override val noteProvider = _noteProvider.asStateFlow()
    private val _noteShortcut = MutableStateFlow<NoteShortcutType>(settings.getStringOrNull("note_shortcut")
        ?.let { Json.decodeFromString(it) } ?: NoteShortcutType.SendToMe)
    override val noteShortcut: StateFlow<NoteShortcutType> = _noteShortcut.asStateFlow()
    private val _backupEnabled = MutableStateFlow(settings.getBoolean("backup_enabled", true))
    override val backupEnabled = _backupEnabled.asStateFlow()
    private val _useEncryption = MutableStateFlow(settings.getBoolean("use_encryption", false))
    override val useEncryption = _useEncryption.asStateFlow()
    private val _encryptionKeyFingerprint = MutableStateFlow(settings.getStringOrNull("encryption_key_fingerprint"))
    override val encryptionKeyFingerprint = _encryptionKeyFingerprint.asStateFlow()

    override suspend fun setUseCactusAgent(useCactus: Boolean) {
        withContext(Dispatchers.IO) {
            settings.putBoolean("use_cactus_agent", useCactus)
            _useCactusAgent.value = useCactus
        }
    }

    override suspend fun setUseCactusTranscription(useCactus: Boolean) {
        withContext(Dispatchers.IO) {
            settings.putBoolean("use_cactus_transcription", useCactus)
            _useCactusTranscription.value = useCactus
        }
    }

    override fun setCactusMode(mode: CactusSTTMode) {
        settings.putInt("cactus_mode", mode.id)
    }

    override fun setRingPaired(id: String?) {
        id?.let {
            settings.putString("ring_paired", id)
        } ?: settings.remove("ring_paired")
        _ringPaired.value = id
    }

    override fun setMusicControlMode(mode: MusicControlMode) {
        settings.putInt("music_control_mode", mode.id)
        _musicControlMode.value = mode
    }

    override suspend fun setLastSyncIndex(index: Int?) {
        _lastSyncIndex.value = index
        withContext(Dispatchers.IO) {
            if (index != null) {
                settings.putInt("last_sync_index", index)
            } else {
                settings.remove("last_sync_index")
            }
        }
    }

    override fun setDebugDetailsEnabled(enabled: Boolean) {
        settings.putBoolean("debug_details_enabled", enabled)
        _debugDetailsEnabled.value = enabled
    }

    override suspend fun setApprovedBeeperContacts(contacts: List<ApprovedBeeperContact>?) {
        withContext(Dispatchers.IO) {
            if (contacts != null) {
                val json = Json.encodeToString(contacts)
                settings.putString("approved_beeper_contacts", json)
            } else {
                settings.remove("approved_beeper_contacts")
            }
            _approvedBeeperContacts.value = contacts ?: emptyList()
        }
    }

    override fun setSecondaryMode(mode: SecondaryMode) {
        settings.putInt("ring_secondary_mode", mode.id)
        _secondaryMode.value = mode
    }

    override fun setPrimaryMode(mode: PrimaryMode) {
        settings.putInt("ring_primary_mode", mode.id)
        _primaryMode.value = mode
    }

    override fun setShareWithIndexAgent(value: Boolean) {
        settings.putBoolean("ring_share_with_index_agent", value)
        _shareWithIndexAgent.value = value
    }

    override fun setReminderProvider(provider: ReminderProvider) {
        settings.putInt("reminder_provider", provider.id)
        _reminderProvider.value = provider
    }

    override fun setNoteProvider(provider: NoteProvider) {
        settings.putInt("note_provider", provider.id)
        _noteProvider.value = provider
    }

    override fun setNoteShortcut(shortcut: NoteShortcutType) {
        val json = Json.encodeToString(shortcut)
        settings.putString("note_shortcut", json)
        _noteShortcut.value = shortcut
    }

    override fun setBackupEnabled(enabled: Boolean) {
        settings.putBoolean("backup_enabled", enabled)
        _backupEnabled.value = enabled
    }

    override fun setUseEncryption(enabled: Boolean) {
        settings.putBoolean("use_encryption", enabled)
        _useEncryption.value = enabled
    }

    override fun setEncryptionKeyFingerprint(fingerprint: String?) {
        if (fingerprint != null) {
            settings.putString("encryption_key_fingerprint", fingerprint)
        } else {
            settings.remove("encryption_key_fingerprint")
        }
        _encryptionKeyFingerprint.value = fingerprint
    }
}

enum class MusicControlMode(val id: Int) {
    Disabled(0),
    SingleClick(1),
    DoubleClick(2);

    companion object {
        fun fromId(id: Int): MusicControlMode {
            return entries.firstOrNull { it.id == id } ?: Disabled
        }
    }
}

enum class SecondaryMode(val id: Int) {
    Disabled(0),
    Search(1),
    IndexWebhook(2);

    companion object {
        fun fromId(id: Int): SecondaryMode {
            return entries.firstOrNull { it.id == id } ?: Search
        }
    }
}

enum class PrimaryMode(val id: Int) {
    IndexAgent(0),
    Webhook(1);

    companion object {
        fun fromId(id: Int): PrimaryMode {
            return entries.firstOrNull { it.id == id } ?: IndexAgent
        }
    }
}