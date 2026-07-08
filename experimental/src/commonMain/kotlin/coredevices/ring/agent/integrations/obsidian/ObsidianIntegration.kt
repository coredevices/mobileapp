package coredevices.ring.agent.integrations.obsidian

import PlatformUiContext
import co.touchlab.kermit.Logger
import coredevices.ring.agent.builtin_servlets.notes.NoteProvider
import coredevices.ring.agent.integrations.ItemSource
import coredevices.ring.agent.integrations.NoteIntegration
import coredevices.ring.data.IntegrationDefinition
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * Writes notes as Markdown files directly into a user-selected local Obsidian vault folder.
 * Filesystem access is delegated to [ObsidianVault]; all formatting lives in [ObsidianNoteFormatter].
 */
class ObsidianIntegration(
    private val vault: ObsidianVault,
    private val prefs: ObsidianPreferences,
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : NoteIntegration {

    companion object {
        val DEFINITION = IntegrationDefinition(
            title = "Obsidian",
            reminder = null,
            notes = NoteProvider.Obsidian,
        )
        private val logger = Logger.withTag("ObsidianIntegration")

        // Serialises note writes across all instances (the vault + prefs are singletons, but this
        // class is a DI factory). Notes can be created concurrently from the background service and
        // the share action; without this, two writes could pick the same filename (timestamped
        // mode) or lose an append (read-modify-write race).
        private val noteMutex = Mutex()
    }

    /** Opens the folder picker and stores the selection. Returns true if a folder was chosen. */
    override suspend fun signIn(uiContext: PlatformUiContext): Boolean {
        val ref = vault.pickFolder(uiContext) ?: return false
        prefs.setVault(ref.handle, ref.displayName)
        return true
    }

    override suspend fun unlink() {
        prefs.vaultHandle.value?.let { vault.releaseAccess(it) }
        prefs.clear()
    }

    override suspend fun isAuthorized(): Boolean {
        val handle = prefs.vaultHandle.value ?: return false
        return vault.hasAccess(handle)
    }

    fun saveConfig(mode: ObsidianMode, targetNote: String, subfolder: String) {
        prefs.setMode(mode)
        prefs.setTargetNote(targetNote)
        prefs.setSubfolder(subfolder)
    }

    fun vaultDisplayName(): String? = prefs.vaultName.value

    fun hasVault(): Boolean = prefs.vaultHandle.value != null
    fun currentMode(): ObsidianMode = prefs.mode.value
    fun currentTargetNote(): String = prefs.targetNote.value
    fun currentSubfolder(): String = prefs.subfolder.value

    /** Lists existing `.md` files in the vault for the "named note" picker (empty if no access). */
    suspend fun listNotes(): List<String> {
        val handle = prefs.vaultHandle.value ?: return emptyList()
        if (!vault.hasAccess(handle)) return emptyList()
        return vault.listMarkdownFiles(handle)
    }

    override suspend fun createNote(content: String, source: ItemSource?): String? = noteMutex.withLock {
        val handle = prefs.vaultHandle.value
        if (handle == null || !vault.hasAccess(handle)) {
            logger.w { "No accessible Obsidian vault; cannot create note" }
            return@withLock null
        }
        val mode = prefs.mode.value
        val targetNote = prefs.targetNote.value
        if (mode == ObsidianMode.NAMED_NOTE && targetNote.isBlank()) {
            // No target note chosen (e.g. stale/corrupted config); writing to ".md" would be wrong.
            logger.w { "Obsidian named-note mode has no target note; cannot create note" }
            return@withLock null
        }
        val config = ObsidianConfig(
            mode = mode,
            targetNote = targetNote,
            subfolder = prefs.subfolder.value,
        )
        val local = clock.now().toLocalDateTime(timeZone)
        val write = ObsidianNoteFormatter.plan(
            config, content,
            local.year, local.monthNumber, local.dayOfMonth, local.hour, local.minute,
        )
        when (write) {
            is ObsidianWrite.NewFile -> {
                val slash = write.fileName.lastIndexOf('/')
                val dir = if (slash >= 0) write.fileName.substring(0, slash) else ""
                val leaf = if (slash >= 0) write.fileName.substring(slash + 1) else write.fileName
                val existing = vault.listMarkdownFiles(handle, dir).toSet()
                val dedupedLeaf = ObsidianNoteFormatter.dedupeName(leaf) { it in existing }
                val finalName = if (dir.isEmpty()) dedupedLeaf else "$dir/$dedupedLeaf"
                if (vault.writeFile(handle, finalName, write.content)) finalName else null
            }
            is ObsidianWrite.Append -> {
                val current = vault.readFile(handle, write.fileName)
                val merged = ObsidianNoteFormatter.mergeAppend(current, write.block)
                if (vault.writeFile(handle, write.fileName, merged)) write.fileName else null
            }
        }
    }
}
