package coredevices.ring.service

import coredevices.libindex.database.BasePreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RingPairingKeyMonitorTest {

    private class FakePrefs : BasePreferences {
        override val ringPaired: StateFlow<String?> = MutableStateFlow(null)
        override val ringPairedName: StateFlow<String?> = MutableStateFlow(null)
        private val _ringPairingKeyHash = MutableStateFlow<Int?>(null)
        override val ringPairingKeyHash: StateFlow<Int?> = _ringPairingKeyHash
        override val lastSyncIndex: StateFlow<Int?> = MutableStateFlow(null)
        override val scanByPairedAddress: StateFlow<Boolean> = MutableStateFlow(true)

        override fun setRingPaired(id: String?) {}
        override fun setRingPairedName(name: String?) {}
        override fun setRingPairingKeyHash(hash: Int?) { _ringPairingKeyHash.value = hash }
        override suspend fun setLastSyncIndex(index: Int?) {}
        override fun setScanByPairedAddress(enabled: Boolean) {}
    }

    private val prefs = FakePrefs()
    private val monitor = RingPairingKeyMonitor(prefs)
    private val fw = "3.86.0"

    @Test
    fun nothingStoredUntilEncryptedLinkConfirmed() {
        assertFalse(monitor.onAdvertisement(fw, 0x1234))
        assertNull(prefs.ringPairingKeyHash.value)
    }

    @Test
    fun confirmedLinkStoresHash() {
        monitor.onEncryptedLinkConfirmed(fw, 0x1234)
        assertEquals(0x1234, prefs.ringPairingKeyHash.value)
        assertFalse(monitor.onAdvertisement(fw, 0x1234))
    }

    @Test
    fun mismatchReportedOncePerHash() {
        monitor.onEncryptedLinkConfirmed(fw, 0x1234)
        assertTrue(monitor.onAdvertisement(fw, 0xABCD))
        assertFalse(monitor.onAdvertisement(fw, 0xABCD))
        assertTrue(monitor.onAdvertisement(fw, 0xEF01))
        // Stored value is left alone so the mismatch persists across restarts.
        assertEquals(0x1234, prefs.ringPairingKeyHash.value)
    }

    @Test
    fun matchAgainRearmsReporting() {
        monitor.onEncryptedLinkConfirmed(fw, 0x1234)
        assertTrue(monitor.onAdvertisement(fw, 0xABCD))
        assertFalse(monitor.onAdvertisement(fw, 0x1234))
        assertTrue(monitor.onAdvertisement(fw, 0xABCD))
    }

    @Test
    fun confirmedLinkWithNewHashReplacesStored() {
        monitor.onEncryptedLinkConfirmed(fw, 0x1234)
        assertTrue(monitor.onAdvertisement(fw, 0xABCD))
        monitor.onEncryptedLinkConfirmed(fw, 0xABCD)
        assertEquals(0xABCD, prefs.ringPairingKeyHash.value)
        assertFalse(monitor.onAdvertisement(fw, 0xABCD))
    }

    @Test
    fun resetClearsExpectationUntilNextConfirmedLink() {
        monitor.onEncryptedLinkConfirmed(fw, 0x1234)
        prefs.setRingPairingKeyHash(null)
        assertFalse(monitor.onAdvertisement(fw, 0xABCD))
    }

    @Test
    fun olderFirmwareIsIgnored() {
        monitor.onEncryptedLinkConfirmed("3.85.0", 0x1234)
        assertNull(prefs.ringPairingKeyHash.value)
        prefs.setRingPairingKeyHash(0x1234)
        assertFalse(monitor.onAdvertisement("3.85.0", 0xABCD))
        assertFalse(monitor.onAdvertisement("0.0.0", 0xABCD))
    }

    @Test
    fun missingHashIsIgnored() {
        monitor.onEncryptedLinkConfirmed(fw, null)
        assertNull(prefs.ringPairingKeyHash.value)
        prefs.setRingPairingKeyHash(0x1234)
        assertFalse(monitor.onAdvertisement(fw, null))
    }

    @Test
    fun firmwareGate() {
        assertTrue(RingPairingKeyMonitor.advertisesPairingKeyHash("3.86.0"))
        assertTrue(RingPairingKeyMonitor.advertisesPairingKeyHash("3.90.0"))
        assertTrue(RingPairingKeyMonitor.advertisesPairingKeyHash("4.0.0"))
        assertFalse(RingPairingKeyMonitor.advertisesPairingKeyHash("3.85.0"))
        assertFalse(RingPairingKeyMonitor.advertisesPairingKeyHash("2.99.0"))
        assertFalse(RingPairingKeyMonitor.advertisesPairingKeyHash("0"))
        assertFalse(RingPairingKeyMonitor.advertisesPairingKeyHash(""))
    }
}
