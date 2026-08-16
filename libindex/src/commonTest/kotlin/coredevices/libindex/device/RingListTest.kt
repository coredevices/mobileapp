package coredevices.libindex.device

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The devices screen keys its rows by identifier, so the list must never hold two entries for
 * one ring.
 */
class RingListTest {

    private fun discovered(id: String, deviceName: String) = object : DiscoveredIndexDevice {
        override val identifier = IndexIdentifier(id)
        override val name = deviceName
        override val rssi = -50
        override val pairingState: IndexPairingState = IndexPairingState.NotPaired
        override val currentImage = IndexImage.Primary
        override suspend fun pair() = IndexPairingResult.Success
    }

    private fun repairable(id: String, deviceName: String) = object : RepairableIndexDevice {
        override val identifier = IndexIdentifier(id)
        override val name = deviceName
        override suspend fun forceFailsafe() {}
    }

    private fun known(id: String, deviceName: String) = object : KnownIndexDevice {
        override val identifier = IndexIdentifier(id)
        override val name = deviceName
        override fun remove() {}
    }

    private val primaryAddress = "EA22CE312EAA"
    private val failsafeAddress = "EA22CE312EAB"
    private val ringName = "Pebble Index EAA"

    private fun List<IndexDevice>.ids() = map { it.identifier.asString }

    @Test
    fun addsRingThatIsNotInTheListYet() {
        val list = listOf(known("AABBCCDDEEFF", "Pebble Index FFF"))
            .upsertRing(discovered(primaryAddress, ringName))
        assertEquals(listOf("AABBCCDDEEFF", primaryAddress), list.ids())
    }

    @Test
    fun pairedRingReplacesItsDiscoveredEntry() {
        val list = listOf(discovered(primaryAddress, ringName))
            .upsertRing(known(primaryAddress, ringName))
        assertEquals(listOf(primaryAddress), list.ids())
        assertTrue(list.single() is KnownIndexDevice)
    }

    @Test
    fun pairedRingReplacesDiscoveredEntryAdvertisingAnotherAddress() {
        // Regression: pairing from the system bluetooth settings bonds the ring at its primary
        // address while the in-app scan has it listed under its failsafe one. Appending the
        // paired entry left two rows that later collapsed onto one key and crashed the screen.
        val list = listOf(discovered(failsafeAddress, ringName))
            .upsertRing(known(primaryAddress, ringName))
        assertEquals(listOf(primaryAddress), list.ids())
        assertTrue(list.single() is KnownIndexDevice)
    }

    @Test
    fun pairedRingReplacesRepairableEntryAdvertisingAnotherAddress() {
        // A repairable entry comes from a scan too, so it is superseded like a discovered one.
        val list = listOf(repairable(failsafeAddress, ringName))
            .upsertRing(known(primaryAddress, ringName))
        assertEquals(listOf(primaryAddress), list.ids())
        assertTrue(list.single() is KnownIndexDevice)
    }

    @Test
    fun scannedEntriesCoverDiscoveredAndRepairable() {
        assertTrue(discovered(primaryAddress, ringName).isScanned)
        assertTrue(repairable(primaryAddress, ringName).isScanned)
        assertFalse(known(primaryAddress, ringName).isScanned)
    }

    @Test
    fun collapsesEntriesAlreadyDuplicatedUnderTwoAddresses() {
        val list = listOf(discovered(failsafeAddress, ringName), known(primaryAddress, ringName))
            .upsertRing(known(primaryAddress, ringName))
        assertEquals(listOf(primaryAddress), list.ids())
    }

    @Test
    fun keepsTheSupersededEntrysPosition() {
        val other = known("AABBCCDDEEFF", "Pebble Index FFF")
        val list = listOf(discovered(failsafeAddress, ringName), other)
            .upsertRing(known(primaryAddress, ringName))
        assertEquals(listOf(primaryAddress, "AABBCCDDEEFF"), list.ids())
    }

    @Test
    fun doesNotMergeDifferentRings() {
        val list = listOf(discovered(primaryAddress, ringName))
            .upsertRing(discovered("AABBCCDDEEFF", "Pebble Index FFF"))
        assertEquals(listOf(primaryAddress, "AABBCCDDEEFF"), list.ids())
    }

    @Test
    fun matchesIdentifierIgnoringCase() {
        val list = listOf(discovered(primaryAddress.lowercase(), ringName))
            .upsertRing(known(primaryAddress, "Index 01"))
        assertEquals(listOf(primaryAddress), list.ids())
    }

    @Test
    fun knownEntryDoesNotMatchAnotherAddressByName() {
        // Only discovered entries match on name; a paired ring is identified by its address.
        assertFalse(known(primaryAddress, ringName).isSameRing(IndexIdentifier(failsafeAddress), ringName))
    }
}
