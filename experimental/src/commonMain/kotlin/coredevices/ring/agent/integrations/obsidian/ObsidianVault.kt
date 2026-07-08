package coredevices.ring.agent.integrations.obsidian

import PlatformUiContext

/** A user-selected vault folder: [handle] is the opaque persisted token, [displayName] is for UI. */
data class VaultRef(val handle: String, val displayName: String)

/**
 * Platform filesystem access to an Obsidian vault folder. The [handle] is an opaque
 * token persisted in [ObsidianPreferences]; only the platform implementation interprets it.
 * [name] arguments are paths relative to the vault root and may contain one subfolder segment
 * (e.g. "Index Inbox/2026-06-18 1405.md"); implementations create the subfolder if missing.
 */
interface ObsidianVault {
    /** Presents a folder picker, persists access, and returns the selection (null if cancelled). */
    suspend fun pickFolder(uiContext: PlatformUiContext): VaultRef?

    /** True if [handle] still grants write access (e.g. SAF permission held / bookmark not stale). */
    suspend fun hasAccess(handle: String): Boolean

    /** Leaf names of `.md` files directly inside [subfolder] (vault root when blank). */
    suspend fun listMarkdownFiles(handle: String, subfolder: String = ""): List<String>

    /** Contents of [name], or null if it does not exist. */
    suspend fun readFile(handle: String, name: String): String?

    /** Create or overwrite [name] with [content]. Returns true on success. */
    suspend fun writeFile(handle: String, name: String, content: String): Boolean

    /** Release any persisted access to [handle]. No-op where not applicable. */
    suspend fun releaseAccess(handle: String)
}
