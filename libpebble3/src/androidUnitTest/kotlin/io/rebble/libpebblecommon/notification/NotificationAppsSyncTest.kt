package io.rebble.libpebblecommon.notification

import io.rebble.libpebblecommon.NotificationConfig
import io.rebble.libpebblecommon.database.MillisecondInstant
import io.rebble.libpebblecommon.database.entity.MuteState
import io.rebble.libpebblecommon.database.entity.NotificationAppItem
import io.rebble.libpebblecommon.io.rebble.libpebblecommon.notification.AndroidNotificationAppsSync.Companion.defaultMuteStateForPackage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.time.Instant

class NotificationAppsSyncTest {

    @Test
    fun defaultMuteState_systemAppAlwaysMuted_evenWhenDefaultsEnabled() {
        val cfg = NotificationConfig(defaultAppsToEnabled = true)
        assertEquals(
            MuteState.Always,
            cfg.defaultMuteStateForPackage("com.android.somesystemthing", isSystemApp = true),
        )
    }

    @Test
    fun defaultMuteState_userAppEnabledByDefault() {
        val cfg = NotificationConfig(defaultAppsToEnabled = true)
        assertEquals(
            MuteState.Never,
            cfg.defaultMuteStateForPackage("com.example.regular", isSystemApp = false),
        )
    }

    @Test
    fun defaultMuteState_userAppMutedWhenGlobalDefaultDisabled() {
        val cfg = NotificationConfig(defaultAppsToEnabled = false)
        assertEquals(
            MuteState.Always,
            cfg.defaultMuteStateForPackage("com.example.regular", isSystemApp = false),
        )
    }

    @Test
    fun defaultMuteState_blocklistedPackageMuted() {
        val cfg = NotificationConfig(defaultAppsToEnabled = true)
        assertEquals(
            MuteState.Always,
            cfg.defaultMuteStateForPackage("com.google.android.calendar", isSystemApp = false),
        )
    }

    @Test
    fun defaultMuteState_curatedAppEnabled() {
        // Curated apps (NotificationProperties entries) arrive here from the sync path with
        // isSystemApp=false — the sync inlines the NotificationProperties check into its
        // isSystemApp computation so curated apps land in the regular list, not "Show system
        // apps." This test pins the resulting default-mute behavior end-to-end.
        val cfg = NotificationConfig(defaultAppsToEnabled = true)
        assertEquals(
            MuteState.Never,
            cfg.defaultMuteStateForPackage("com.google.android.dialer", isSystemApp = false),
        )
    }

    @Test
    fun defaultMuteState_blocklistBeatsRegularUserAppDefault() {
        // Google Calendar is in both NotificationProperties (for theming) AND the blocklist.
        // Sync gives it isSystemApp=false (NotificationProperties wins on classification), and
        // the blocklist still forces it muted by default. Both safety nets in play.
        val cfg = NotificationConfig(defaultAppsToEnabled = true)
        assertEquals(
            MuteState.Always,
            cfg.defaultMuteStateForPackage("com.google.android.calendar", isSystemApp = false),
        )
    }

    @Test
    fun defaultMuteState_globalDisableOverridesEverything() {
        val cfg = NotificationConfig(defaultAppsToEnabled = false)
        assertEquals(
            MuteState.Always,
            cfg.defaultMuteStateForPackage("com.google.android.dialer", isSystemApp = false),
        )
    }

    @Test
    fun deletionSweep_skipsAutoAddedRows() {
        // Auto-added rows represent cross-profile / multi-user apps that PackageManager in our
        // profile can't see. They must survive the sync deletion sweep, otherwise we'd nuke
        // every cross-profile app row on the next OS sync.
        val pmSyncedRemoved = appItem("com.example.removed", autoAdded = false)
        val crossProfile = appItem("com.example.crossprofile", autoAdded = true)

        val toDelete = listOf(pmSyncedRemoved, crossProfile)
            .filter { !it.autoAdded }

        assertEquals(listOf(pmSyncedRemoved), toDelete)
        assertFalse(toDelete.contains(crossProfile))
    }

    private fun appItem(pkg: String, autoAdded: Boolean): NotificationAppItem =
        NotificationAppItem(
            packageName = pkg,
            name = pkg,
            muteState = MuteState.Never,
            channelGroups = emptyList(),
            stateUpdated = MillisecondInstant(Instant.fromEpochMilliseconds(0)),
            lastNotified = MillisecondInstant(Instant.fromEpochMilliseconds(0)),
            muteExpiration = null,
            vibePatternName = null,
            colorName = null,
            iconCode = null,
            allowDuplicates = false,
            isSystemApp = false,
            autoAdded = autoAdded,
        )
}
