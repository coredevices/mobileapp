package coredevices.pebble.ui

import coredevices.pebble.account.FirestoreLockerEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.uuid.Uuid

class AppReplacementsTest {
    private val uuid = Uuid.parse("2c4b7f10-9a3d-4e6b-8f21-5d0c7e9a1b34")
    private val locker = listOf(FirestoreLockerEntry(uuid, appstoreId = "id", appstoreSource = STORE, timelineToken = null))

    @Test
    fun aSideloadedAppHasNoStore() {
        assertNull(installedSourceUrl(uuid, sideloaded = true, locker))
    }

    @Test
    fun aStoreAppComesFromItsLockerEntrysStore() {
        assertEquals(STORE, installedSourceUrl(uuid, sideloaded = false, locker))
    }

    @Test
    fun aStoreAppMissingFromTheLockerMatchesNeitherAStoreNorASideload() {
        val source = installedSourceUrl(uuid, sideloaded = false, locker = emptyList())
        assertNotEquals(STORE, source)
        assertNotEquals(null, source)
    }

    private companion object {
        const val STORE = "https://store/api"
    }
}
