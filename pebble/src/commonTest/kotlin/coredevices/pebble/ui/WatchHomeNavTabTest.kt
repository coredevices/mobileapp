package coredevices.pebble.ui

import coredevices.pebble.ui.WatchHomeNavTab.Health
import coredevices.pebble.ui.WatchHomeNavTab.Index
import coredevices.pebble.ui.WatchHomeNavTab.Notifications
import coredevices.pebble.ui.WatchHomeNavTab.Settings
import coredevices.pebble.ui.WatchHomeNavTab.WatchFaces
import coredevices.pebble.ui.WatchHomeNavTab.Watches
import coredevices.util.CoreConfig
import kotlin.test.Test
import kotlin.test.assertEquals

class WatchHomeNavTabTest {
    private val migrated = listOf("WatchFaces", "Index", "Watches", "Health", "Notifications", "Settings")

    private fun bar(config: CoreConfig, healthEnabled: Boolean) =
        WatchHomeNavTab.barTabs(config, WatchHomeNavTab.availableTabs(config, healthEnabled))

    @Test
    fun migratedBarMatchesLegacyBarWhenEverythingFits() {
        for ((index, health) in listOf(false to false, false to true, true to false)) {
            val legacy = buildList {
                add(WatchFaces)
                if (index) add(Index)
                add(Watches)
                if (health) add(Health)
                add(Notifications)
                add(Settings)
            }
            assertEquals(legacy, bar(CoreConfig(enableIndex = index, navBarTabs = migrated), health), "index=$index health=$health")
        }
    }

    @Test
    fun overflowLeavesASlotForMore() {
        val config = CoreConfig(enableIndex = true, navBarTabs = migrated)
        assertEquals(listOf(WatchFaces, Index, Watches, Health), bar(config, healthEnabled = true))
    }

    @Test
    fun slotsAreCappedToBarCapacity() {
        assertEquals(WatchHomeNavTab.MAX_BAR_SLOTS, WatchHomeNavTab.barSlots(CoreConfig(navBarSlots = 12)))
        assertEquals(2, WatchHomeNavTab.barSlots(CoreConfig(navBarSlots = 0)))
    }

    @Test
    fun fewerSlotsMovesTabsUnderMore() {
        val config = CoreConfig(enableIndex = false, navBarTabs = migrated, navBarSlots = 3)
        assertEquals(listOf(WatchFaces, Watches), bar(config, healthEnabled = false))
    }

    @Test
    fun orderSkipsUnknownNamesAndAppendsMissingTabs() {
        val config = CoreConfig(enableIndex = false, navBarTabs = listOf("Settings", "Index", "Gone", "Watches"))
        assertEquals(
            listOf(Settings, Watches, WatchFaces, Health, Notifications),
            WatchHomeNavTab.availableTabs(config, healthEnabled = true),
        )
    }

    @Test
    fun orderedTabsKeepsUnavailableTabsInPlace() {
        val config = CoreConfig(enableIndex = false, navBarTabs = listOf("Settings", "Index", "Watches"))
        assertEquals(listOf(Settings, Index, Watches, WatchFaces, Health, Notifications), WatchHomeNavTab.orderedTabs(config))
    }
}
