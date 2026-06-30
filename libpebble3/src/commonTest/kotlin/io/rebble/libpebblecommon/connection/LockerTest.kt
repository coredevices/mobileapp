package io.rebble.libpebblecommon.connection

import io.rebble.libpebblecommon.database.entity.LockerEntry
import io.rebble.libpebblecommon.database.entity.LockerEntryPlatform
import io.rebble.libpebblecommon.database.entity.asMetadata
import io.rebble.libpebblecommon.metadata.WatchType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

class LockerTest {
    @Test
    fun versionParser() {
        val id = Uuid.random()
        val platform = LockerEntryPlatform(
            lockerEntryId = id,
            sdkVersion = "1.2",
            processInfoFlags = 0,
            name = "aplite",
            screenshotImageUrl = "",
            listImageUrl = "",
            iconImageUrl = "",
            pbwIconResourceId = 0,
        )
        val entry = LockerEntry(
            id = id,
            version = "12.13-rbl1",
            title = "test",
            type = "watchface",
            developerName = "core",
            configurable = false,
            pbwVersionCode = "0",
            sideloaded = false,
            appstoreData = null,
            platforms = listOf(platform),
        )

        val metadata = entry.asMetadata(WatchType.APLITE)
        assertEquals(12u, metadata!!.appVersionMajor.get())
        assertEquals(13u, metadata!!.appVersionMinor.get())
        assertEquals(1u, metadata!!.sdkVersionMajor.get())
        assertEquals(2u, metadata!!.sdkVersionMinor.get())
    }

    @Test
    fun prefersBestVariantForWatchOverFirstCompatible() {
        val id = Uuid.random()
        fun platform(name: String, sdkVersion: String, icon: Int) = LockerEntryPlatform(
            lockerEntryId = id,
            sdkVersion = sdkVersion,
            processInfoFlags = 0,
            name = name,
            screenshotImageUrl = "",
            listImageUrl = "",
            iconImageUrl = "",
            pbwIconResourceId = icon,
        )
        // aplite is listed first (as it commonly is) but reports a stale pre-4.0 SDK.
        val entry = LockerEntry(
            id = id,
            version = "1.0",
            title = "test",
            type = "watchapp",
            developerName = "core",
            configurable = false,
            pbwVersionCode = "0",
            sideloaded = false,
            appstoreData = null,
            platforms = listOf(
                platform(name = "aplite", sdkVersion = "3.0", icon = 1),
                platform(name = "emery", sdkVersion = "5.86", icon = 2),
            ),
        )

        // An emery watch must get the emery variant's metadata, not the first-compatible aplite one.
        val metadata = entry.asMetadata(WatchType.EMERY)
        assertEquals(5u, metadata!!.sdkVersionMajor.get())
        assertEquals(86u, metadata.sdkVersionMinor.get())
        assertEquals(2u, metadata.icon.get())
    }
}