package coredevices.libindex.device

import kotlin.test.Test
import kotlin.test.assertEquals

class PairedRingResolutionTest {

    private fun assoc(id: String, name: String) =
        IndexAssociation(deviceName = name, identifier = IndexIdentifier(id))

    @Test
    fun storedRing_keptWhenAssociationsNotLoaded() {
        // Regression: after an app update revokes BLUETOOTH_CONNECT (or BT is off), the
        // association list is null/unloaded at startup. We must NOT clear the stored ring.
        val decision = resolvePairedRing(
            storedPairedId = "EA22CE312EAA",
            storedPairedName = "Pebble Index EAA",
            associations = null,
        )
        assertEquals(PairedRingDecision.Keep, decision)
    }

    @Test
    fun storedRing_clearedWhenLoadedListDoesNotContainIt() {
        val decision = resolvePairedRing(
            storedPairedId = "EA22CE312EAA",
            storedPairedName = "Pebble Index EAA",
            associations = listOf(assoc("AABBCCDDEEFF", "Some Headphones")),
        )
        assertEquals(PairedRingDecision.Clear, decision)
    }

    @Test
    fun storedRing_clearedWhenLoadedListIsEmpty() {
        // An empty list that has actually been loaded legitimately means no bonded devices.
        val decision = resolvePairedRing(
            storedPairedId = "EA22CE312EAA",
            storedPairedName = "Pebble Index EAA",
            associations = emptyList(),
        )
        assertEquals(PairedRingDecision.Clear, decision)
    }

    @Test
    fun storedRing_keptWhenPresentWithSameName() {
        val decision = resolvePairedRing(
            storedPairedId = "EA22CE312EAA",
            storedPairedName = "Pebble Index EAA",
            associations = listOf(assoc("EA22CE312EAA", "Pebble Index EAA")),
        )
        assertEquals(PairedRingDecision.Keep, decision)
    }

    @Test
    fun storedRing_matchesCaseInsensitively() {
        val decision = resolvePairedRing(
            storedPairedId = "ea22ce312eaa",
            storedPairedName = "Pebble Index EAA",
            associations = listOf(assoc("EA22CE312EAA", "Pebble Index EAA")),
        )
        assertEquals(PairedRingDecision.Keep, decision)
    }

    @Test
    fun storedRing_nameUpdatedWhenChanged() {
        val decision = resolvePairedRing(
            storedPairedId = "EA22CE312EAA",
            storedPairedName = "Old Name",
            associations = listOf(assoc("EA22CE312EAA", "Pebble Index EAA")),
        )
        assertEquals(PairedRingDecision.UpdateName("Pebble Index EAA"), decision)
    }

    @Test
    fun noStoredRing_keptWhenAssociationsNotLoaded() {
        val decision = resolvePairedRing(
            storedPairedId = null,
            storedPairedName = null,
            associations = null,
        )
        assertEquals(PairedRingDecision.Keep, decision)
    }

    @Test
    fun noStoredRing_adoptsPebbleIndexCandidate() {
        val decision = resolvePairedRing(
            storedPairedId = null,
            storedPairedName = null,
            associations = listOf(
                assoc("AABBCCDDEEFF", "Some Headphones"),
                assoc("EA22CE312EAA", "Pebble Index EAA"),
            ),
        )
        assertEquals(PairedRingDecision.Adopt("EA22CE312EAA", "Pebble Index EAA"), decision)
    }

    @Test
    fun noStoredRing_keptWhenNoPebbleIndexInList() {
        val decision = resolvePairedRing(
            storedPairedId = null,
            storedPairedName = null,
            associations = listOf(assoc("AABBCCDDEEFF", "Some Headphones")),
        )
        assertEquals(PairedRingDecision.Keep, decision)
    }
}