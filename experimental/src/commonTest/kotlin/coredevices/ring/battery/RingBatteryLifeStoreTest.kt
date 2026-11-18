package coredevices.ring.battery

import com.russhwolf.settings.MapSettings
import coredevices.ring.data.entity.firestore.FirestoreKnownRing
import coredevices.ring.data.entity.room.battery.RingBatteryHistory
import coredevices.ring.database.room.dao.RingBatteryHistoryDao
import coredevices.ring.ui.screens.settings.formatRecordingTime
import coredevices.ring.ui.screens.settings.formatRemaining
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

class RingBatteryLifeStoreTest {
    private class FakeClock(var now: Instant = Instant.fromEpochSeconds(1_790_000_000)) : Clock {
        override fun now() = now
        fun advance(d: Duration) { now += d }
    }

    private class FakeDao : RingBatteryHistoryDao {
        val rows = mutableMapOf<String, RingBatteryHistory>()
        override suspend fun upsert(history: RingBatteryHistory) { rows[history.serial] = history }
        override suspend fun get(serial: String) = rows[serial]
    }

    private fun store(clock: Clock = FakeClock()) = RingBatteryLifeStore(FakeDao(), MapSettings(), clock)

    @Test
    fun learnsForAWeekThenEstimates() = runTest {
        val clock = FakeClock()
        val store = store(clock)
        assertEquals(null, store.connectedSerial("pair-1"))
        assertEquals(RingBatteryLifeState.NoData, store.localState(null))

        store.recordCollections("C1", 1_000, "pair-1")
        clock.advance(3.days)
        store.recordCollections("C1", 1_066, "pair-1")
        assertEquals(RingBatteryLifeState.Learning(3, 7), store.localState("C1"))

        clock.advance(4.days)
        store.recordCollections("C1", 1_154, "pair-1") // 22/day
        store.recordVoltage("C1", 1590)
        val e = assertIs<RingBatteryLifeState.Estimated>(store.localState(store.connectedSerial("pair-1")))
        assertEquals(22.0, e.collectionsPerDay, 0.01)
        assertTrue(e.daysLeft / 365.0 in 5.0..6.0, "median user ≈5.5 years, got ${e.daysLeft} days")
        assertEquals(false, e.replaceSoon)
    }

    @Test
    fun voltageCliffMeansReplaceSoon() = runTest {
        val clock = FakeClock()
        val store = store(clock)
        store.recordCollections("C1", 2_000, "pair-1")
        clock.advance(8.days)
        store.recordVoltage("C1", 1300)
        store.recordVoltage("C1", null) // a transfer without a reading keeps the warning
        val e = assertIs<RingBatteryLifeState.Estimated>(store.localState("C1"))
        assertTrue(e.replaceSoon)
        assertEquals(0, e.daysLeft)
    }

    @Test
    fun voltageCliffWarnsDuringLearningAndBeforeFirstCount() = runTest {
        val clock = FakeClock()
        val store = store(clock)
        store.recordVoltage("C1", 1300) // voltage can arrive before the first count
        assertIs<RingBatteryLifeState.Estimated>(store.localState("C1")).let { assertTrue(it.replaceSoon) }
        store.recordCollections("C1", 2_000, "pair-1")
        clock.advance(1.days)
        val e = assertIs<RingBatteryLifeState.Estimated>(store.localState("C1"))
        assertTrue(e.replaceSoon)
        store.recordVoltage("C1", 1590)
        assertEquals(RingBatteryLifeState.Learning(0, 7), store.localState("C1")) // one sample: nothing observed yet
    }

    @Test
    fun rateCoversOnlyObservedTimeNotSilenceSinceTheLastTransfer() = runTest {
        val clock = FakeClock()
        val store = store(clock)
        store.recordCollections("C1", 1_000, "pair-1")
        clock.advance(7.days)
        store.recordCollections("C1", 1_700, "pair-1") // 100/day
        clock.advance(30.days) // phone away: no transfers, but the ring may still be in use
        val e = assertIs<RingBatteryLifeState.Estimated>(store.localState("C1"))
        assertEquals(100.0, e.collectionsPerDay, 0.01)
        store.recordCollections("C2", 5, "pair-2")
        clock.advance(10.days) // one sample, then silence: still learning, not a learned zero rate
        assertEquals(RingBatteryLifeState.Learning(0, 7), store.localState("C2"))
    }

