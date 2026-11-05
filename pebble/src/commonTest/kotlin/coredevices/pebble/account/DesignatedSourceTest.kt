package coredevices.pebble.account

import coredevices.pebble.services.STOCKS_LISTING
import coredevices.pebble.services.StoreApplication
import coredevices.pebble.services.toLockerEntry
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class DesignatedSourceTest {
    private val installed = Json { ignoreUnknownKeys = true }
        .decodeFromString(StoreApplication.serializer(), STOCKS_LISTING)
        .toLockerEntry(sourceUrl = ORIGINAL, timelineToken = null)!!
    private val unknown = installed.copy(uuid = "8f0d6c1e-3b2a-4c5d-9e7f-1a2b3c4d5e6f")
    private val designated = mapOf(installed.uuid to ORIGINAL)

    @Test
    fun anotherStoreCantUpdateAnInstalledApp() {
        val newer = installed.copy(version = "99.0")
        assertEquals(emptyList(), listOf(newer).fromDesignatedSource(OTHER, designated))
    }

    @Test
    fun theInstallingStoreUpdatesItsOwnApp() {
        assertEquals(listOf(installed), listOf(installed).fromDesignatedSource(ORIGINAL, designated))
    }

    @Test
    fun appsNotInTheLockerYetBelongToTheStoreReportingThem() {
        assertEquals(listOf(unknown), listOf(installed, unknown).fromDesignatedSource(OTHER, designated))
    }

    private companion object {
        const val ORIGINAL = "https://original-store/api"
        const val OTHER = "https://other-store/api"
    }
}
