package coredevices.libindex.device

import kotlin.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The devices screen keys its rows by identifier, so the list must never hold two entries for
 * one ring, and a scan result has to land on the entry already standing in for it.
 */
class RingListTest {

    private fun discovered(id: String, deviceName: String, image: IndexImage = IndexImage.Failsafe) =
        object : DiscoveredIndexDevice {
            override val identifier = IndexIdentifier(id)
            override val name = deviceName
            override val rssi = -50
            override val currentImage = image
        }

    private fun pairable(
        id: String,
        deviceName: String,
        pairing: IndexPairingState = IndexPairingState.NotPaired,
    ) = object : PairableIndexDevice {
        override val identifier = IndexIdentifier(id)
        override val name = deviceName
        override val rssi = -50
        override val pairingState: IndexPairingState = pairing
        override val currentImage = IndexImage.Primary
        override suspend fun pair() = IndexPairingResult.Success
    }

    private fun repairable(id: String, deviceName: String) = object : RepairableIndexDevice {
        override val identifier = IndexIdentifier(id)
        override val name = deviceName
        override val rssi = -50
        override val currentImage = IndexImage.ProductionTest
        override suspend fun forceFailsafe() {}
    }

    private fun known(id: String, deviceName: String) = object : KnownIndexDevice {
        override val identifier = IndexIdentifier(id)
        override val name = deviceName
        override fun remove() {}
        override suspend fun measureRSSI(connectionTimeout: Duration): RSSIMeasurement = RSSIMeasurement(-50f, -50f)
    }

    private val primaryAddress = "EA22CE312EAA"
    private val failsafeAddress = "EA22CE312EAB"
    private val ringName = "Pebble Index EAA"

    private fun List<IndexDevice>.ids() = map { it.identifier.asString }

    @Test
    fun addsRingThatIsNotInTheListYet() {
        val list = listOf(known("AABBCCDDEEFF", "Pebble Index FFF"))
            .upsertRing(pairable(primaryAddress, ringName))
        assertEquals(listOf("AABBCCDDEEFF", primaryAddress), list.ids())
    }

    @Test
    fun pairedRingReplacesItsDiscoveredEntry() {
        val list = listOf(pairable(primaryAddress, ringName))
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
    fun everyScanResultTypeCountsAsScanned() {
        assertTrue(pairable(primaryAddress, ringName).isScanned)
        assertTrue(discovered(failsafeAddress, ringName).isScanned)
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
        val list = listOf(pairable(primaryAddress, ringName))
            .upsertRing(pairable("AABBCCDDEEFF", "Pebble Index FFF"))
        assertEquals(listOf(primaryAddress, "AABBCCDDEEFF"), list.ids())
    }

    @Test
    fun matchesIdentifierIgnoringCase() {
        val list = listOf(pairable(primaryAddress.lowercase(), ringName))
            .upsertRing(known(primaryAddress, "Index 01"))
        assertEquals(listOf(primaryAddress), list.ids())
    }

    @Test
    fun knownEntryDoesNotMatchAnotherAddressByName() {
        // Only discovered entries match on name; a paired ring is identified by its address.
        assertFalse(known(primaryAddress, ringName).isSameRing(IndexIdentifier(failsafeAddress), ringName))
    }

    @Test
    fun findsTheRingByEitherHalfOfTheIdNamePair() {
        val list = listOf(pairable(primaryAddress, ringName))
        assertEquals(primaryAddress, list.getByIDNamePair(IndexIdentifier(primaryAddress), "Index 01")?.identifier?.asString)
        // A ring entering failsafe keeps its name but advertises another address, and the pairing
        // flow only holds the address it advertised when the user tapped pair.
        assertEquals(primaryAddress, list.getByIDNamePair(IndexIdentifier(failsafeAddress), ringName)?.identifier?.asString)
    }

    @Test
    fun findsNoRingWhenNeitherHalfMatches() {
        val list = listOf(pairable(primaryAddress, ringName))
        assertNull(list.getByIDNamePair(IndexIdentifier(failsafeAddress), "Pebble Index FFF"))
        assertNull(emptyList<IndexDevice>().getByIDNamePair(IndexIdentifier(primaryAddress), ringName))
    }

    @Test
    fun keepsPairingStateWhileTheRingStaysPairable() {
        assertEquals(
            IndexPairingState.Pairing,
            pairable(primaryAddress, ringName, pairing = IndexPairingState.Pairing).inheritedPairingState(),
        )
    }

    @Test
    fun dropsPairingStateWhenTheRingIsNoLongerPairable() {
        // A ring dropping into failsafe mid-pair kept the in-flight "Pairing" state on a row that
        // cannot be paired, leaving a spinner nothing would ever clear.
        assertEquals(IndexPairingState.NotPaired, discovered(failsafeAddress, ringName).inheritedPairingState())
        assertEquals(IndexPairingState.NotPaired, repairable(primaryAddress, ringName).inheritedPairingState())
        assertEquals(IndexPairingState.NotPaired, known(primaryAddress, ringName).inheritedPairingState())
        assertEquals(IndexPairingState.NotPaired, null.inheritedPairingState())
    }

    @Test
    fun readsTheImageFromTheEntryStandingInForTheRing() {
        val list = listOf(discovered(failsafeAddress, ringName))
        val entry = list.getByIDNamePair(IndexIdentifier(primaryAddress), ringName)
        assertEquals(IndexImage.Failsafe, (entry as? DiscoveredIndexDevice)?.currentImage)
        assertEquals(
            IndexImage.ProductionTest,
            (listOf(repairable(primaryAddress, ringName)).getByIDNamePair(IndexIdentifier(primaryAddress), ringName) as? DiscoveredIndexDevice)?.currentImage,
        )
        // A paired ring is not a scan result, so it has no image to read.
        assertNull(
            (listOf(known(primaryAddress, ringName)).getByIDNamePair(IndexIdentifier(primaryAddress), ringName) as? DiscoveredIndexDevice)?.currentImage,
        )
    }
}