    @Test
    fun knownRingSerialNeedsOneUnambiguousMatch() {
        fun ring(mac: String, serial: String?) = FirestoreKnownRing(mac = mac, serial = serial, runningFwVersion = "3.86.0")
        val mine = ring("E9276D8BF071", "C23251100CZ-DE1-AG-10")
        assertEquals("C23251100CZ-DE1-AG-10", knownRingSerial(listOf(ring("E9276D8BF071", null), mine), "EA276D8BF071"))
        assertEquals(null, knownRingSerial(listOf(mine, ring("E8276D8BF072", "OTHER")), "EA276D8BF071")) // ambiguous
        assertEquals(null, knownRingSerial(listOf(ring("E92236B4E6E5", "C2")), "EA276D8BF071"))
        assertEquals(false, sameRingAddress("ZZ276D8BF0ZZ", "EA276D8BF071")) // not hex
    }

    @Test
    fun serverEstimateIsValidatedBeforeUse() {
        val now = Instant.fromEpochSeconds(1_790_000_000)
        val ok = ServerRing("C1", 89.5, 685, 94, "ok", (now - 1.days).toString())
        assertEquals(RingBatteryLifeState.Estimated(685, 94, false, 89.5), ok.toEstimate("C1", now, 3.days))
        assertEquals(null, ok.toEstimate("C2", now, 3.days)) // another ring
        assertEquals(null, ok.copy(computedAt = (now - 4.days).toString()).toEstimate("C1", now, 3.days)) // stale
        assertEquals(null, ok.copy(computedAt = (now + 2.days).toString()).toEstimate("C1", now, 3.days)) // future
        assertEquals(null, ok.copy(remainingPct = 140).toEstimate("C1", now, 3.days))
        assertEquals(null, ok.copy(daysLeft = -1).toEstimate("C1", now, 3.days))
        assertEquals(null, ok.copy(status = "mystery").toEstimate("C1", now, 3.days))
        assertEquals(null, ok.copy(computedAt = "garbage").toEstimate("C1", now, 3.days))
        assertTrue(ok.copy(status = "replace_soon").toEstimate("C1", now, 3.days)!!.replaceSoon)
    }

    @Test
    fun countGoingBackwardsRestartsLearning() = runTest {
        val clock = FakeClock()
        val store = store(clock)
        store.recordCollections("C1", 5_000, "pair-1")
        clock.advance(10.days)
        store.recordCollections("C1", 10, "pair-1")
        assertEquals(RingBatteryLifeState.Learning(0, 7), store.localState("C1"))
    }

    @Test
    fun onlyTheRingFromTheCurrentPairingIsShown() = runTest {
        val store = store(FakeClock())
        store.recordCollections("OLD-RING", 3_000, "pair-1")
        assertEquals("OLD-RING", store.connectedSerial("pair-1"))
        assertEquals(null, store.connectedSerial("pair-2")) // re-paired: hide old ring until new one syncs
        assertEquals(null, store.connectedSerial(null)) // unpaired
        store.recordCollections("NEW-RING", 10, "pair-2")
        assertEquals("NEW-RING", store.connectedSerial("pair-2"))
        store.recordCollections("STRAY-RING", 10, null) // transfer not from the paired ring
        assertEquals("NEW-RING", store.connectedSerial("pair-2"))
    }

    @Test
    fun pairingAddressMatchesTheRingsReportedMac() {
        assertTrue(sameRingAddress("E9276D8BF071", "EA:27:6D:8B:F0:71")) // known_rings mac vs paired id
        assertTrue(sameRingAddress("EA22CE312EAA", "EA22CE312EAB")) // primary vs failsafe
        assertEquals(false, sameRingAddress("E92236B4E6E5", "EA276D8BF071"))
        assertEquals(false, sameRingAddress("", "EA276D8BF071"))
    }

    @Test
    fun pairedRingIsKnownBeforeAnyTransfer() = runTest {
        val store = store(FakeClock())
        store.rememberPairedRing("C1", "pair-1")
        assertEquals("C1", store.connectedSerial("pair-1")) // lets the repository ask the server
        assertEquals(RingBatteryLifeState.NoData, store.localState("C1")) // no local history yet
        val v = store.changes.value
        store.rememberPairedRing("C1", "pair-1")
        assertEquals(v, store.changes.value) // unchanged: no reload churn
    }

    @Test
    fun everySampleSignalsAnOpenScreenToReload() = runTest {
        val store = store(FakeClock())
        val before = store.changes.value
        store.recordCollections("C1", 1, "pair-1")
        store.recordVoltage("C1", 1590)
        assertEquals(before + 2, store.changes.value)
    }

    @Test
    fun formatsCoarsely() {
        assertEquals("5 years", formatRemaining(2_000))
        assertEquals("13 months", formatRemaining(400))
        assertEquals("22 months", formatRemaining(685)) // not "1 year" for 1.9 years
        assertEquals("3 months", formatRemaining(100))
        assertEquals("4 weeks", formatRemaining(30))
        assertEquals("1 day", formatRemaining(1))
        assertEquals("11 sec", formatRecordingTime(22.0)) // fleet median
        assertEquals("3 min", formatRecordingTime(316.0)) // p99
    }
}
