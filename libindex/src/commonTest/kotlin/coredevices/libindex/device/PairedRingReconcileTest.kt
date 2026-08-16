package coredevices.libindex.device

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The reconcile collector re-resolves on every association emission, so the interesting
 * cases are sequences rather than single decisions (those live in [PairedRingResolutionTest]).
 */
class PairedRingReconcileTest {

    private fun assoc(id: String, name: String) =
        IndexAssociation(deviceName = name, identifier = IndexIdentifier(id))

    /** Mirrors the collector: re-read the stored pairing on each emission. */
    private fun fold(
        start: StoredPairing,
        vararg emissions: List<IndexAssociation>?,
    ): StoredPairing = emissions.fold(start) { stored, loaded ->
        stored.applyDecision(resolvePairedRing(stored.id, stored.name, loaded))
    }

    private val ring = StoredPairing("EA22CE312EAA", "Pebble Index EAA")
    private val bondedRing = assoc("EA22CE312EAA", "Pebble Index EAA")
    private val headphones = assoc("AABBCCDDEEFF", "Some Headphones")

    @Test
    fun unloadedEmissionsNeverClearStoredPairing() {
        assertEquals(ring, fold(ring, null, null, null))
    }

    @Test
    fun clearsOnceListLoadsWithoutTheRing() {
        // The seed emission is null (bond list not read yet); only the real read clears.
        assertEquals(StoredPairing(null, null), fold(ring, null, emptyList()))
    }

    @Test
    fun clearsOnceListLoadsWithOtherDevicesOnly() {
        assertEquals(StoredPairing(null, null), fold(ring, null, listOf(headphones)))
    }

    @Test
    fun keepsStoredPairingWhileStillBonded() {
        assertEquals(ring, fold(ring, null, listOf(headphones, bondedRing)))
    }

    @Test
    fun adoptsOnceThenSettles() {
        // Adopt must not re-fire on repeat emissions of the same list; the collection wipe
        // it triggers is keyed on this resolving to Keep the second time around.
        val empty = StoredPairing(null, null)
        val afterFirst = fold(empty, listOf(bondedRing))
        assertEquals(ring, afterFirst)
        assertEquals(
            PairedRingDecision.Keep,
            resolvePairedRing(afterFirst.id, afterFirst.name, listOf(bondedRing)),
        )
    }

    @Test
    fun cachedListSurvivingPermissionLossDoesNotClear() {
        // updateAssociations() keeps the last good list when the read fails, so the collector
        // sees the same list again rather than null.
        assertEquals(ring, fold(ring, listOf(bondedRing), listOf(bondedRing)))
    }

    @Test
    fun renameThenUnbondAppliesInOrder() {
        val renamed = assoc("EA22CE312EAA", "Pebble Index NEW")
        assertEquals(
            StoredPairing(null, null),
            fold(ring, listOf(renamed), emptyList()),
        )
    }

    @Test
    fun renameIsRetainedAcrossEmissions() {
        val renamed = assoc("EA22CE312EAA", "Pebble Index NEW")
        assertEquals(
            StoredPairing("EA22CE312EAA", "Pebble Index NEW"),
            fold(ring, listOf(renamed), listOf(renamed)),
        )
    }
}