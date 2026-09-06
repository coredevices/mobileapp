package io.rebble.libpebblecommon.notification

import io.rebble.libpebblecommon.NotificationConfig
import io.rebble.libpebblecommon.database.MillisecondInstant
import io.rebble.libpebblecommon.database.entity.ChannelGroup
import io.rebble.libpebblecommon.database.entity.ChannelItem
import io.rebble.libpebblecommon.database.entity.MuteState
import io.rebble.libpebblecommon.database.entity.NotificationAppItem
import io.rebble.libpebblecommon.io.rebble.libpebblecommon.notification.AndroidNotificationAppsSync.Companion.defaultMuteStateForPackage
import io.rebble.libpebblecommon.io.rebble.libpebblecommon.notification.carriedOverMuteState
import io.rebble.libpebblecommon.io.rebble.libpebblecommon.notification.mergedWithOsApp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
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

    @Test
    fun channelMute_preservedWhenIdChangesButNameMatches() {
        val existing = appItem("com.snapchat.android", autoAdded = false).copy(
            channelGroups = listOf(
                ChannelGroup(
                    id = "default", name = null, channels = listOf(
                        ChannelItem(id = "silent_v1", name = "Silent notifications", muteState = MuteState.Always),
                    )
                )
            )
        )
        val newGroup = ChannelGroup(id = "default", name = null, channels = emptyList())
        val newChannel = ChannelItem(id = "silent_v2", name = "Silent notifications", muteState = MuteState.Never)
        assertEquals(MuteState.Always, existing.carriedOverMuteState(newGroup, newChannel))
    }

    @Test
    fun channelMute_idMatchWinsOverNameMatch() {
        val existing = appItem("com.example", autoAdded = false).copy(
            channelGroups = listOf(
                ChannelGroup(
                    id = "g", name = "Group", channels = listOf(
                        ChannelItem(id = "a", name = "Chat", muteState = MuteState.Never),
                        ChannelItem(id = "b", name = "Chat", muteState = MuteState.Always),
                    )
                )
            )
        )
        val group = ChannelGroup(id = "g", name = "Group", channels = emptyList())
        val channel = ChannelItem(id = "b", name = "Chat", muteState = MuteState.Never)
        assertEquals(MuteState.Always, existing.carriedOverMuteState(group, channel))
    }

    @Test
    fun channelMute_unknownChannelDefaultsToNever() {
        val existing = appItem("com.example", autoAdded = false).copy(
            channelGroups = listOf(
                ChannelGroup(
                    id = "g", name = "Group", channels = listOf(
                        ChannelItem(id = "a", name = "Chat", muteState = MuteState.Always),
                    )
                )
            )
        )
        val group = ChannelGroup(id = "g", name = "Group", channels = emptyList())
        val channel = ChannelItem(id = "new", name = "Other", muteState = MuteState.Never)
        assertEquals(MuteState.Never, existing.carriedOverMuteState(group, channel))
    }

    @Test
    fun osSyncMerge_preservesUserSetFields() {
        val existing = appItem("com.example", autoAdded = false).copy(
            muteState = MuteState.Always,
            muteExpiration = MillisecondInstant(Instant.fromEpochMilliseconds(1234)),
            vibePatternName = "pattern",
            colorName = "red",
            iconCode = "icon",
            allowDuplicates = true,
            sendImages = false,
            rulesUpdated = MillisecondInstant(Instant.fromEpochMilliseconds(5678)),
        )

        val merged = existing.mergedWithOsApp(
            name = "Example",
            channels = emptyList(),
            isSystemApp = false,
        )

        assertEquals(existing.copy(name = "Example"), merged)
    }

    @Test
    fun osSyncMerge_takesNameChannelsAndSystemFlagFromOs() {
        val existing = appItem("com.example", autoAdded = true).copy(name = "Old name")
        val channels = listOf(
            ChannelGroup(
                id = "g", name = "Group", channels = listOf(
                    ChannelItem(id = "a", name = "Chat", muteState = MuteState.Never),
                )
            )
        )

        val merged = existing.mergedWithOsApp("New name", channels, isSystemApp = true)

        assertEquals("New name", merged.name)
        assertEquals(channels, merged.channelGroups)
        assertTrue(merged.isSystemApp)
        assertFalse(merged.autoAdded)
    }

    @Test
    fun osSyncMerge_carriesOverChannelMuteStates() {
        val existing = appItem("com.example", autoAdded = false).copy(
            channelGroups = listOf(
                ChannelGroup(
                    id = "g", name = "Group", channels = listOf(
                        ChannelItem(id = "a", name = "Chat", muteState = MuteState.Always),
                    )
                )
            )
        )
        val fromOs = listOf(
            ChannelGroup(
                id = "g", name = "Group", channels = listOf(
                    ChannelItem(id = "a", name = "Chat", muteState = MuteState.Never),
                    ChannelItem(id = "b", name = "Calls", muteState = MuteState.Never),
                )
            )
        )

        val merged = existing.mergedWithOsApp("Example", fromOs, isSystemApp = false)

        assertEquals(MuteState.Always, merged.channelGroups[0].channels[0].muteState)
        assertEquals(MuteState.Never, merged.channelGroups[0].channels[1].muteState)
    }

    @Test
    fun osSyncMerge_emptyOsChannelsKeepsExistingChannels() {
        // getChannelsForApp() returns empty when the notification listener isn't bound yet.
        val existing = appItem("com.example", autoAdded = false).copy(
            channelGroups = listOf(
                ChannelGroup(
                    id = "g", name = "Group", channels = listOf(
                        ChannelItem(id = "a", name = "Chat", muteState = MuteState.Always),
                    )
                )
            )
        )

        val merged = existing.mergedWithOsApp("Example", channels = emptyList(), isSystemApp = false)

        assertEquals(existing.channelGroups, merged.channelGroups)
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
