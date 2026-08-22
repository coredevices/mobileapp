package coredevices.ring.agent.builtin_servlets.notes

import coredevices.ring.database.Preferences
import coredevices.ring.external.indexlocal.IndexLocalAppPreferences
import coredevices.util.integrations.IntegrationTokenStorage

/**
 * Users who turned on the old per-gesture "Local apps → Notesnook" toggle should keep
 * receiving captures after Notesnook becomes a normal Notes destination.
 */
class NotesnookFromLocalAppsMigration(
    private val localPrefs: IndexLocalAppPreferences,
    private val prefs: Preferences,
    private val tokenStorage: IntegrationTokenStorage,
) {
    suspend fun run() {
        if (!localPrefs.anyEnabled()) return
        if (tokenStorage.getToken(NOTESNOOK_TOKEN_STORAGE_KEY) == null) {
            tokenStorage.saveToken(NOTESNOOK_TOKEN_STORAGE_KEY, "enabled")
        }
        if (prefs.noteProvider.value == NoteProvider.Builtin) {
            prefs.setNoteProvider(NoteProvider.Notesnook)
        }
    }
}
